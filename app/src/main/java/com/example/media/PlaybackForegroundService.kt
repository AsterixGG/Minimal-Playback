package com.example.media

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.R
import com.example.model.LocalAudioTrack
import com.example.model.PlaybackSnapshot
import com.example.model.PlaybackSource
import com.example.model.QueueTrack
import com.example.model.RepeatModeState
import com.example.model.SleepTimerMode
import com.example.model.SleepTimerState
import com.example.model.TrackMetadata
import com.example.util.Id3LyricsExtractor
import com.example.util.formatCountdownMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaybackForegroundService : Service(), LocalTransportHandler {

    companion object {
        private const val TAG = "PlaybackService"
        private const val ACTION_SYNC_EXTERNAL = "com.minimalplayback.SYNC_EXTERNAL_NOTIF"

        @Volatile
        private var pendingPlaylist: List<LocalAudioTrack> = emptyList()

        @Volatile
        private var pendingStartIndex: Int = 0

        @Volatile
        private var instance: PlaybackForegroundService? = null

        fun playLocalTracks(context: Context, tracks: List<LocalAudioTrack>, startIndex: Int) {
            if (tracks.isEmpty()) return
            pendingPlaylist = tracks
            pendingStartIndex = startIndex.coerceIn(0, tracks.lastIndex)
            val svc = instance
            if (svc != null) {
                svc.loadAndPlayPendingPlaylist()
            } else {
                val intent = Intent(context, PlaybackForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun syncExternalNotification(context: Context) {
            val snap = PlaybackStateBus.currentSnapshot()
            if (!snap.hasActiveSession) return
            val intent = Intent(context, PlaybackForegroundService::class.java).apply {
                action = ACTION_SYNC_EXTERNAL
            }
            runCatching {
                if (snap.isPlaying || PlaybackStateBus.sleepTimerState.value.isActive) {
                    ContextCompat.startForegroundService(context, intent)
                } else if (instance != null) {
                    instance?.updateSessionAndNotification(snap)
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var mediaSessionCompat: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager

    private var currentTracks: List<LocalAudioTrack> = emptyList()
    private val artworkCache = mutableMapOf<Long, Bitmap?>()

    private var lastMetadataKey: String = ""
    private var lastMetadataHadArt: Boolean = false
    private var cachedNotificationRoundedArt: Bitmap? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationTicker = object : Runnable {
        override fun run() {
            val snap = PlaybackStateBus.currentSnapshot()
            val sleep = PlaybackStateBus.sleepTimerState.value
            if (snap.hasActiveSession && (snap.isPlaying || sleep.isActive)) {
                updatePlaybackStateOnly(snap, sleep)
                if (sleep.isActive) {
                    updateSessionAndNotification(snap, sleep)
                }
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (!PlaybackStateBus.currentSnapshot().isPlaying) {
                PlaybackStateBus.playPause()
            }
        }

        override fun onPause() {
            PlaybackStateBus.pause()
        }

        override fun onSkipToNext() {
            PlaybackStateBus.next()
        }

        override fun onSkipToPrevious() {
            PlaybackStateBus.previous()
        }

        override fun onSeekTo(pos: Long) {
            PlaybackStateBus.seekTo(pos)
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            when (action) {
                PlaybackNotificationHelper.ACTION_START_RADIO -> {
                    PlaybackStateBus.requestSongRadio(this@PlaybackForegroundService)
                }
                PlaybackNotificationHelper.ACTION_SLEEP_TIMER -> {
                    PlaybackStateBus.cycleSleepTimerPreset()
                }
                PlaybackNotificationHelper.ACTION_CANCEL_SLEEP_TIMER -> {
                    PlaybackStateBus.cancelSleepTimer()
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            pushLocalSnapshot()
            val snap = PlaybackStateBus.currentSnapshot()
            updateSessionAndNotification(snap)
            scheduleOrStopTicker(snap.isPlaying, PlaybackStateBus.sleepTimerState.value.isActive)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            pushLocalSnapshot()
            updateSessionAndNotification(PlaybackStateBus.currentSnapshot())
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            pushLocalSnapshot()
            updateSessionAndNotification(PlaybackStateBus.currentSnapshot())
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            pushLocalSnapshot()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            pushLocalSnapshot()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            pushLocalSnapshot()
            updateSessionAndNotification(PlaybackStateBus.currentSnapshot())
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            pushLocalSnapshot()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        PlaybackStateBus.init(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        PlaybackNotificationHelper.ensureChannel(this)

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(playerListener)
            }

        mediaSessionCompat = MediaSessionCompat(this, "MinimalPlaybackMediaSession").apply {
            setCallback(sessionCallback)
            isActive = true
        }

        PlaybackStateBus.localActionHandler = this
        PlaybackStateBus.localVolumeScaleHandler = { scale ->
            mainHandler.post {
                player.volume = scale.coerceIn(0f, 1f)
            }
        }

        serviceScope.launch {
            PlaybackStateBus.activeSnapshot.collectLatest { snap ->
                val sleep = PlaybackStateBus.sleepTimerState.value
                if (snap.hasActiveSession) {
                    updateSessionAndNotification(snap, sleep)
                    scheduleOrStopTicker(snap.isPlaying, sleep.isActive)
                } else {
                    scheduleOrStopTicker(false, false)
                }
            }
        }

        serviceScope.launch {
            PlaybackStateBus.sleepTimerState.collectLatest { sleepState ->
                val snap = PlaybackStateBus.currentSnapshot()
                if (snap.hasActiveSession) {
                    updateSessionAndNotification(snap, sleepState)
                    scheduleOrStopTicker(snap.isPlaying, sleepState.isActive)
                }
            }
        }

        if (pendingPlaylist.isNotEmpty()) {
            loadAndPlayPendingPlaylist()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SYNC_EXTERNAL) {
            val snap = PlaybackStateBus.currentSnapshot()
            val sleep = PlaybackStateBus.sleepTimerState.value
            updateMediaMetadataIfNeeded(snap)
            updatePlaybackStateOnly(snap, sleep)
            val notif = PlaybackNotificationHelper.buildNotification(
                context = this,
                snapshot = snap,
                mediaSessionCompat = mediaSessionCompat,
                sleepTimer = sleep,
                cachedRoundedArt = cachedNotificationRoundedArt
            )
            runCatching {
                startForeground(PlaybackNotificationHelper.NOTIFICATION_ID, notif)
            }
            scheduleOrStopTicker(snap.isPlaying, sleep.isActive)
        } else if (pendingPlaylist.isNotEmpty() && currentTracks != pendingPlaylist) {
            loadAndPlayPendingPlaylist()
        }
        return START_STICKY
    }

    private fun updateSessionAndNotification(
        snap: PlaybackSnapshot,
        sleepTimer: SleepTimerState = PlaybackStateBus.sleepTimerState.value
    ) {
        if (!snap.hasActiveSession) return
        mediaSessionCompat.isActive = true
        updateMediaMetadataIfNeeded(snap)
        updatePlaybackStateOnly(snap, sleepTimer)

        val notification = PlaybackNotificationHelper.buildNotification(
            context = this,
            snapshot = snap,
            mediaSessionCompat = mediaSessionCompat,
            sleepTimer = sleepTimer,
            cachedRoundedArt = cachedNotificationRoundedArt
        )
        if (snap.isPlaying || sleepTimer.isActive) {
            runCatching {
                startForeground(PlaybackNotificationHelper.NOTIFICATION_ID, notification)
            }.onFailure {
                Log.w(TAG, "startForeground fallback to notify: ${it.message}")
                notificationManager.notify(PlaybackNotificationHelper.NOTIFICATION_ID, notification)
            }
        } else {
            notificationManager.notify(PlaybackNotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private fun updateMediaMetadataIfNeeded(snap: PlaybackSnapshot) {
        val track = snap.track
        val key = "${track.id}|${track.title}|${track.artist}|${snap.durationMs}"
        val hasArt = track.artworkBitmap != null && !track.artworkBitmap.isRecycled

        if (key == lastMetadataKey && (!hasArt || lastMetadataHadArt)) {
            return
        }

        lastMetadataKey = key
        lastMetadataHadArt = hasArt
        cachedNotificationRoundedArt = if (hasArt) {
            PlaybackNotificationHelper.roundCorners(track.artworkBitmap!!, 28f)
        } else {
            null
        }

        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title.ifBlank { getString(R.string.app_name) })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist.ifBlank { getString(R.string.app_name) })
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, snap.durationMs.coerceAtLeast(0L))

        if (hasArt) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, track.artworkBitmap)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, track.artworkBitmap)
        }

        mediaSessionCompat.setMetadata(builder.build())
    }

    private fun updatePlaybackStateOnly(snap: PlaybackSnapshot, sleepTimer: SleepTimerState) {
        val state = if (snap.isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP

        val sleepActionLabel = when {
            !sleepTimer.isActive -> "Sleep Timer"
            sleepTimer.isFadingOut -> "Fading…"
            sleepTimer.mode == SleepTimerMode.END_OF_TRACK -> "Sleep: End"
            else -> "Sleep: ${formatCountdownMs(sleepTimer.remainingMs)}"
        }

        val radioCustomAction = PlaybackStateCompat.CustomAction.Builder(
            PlaybackNotificationHelper.ACTION_START_RADIO,
            "Song Radio",
            R.drawable.ic_vec_radio
        ).build()

        val sleepCustomAction = PlaybackStateCompat.CustomAction.Builder(
            PlaybackNotificationHelper.ACTION_SLEEP_TIMER,
            sleepActionLabel,
            R.drawable.ic_vec_timer
        ).build()

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                state,
                PlaybackStateBus.interpolatedPositionMs(snap),
                if (snap.isPlaying) snap.playbackSpeed else 0f,
                SystemClock.elapsedRealtime()
            )
            .addCustomAction(radioCustomAction)
            .addCustomAction(sleepCustomAction)
            .build()

        mediaSessionCompat.setPlaybackState(playbackState)
    }

    private fun scheduleOrStopTicker(isPlaying: Boolean, isSleepActive: Boolean) {
        mainHandler.removeCallbacks(notificationTicker)
        if (isPlaying || isSleepActive) {
            mainHandler.postDelayed(notificationTicker, 1000L)
        }
    }

    private fun loadAndPlayPendingPlaylist() {
        val tracks = pendingPlaylist
        if (tracks.isEmpty()) return
        val startIndex = pendingStartIndex.coerceIn(0, tracks.lastIndex)
        currentTracks = tracks

        val items = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .build()
                )
                .build()
        }

        player.volume = 1f
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.play()
        PlaybackStateBus.setSelectedSource(PlaybackSource.LOCAL)
        pushLocalSnapshot()
        updateSessionAndNotification(PlaybackStateBus.currentSnapshot())
    }

    private fun pushLocalSnapshot() {
        val idx = player.currentMediaItemIndex
        val activeTrack = currentTracks.getOrNull(idx)
        if (activeTrack == null) {
            PlaybackStateBus.updateLocalSnapshot(
                PlaybackSnapshot(source = PlaybackSource.LOCAL, hasActiveSession = false)
            )
            return
        }

        val cachedArt = artworkCache[activeTrack.id]
        if (!artworkCache.containsKey(activeTrack.id)) {
            artworkCache[activeTrack.id] = null
            serviceScope.launch(Dispatchers.IO) {
                val extracted = Id3LyricsExtractor.extract(this@PlaybackForegroundService, activeTrack.contentUri)
                val art = extracted.artwork
                artworkCache[activeTrack.id] = art
                mainHandler.post {
                    pushLocalSnapshot()
                    updateSessionAndNotification(PlaybackStateBus.currentSnapshot())
                }
            }
        }

        val repeatState = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatModeState.ALL
            Player.REPEAT_MODE_ONE -> RepeatModeState.ONE
            else -> RepeatModeState.OFF
        }

        val queueItems = currentTracks.mapIndexed { qIdx, track ->
            QueueTrack(
                queueId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                isCurrent = qIdx == idx,
                isNext = qIdx == idx + 1,
                localIndex = qIdx
            )
        }

        val duration = player.duration.takeIf { it > 0L } ?: activeTrack.durationMs
        val pos = player.currentPosition.coerceAtLeast(0L)

        PlaybackStateBus.updateLocalSnapshot(
            PlaybackSnapshot(
                source = PlaybackSource.LOCAL,
                hasActiveSession = true,
                isPlaying = player.isPlaying,
                positionMs = pos,
                durationMs = duration,
                playbackSpeed = player.playbackParameters.speed,
                lastPositionUpdateElapsedMs = SystemClock.elapsedRealtime(),
                track = TrackMetadata(
                    id = "local:${activeTrack.id}",
                    title = activeTrack.title,
                    artist = activeTrack.artist,
                    album = activeTrack.album,
                    durationMs = duration,
                    artworkBitmap = cachedArt,
                    sourceAppLabel = "Local Audio",
                    sourcePackageName = packageName,
                    localUri = activeTrack.contentUri
                ),
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = repeatState,
                queue = queueItems,
                activeAudioSessionId = player.audioSessionId.coerceAtLeast(0)
            )
        )
    }

    override fun togglePlayPause() {
        mainHandler.post {
            if (player.isPlaying) player.pause() else {
                player.volume = 1f
                player.play()
            }
        }
    }

    override fun pause() {
        mainHandler.post {
            player.pause()
        }
    }

    override fun skipToNext() {
        mainHandler.post {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else if (currentTracks.isNotEmpty()) {
                player.seekTo(0, 0L)
            }
            player.volume = 1f
            player.play()
        }
    }

    override fun skipToPrevious() {
        mainHandler.post {
            if (player.currentPosition > 3500L) {
                player.seekTo(0L)
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            } else {
                player.seekTo(0L)
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        mainHandler.post {
            player.seekTo(positionMs.coerceAtLeast(0L))
            pushLocalSnapshot()
            updateSessionAndNotification(PlaybackStateBus.currentSnapshot())
        }
    }

    override fun toggleShuffle() {
        mainHandler.post {
            player.shuffleModeEnabled = !player.shuffleModeEnabled
        }
    }

    override fun cycleRepeat() {
        mainHandler.post {
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    override fun skipToIndex(index: Int) {
        mainHandler.post {
            if (index in currentTracks.indices) {
                player.volume = 1f
                player.seekToDefaultPosition(index)
                player.play()
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(notificationTicker)
        if (PlaybackStateBus.localActionHandler === this) {
            PlaybackStateBus.localActionHandler = null
        }
        PlaybackStateBus.localVolumeScaleHandler = null
        instance = null
        mediaSessionCompat.isActive = false
        mediaSessionCompat.release()
        player.removeListener(playerListener)
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }
}
