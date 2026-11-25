package com.example.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.SessionToken
import com.example.model.PlaybackSnapshot
import com.example.model.PlaybackSource
import com.example.model.QueueTrack
import com.example.model.TrackMetadata
import com.google.common.util.concurrent.ListenableFuture

class MediaNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaNotifListener"
        private const val LEGACY_SET_SHUFFLE_ACTION =
            "android.support.v4.media.session.action.SET_SHUFFLE_MODE"
        private const val LEGACY_SHUFFLE_ARG =
            "android.support.v4.media.session.action.ARGUMENT_SHUFFLE_MODE"

        fun isNotificationAccessEnabled(context: Context): Boolean {
            return NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.packageName)
        }
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var media3TokenFuture: ListenableFuture<SessionToken>? = null
    private var media3ControllerFuture: ListenableFuture<androidx.media3.session.MediaController>? = null
    private var media3Controller: androidx.media3.session.MediaController? = null
    private var optimisticExternalShuffle: Boolean = false
    private var lastArtworkTrackKey: String = ""
    private var lastArtworkBitmap: Bitmap? = null
    private lateinit var componentName: ComponentName

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            selectBestExternalController(controllers)
        }

    private val media3PlayerListener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            optimisticExternalShuffle = shuffleModeEnabled
            pushExternalSnapshot()
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            pushExternalSnapshot()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            pushExternalSnapshot()
        }

        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
            pushExternalSnapshot()
        }

        override fun onSessionDestroyed() {
            refreshActiveSessions()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        PlaybackStateBus.init(this)
        PlaybackStateBus.setNotificationAccessGranted(true)
        PlaybackStateBus.externalShuffleHandler = { toggleExternalShuffle() }
        componentName = ComponentName(this, MediaNotificationListenerService::class.java)
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        runCatching {
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                componentName
            )
        }.onFailure {
            Log.e(TAG, "Failed to register active sessions listener", it)
        }
        refreshActiveSessions()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        PlaybackStateBus.setNotificationAccessGranted(false)
        PlaybackStateBus.externalShuffleHandler = null
        detachCurrentController()
        runCatching {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName != packageName) {
            refreshActiveSessions()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName != packageName) {
            refreshActiveSessions()
        }
    }

    private fun refreshActiveSessions() {
        val manager = mediaSessionManager ?: return
        runCatching {
            val controllers = manager.getActiveSessions(componentName)
            selectBestExternalController(controllers)
        }.onFailure {
            Log.w(TAG, "Unable to query active sessions: ${it.message}")
        }
    }

    private fun selectBestExternalController(controllers: List<MediaController>?) {
        val externalControllers = controllers.orEmpty()
            .filter { it.packageName != packageName }

        val preferred = externalControllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: externalControllers.firstOrNull()

        if (preferred?.sessionToken != activeController?.sessionToken) {
            detachCurrentController()
            activeController = preferred
            optimisticExternalShuffle = false
            PlaybackStateBus.activeExternalController = preferred
            preferred?.registerCallback(controllerCallback)
            preferred?.let { connectMedia3Bridge(it.sessionToken) }
        }
        pushExternalSnapshot()
    }

    private fun connectMedia3Bridge(platformToken: MediaSession.Token) {
        releaseMedia3Bridge()
        val executor = ContextCompat.getMainExecutor(this)
        runCatching {
            val tokenFuture = SessionToken.createSessionToken(this, platformToken)
            media3TokenFuture = tokenFuture
            tokenFuture.addListener({
                runCatching {
                    val sessionToken = tokenFuture.get()
                    val ctrlFuture = androidx.media3.session.MediaController.Builder(this, sessionToken)
                        .buildAsync()
                    media3ControllerFuture = ctrlFuture
                    ctrlFuture.addListener({
                        runCatching {
                            val ctrl = ctrlFuture.get()
                            media3Controller = ctrl
                            ctrl.addListener(media3PlayerListener)
                            if (ctrl.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM) ||
                                ctrl.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)
                            ) {
                                optimisticExternalShuffle = ctrl.shuffleModeEnabled
                                pushExternalSnapshot()
                            }
                        }
                    }, executor)
                }
            }, executor)
        }
    }

    private fun releaseMedia3Bridge() {
        runCatching {
            media3Controller?.removeListener(media3PlayerListener)
            media3Controller?.release()
        }
        media3Controller = null
        media3ControllerFuture?.let {
            runCatching { androidx.media3.session.MediaController.releaseFuture(it) }
        }
        media3ControllerFuture = null
        media3TokenFuture = null
    }

    private fun detachCurrentController() {
        releaseMedia3Bridge()
        activeController?.let {
            runCatching { it.unregisterCallback(controllerCallback) }
        }
        activeController = null
        PlaybackStateBus.activeExternalController = null
    }

    private fun toggleExternalShuffle() {
        val controller = activeController ?: return
        val m3 = media3Controller
        if (m3 != null && m3.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) {
            val next = !m3.shuffleModeEnabled
            m3.shuffleModeEnabled = next
            optimisticExternalShuffle = next
            pushExternalSnapshot()
            return
        }

        val customShuffle = controller.playbackState?.customActions?.firstOrNull { action ->
            action.action.contains("shuffle", ignoreCase = true) ||
                action.name.toString().contains("shuffle", ignoreCase = true)
        }
        if (customShuffle != null) {
            runCatching {
                controller.transportControls.sendCustomAction(customShuffle, null)
                optimisticExternalShuffle = !optimisticExternalShuffle
                pushExternalSnapshot()
            }
            return
        }

        val next = !optimisticExternalShuffle
        optimisticExternalShuffle = next
        runCatching {
            val args = Bundle().apply {
                putInt(LEGACY_SHUFFLE_ARG, if (next) 1 else 0)
            }
            controller.transportControls.sendCustomAction(LEGACY_SET_SHUFFLE_ACTION, args)
        }
        pushExternalSnapshot()
    }

    private fun pushExternalSnapshot() {
        val controller = activeController
        if (controller == null) {
            PlaybackStateBus.updateExternalSnapshot(
                PlaybackSnapshot(source = PlaybackSource.EXTERNAL, hasActiveSession = false)
            )
            return
        }

        val metadata = controller.metadata
        val playbackState = controller.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L

        val trackKey = "${title.trim()}|${artist.trim()}"
        val decodedArtwork: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        val artwork: Bitmap? = if (trackKey == lastArtworkTrackKey) {
            lastArtworkBitmap ?: decodedArtwork?.also { lastArtworkBitmap = it }
        } else {
            lastArtworkTrackKey = trackKey
            decodedArtwork.also { lastArtworkBitmap = it }
        }

        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING ||
            playbackState?.state == PlaybackState.STATE_BUFFERING

        val rawPos = playbackState?.position?.coerceAtLeast(0L) ?: 0L
        val speed = playbackState?.playbackSpeed?.takeIf { it > 0f } ?: 1f
        val updateTime = playbackState?.lastPositionUpdateTime?.takeIf { it > 0L }
            ?: SystemClock.elapsedRealtime()

        val activeQueueId = playbackState?.activeQueueItemId ?: MediaSession.QueueItem.UNKNOWN_ID.toLong()
        val rawQueue = controller.queue.orEmpty()
        val currentIdx = rawQueue.indexOfFirst { it.queueId == activeQueueId }
        val mappedQueue = rawQueue.mapIndexed { idx, item ->
            val desc = item.description
            val qTitle = desc.title?.toString().orEmpty().ifBlank { "Track ${idx + 1}" }
            val qArtist = desc.subtitle?.toString().orEmpty().ifBlank { artist }
            val isCurr = idx == currentIdx || item.queueId == activeQueueId
            val isNext = if (currentIdx >= 0) idx == currentIdx + 1 else idx == 0
            QueueTrack(
                queueId = item.queueId,
                title = qTitle,
                artist = qArtist,
                album = desc.description?.toString().orEmpty(),
                isCurrent = isCurr,
                isNext = isNext
            )
        }

        val appLabel = resolveAppLabel(controller.packageName)
        val shuffleEnabled = media3Controller?.takeIf {
            it.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)
        }?.shuffleModeEnabled ?: optimisticExternalShuffle

        PlaybackStateBus.updateExternalSnapshot(
            PlaybackSnapshot(
                source = PlaybackSource.EXTERNAL,
                hasActiveSession = title.isNotBlank() || playbackState != null,
                isPlaying = isPlaying,
                positionMs = rawPos,
                durationMs = durationMs,
                playbackSpeed = speed,
                lastPositionUpdateElapsedMs = updateTime,
                track = TrackMetadata(
                    id = "${controller.packageName}:$title:$artist",
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    artworkBitmap = artwork,
                    sourceAppLabel = appLabel,
                    sourcePackageName = controller.packageName
                ),
                shuffleEnabled = shuffleEnabled,
                queue = mappedQueue
            )
        )
        if (PlaybackStateBus.selectedSource.value == PlaybackSource.EXTERNAL) {
            PlaybackForegroundService.syncExternalNotification(this)
        }
    }

    private fun resolveAppLabel(pkg: String): String {
        return try {
            val pm = packageManager
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }
}
