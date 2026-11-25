package com.example.ui

import android.app.Application
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiLyricsAndQueueService
import com.example.audio.AudioEffectsManager
import com.example.audio.AudioVisualizerManager
import com.example.audio.VisualizerFrame
import com.example.data.GeminiCacheStore
import com.example.data.KaralyrClient
import com.example.data.LrcLibApi
import com.example.data.LrcLibClient
import com.example.data.LrcLibTrackResponse
import com.example.data.LrcParser
import com.example.data.LrcSearchMatcher
import com.example.data.LyricsOvhClient
import com.example.data.PersistedAudioSettings
import com.example.data.PlaybackStatsCodec
import com.example.data.PlaybackStatsRepository
import com.example.data.SettingsRepository
import com.example.data.TrackMetadataCleaner
import com.example.media.LocalLibraryScanResult
import com.example.media.LocalMediaScanner
import com.example.media.MediaNotificationListenerService
import com.example.media.PlaybackForegroundService
import com.example.media.PlaybackStateBus
import com.example.model.AiRecommendation
import com.example.model.AppThemeStyle
import com.example.model.EqPreset
import com.example.model.EqualizerState
import com.example.model.GestureAction
import com.example.model.HapticIntensity
import com.example.model.LocalAudioTrack
import com.example.model.LyricLine
import com.example.model.LyricsOrigin
import com.example.model.LyricsUiState
import com.example.model.PlaybackSnapshot
import com.example.model.PlaybackSource
import com.example.model.PlaybackStatsState
import com.example.model.QueueTrack
import com.example.model.RecommendationsUiState
import com.example.model.SleepTimerState
import com.example.model.TrackArtworkState
import com.example.sensor.PocketMotionDetector
import com.example.util.ColorExtractor
import com.example.util.ExtractedTrackColors
import com.example.util.FontManager
import com.example.util.HapticFeedbackManager
import com.example.util.Id3LyricsExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlaybackViewModel"
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }

    private val appContext = application.applicationContext
    private val settingsRepo = SettingsRepository(appContext)
    private val statsRepo = PlaybackStatsRepository(appContext)
    private val geminiCache = GeminiCacheStore(appContext)
    private val geminiService = GeminiLyricsAndQueueService()
    val audioEffectsManager = AudioEffectsManager()
    private val audioVisualizerManager = AudioVisualizerManager()
    private val hapticManager = HapticFeedbackManager(appContext)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _selectedSource = PlaybackStateBus.selectedSource
    val selectedSource: StateFlow<PlaybackSource> = _selectedSource

    private val _playbackSnapshot = MutableStateFlow(PlaybackSnapshot())
    val playbackSnapshot: StateFlow<PlaybackSnapshot> = _playbackSnapshot.asStateFlow()

    private val _livePositionMs = MutableStateFlow(0L)
    val livePositionMs: StateFlow<Long> = _livePositionMs.asStateFlow()

    private val _trackColors = MutableStateFlow(ExtractedTrackColors())
    val trackColors: StateFlow<ExtractedTrackColors> = _trackColors.asStateFlow()

    private val _trackArtwork = MutableStateFlow(TrackArtworkState())
    val trackArtwork: StateFlow<TrackArtworkState> = _trackArtwork.asStateFlow()

    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()

    private val _recommendationsState = MutableStateFlow<RecommendationsUiState>(RecommendationsUiState.Idle)
    val recommendationsState: StateFlow<RecommendationsUiState> = _recommendationsState.asStateFlow()

    private val _notificationAccessGranted = PlaybackStateBus.notificationAccessGranted
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted

    private val _localLibrary = MutableStateFlow(
        LocalLibraryScanResult(emptyList(), emptyList(), emptyList())
    )
    val localLibrary: StateFlow<LocalLibraryScanResult> = _localLibrary.asStateFlow()

    private val _isScanningLibrary = MutableStateFlow(false)
    val isScanningLibrary: StateFlow<Boolean> = _isScanningLibrary.asStateFlow()

    private val _isDimLockActive = MutableStateFlow(false)
    val isDimLockActive: StateFlow<Boolean> = _isDimLockActive.asStateFlow()

    private val _gestureFeedback = MutableStateFlow<String?>(null)
    val gestureFeedback: StateFlow<String?> = _gestureFeedback.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    private val _openUpNextTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val openUpNextTrigger: SharedFlow<Unit> = _openUpNextTrigger.asSharedFlow()

    private val _settings = MutableStateFlow(PersistedAudioSettings())
    val settings: StateFlow<PersistedAudioSettings> = _settings.asStateFlow()

    private val _customFontFamily = MutableStateFlow<FontFamily?>(null)
    val customFontFamily: StateFlow<FontFamily?> = _customFontFamily.asStateFlow()

    private val _fontUploadStatus = MutableStateFlow<String?>(null)
    val fontUploadStatus: StateFlow<String?> = _fontUploadStatus.asStateFlow()

    private val _mediaVolume = MutableStateFlow(
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) to
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    )
    val mediaVolume: StateFlow<Pair<Int, Int>> = _mediaVolume.asStateFlow()

    private data class PendingStatsDelta(
        val listenMs: Long = 0L,
        val dimLockMs: Long = 0L,
        val currentDimLockSessionMs: Long = 0L,
        val externalListenMs: Long = 0L,
        val localListenMs: Long = 0L,
        val artistName: String? = null,
        val source: PlaybackSource = PlaybackSource.EXTERNAL
    )

    private val _pendingStatsDelta = MutableStateFlow(PendingStatsDelta())
    private val statsFlushMutex = Mutex()

    val playbackStats: StateFlow<PlaybackStatsState> = combine(
        statsRepo.statsFlow,
        _pendingStatsDelta
    ) { persisted, pending ->
        persisted.copy(
            totalListeningMs = persisted.totalListeningMs + pending.listenMs,
            totalDimLockMs = persisted.totalDimLockMs + pending.dimLockMs,
            longestDimLockSessionMs = maxOf(
                persisted.longestDimLockSessionMs,
                pending.currentDimLockSessionMs
            ),
            externalListenMs = persisted.externalListenMs + pending.externalListenMs,
            localListenMs = persisted.localListenMs + pending.localListenMs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PlaybackStatsState()
    )

    val equalizerState: StateFlow<EqualizerState> = audioEffectsManager.state
    val visualizerFrame: StateFlow<VisualizerFrame> = audioVisualizerManager.frame
    val sleepTimerState: StateFlow<SleepTimerState> = PlaybackStateBus.sleepTimerState

    private var lastLoadedTrackKey: String? = null
    private var lastCountedPlayedTrackKey: String? = null
    private var lastCleanedTrackPair: Pair<String, String>? = null
    private var lastRecommendedTrackKey: String? = null
    private var lastArtworkTrackKey: String = ""
    private var lastLoadedFontPath: String? = null
    private var hasRestoredInitialSource = false
    private var isLifecycleResumed = true
    private var isUpNextTabOpen = false
    private var lyricsJob: Job? = null
    private var recommendationsJob: Job? = null
    private var feedbackJob: Job? = null
    private var toastJob: Job? = null
    private val _userInteractionTicks = MutableStateFlow(0L)
    private val _isForegroundActive = MutableStateFlow(true)
    private var lastSavedNonZeroVolume: Int = 5

    private val lyricsSessionCache = mutableMapOf<String, LyricsUiState.Ready>()

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_CHANGED_ACTION) {
                refreshMediaVolume()
            }
        }
    }

    private val motionDetector = PocketMotionDetector(
        context = appContext,
        onFlickTriggered = {
            val s = _settings.value
            if (_isDimLockActive.value && s.wristFlickEnabled) {
                executeGestureAction(s.wristFlickAction, "Wrist Flick")
            }
        },
        onFaceDownTriggered = {
            val s = _settings.value
            if (_isDimLockActive.value && s.faceDownEnabled) {
                executeGestureAction(s.faceDownAction, "Face-Down Flip")
            }
        }
    )

    init {
        PlaybackStateBus.init(appContext)
        refreshNotificationAccess()
        refreshMediaVolume()
        audioEffectsManager.attachToSession(0)

        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                volumeReceiver,
                IntentFilter(VOLUME_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        viewModelScope.launch {
            PlaybackStateBus.radioStatusEvents.collect { msg ->
                showStatusToast(msg)
            }
        }

        viewModelScope.launch {
            PlaybackStateBus.externalRadioFallbackEvents.collect { track ->
                val cleaned = TrackMetadataCleaner.clean(track.title, track.artist)
                loadRecommendations(cleaned.title, cleaned.artist, track.album, forceRefresh = false)
                _openUpNextTrigger.tryEmit(Unit)
            }
        }

        viewModelScope.launch {
            settingsRepo.settingsFlow.collectLatest { persisted ->
                _settings.value = persisted
                if (!hasRestoredInitialSource) {
                    hasRestoredInitialSource = true
                    PlaybackStateBus.setSelectedSource(persisted.preferredSource)
                }
                audioEffectsManager.setEnabled(persisted.eqEnabled)
                audioEffectsManager.applyPreset(persisted.eqPreset, persisted.customBandLevels)
                audioEffectsManager.setBassBoostStrength(persisted.bassBoostStrength)
                motionDetector.configure(
                    wristFlickEnabled = persisted.wristFlickEnabled,
                    wristFlickSensitivity = persisted.wristFlickSensitivity,
                    faceDownEnabled = persisted.faceDownEnabled,
                    faceDownSensitivity = persisted.faceDownSensitivity
                )
                hapticManager.enabled = persisted.hapticsEnabled
                hapticManager.intensity = persisted.hapticIntensity
                hapticManager.onTransportEnabled = persisted.hapticOnTransport
                hapticManager.onSlidersEnabled = persisted.hapticOnSliders
                hapticManager.onGesturesEnabled = persisted.hapticOnGestures
                if (persisted.customFontPath != lastLoadedFontPath) {
                    lastLoadedFontPath = persisted.customFontPath
                    val loaded = withContext(Dispatchers.IO) {
                        FontManager.loadFontFamily(persisted.customFontPath)
                    }
                    _customFontFamily.value = loaded
                }
            }
        }

        viewModelScope.launch {
            PlaybackStateBus.activeSnapshot.collectLatest { snap ->
                _playbackSnapshot.value = snap
                _livePositionMs.value = PlaybackStateBus.interpolatedPositionMs(snap)

                // Reattach equalizer and visualizer to active ExoPlayer session when local, or global 0 when external
                val targetAudioSession = if (snap.source == PlaybackSource.LOCAL && snap.activeAudioSessionId > 0) {
                    snap.activeAudioSessionId
                } else {
                    0
                }
                audioEffectsManager.attachToSession(targetAudioSession)
                audioVisualizerManager.updatePlaybackContext(
                    sessionId = targetAudioSession,
                    isPlaying = snap.isPlaying && snap.hasActiveSession,
                    isLifecycleResumed = isLifecycleResumed
                )

                // Decouple album art & color extraction from PlaybackState (play/pause/seek/lyric) updates.
                // Key strictly by stable mediaId (falling back to title+artist) and convert ImageBitmap once.
                val artTrackKey = snap.track.id.ifBlank {
                    "${snap.track.title.trim()}|${snap.track.artist.trim()}"
                }
                val incomingArt = snap.track.artworkBitmap?.takeUnless { it.isRecycled }
                if (snap.hasActiveSession && snap.track.title.isNotBlank()) {
                    val trackChanged = artTrackKey != lastArtworkTrackKey
                    val asyncArtArrived = !trackChanged && _trackArtwork.value.bitmap == null && incomingArt != null
                    if (trackChanged || asyncArtArrived) {
                        lastArtworkTrackKey = artTrackKey
                        val imgBitmap = withContext(Dispatchers.Default) {
                            incomingArt?.let { runCatching { it.asImageBitmap() }.getOrNull() }
                        }
                        _trackArtwork.value = TrackArtworkState(
                            trackKey = artTrackKey,
                            bitmap = incomingArt,
                            imageBitmap = imgBitmap
                        )
                        val colors = withContext(Dispatchers.Default) {
                            ColorExtractor.extract(incomingArt)
                        }
                        _trackColors.value = colors
                    }
                } else if (lastArtworkTrackKey.isNotEmpty()) {
                    lastArtworkTrackKey = ""
                    _trackArtwork.value = TrackArtworkState()
                    _trackColors.value = ExtractedTrackColors()
                }

                // Sync native MediaStyle notification when external source is playing
                if (snap.source == PlaybackSource.EXTERNAL && snap.hasActiveSession) {
                    PlaybackForegroundService.syncExternalNotification(appContext)
                }

                // Load lyrics when track changes; only trigger Gemini recommendations lazily if Up Next is open
                val trackKey = "${snap.track.title.trim()}|${snap.track.artist.trim()}|${snap.track.localUri}"
                if (snap.hasActiveSession && snap.track.title.isNotBlank()) {
                    if (trackKey != lastLoadedTrackKey) {
                        flushPendingStatsAsync()
                        lastLoadedTrackKey = trackKey
                        loadLyricsForTrack(snap)
                    }

                    if (snap.isPlaying && trackKey != lastCountedPlayedTrackKey) {
                        lastCountedPlayedTrackKey = trackKey
                        val cleaned = TrackMetadataCleaner.clean(snap.track.title, snap.track.artist)
                        val artistForStats = cleaned.artist.takeIf {
                            PlaybackStatsCodec.normalizeArtistKey(it) != null
                        }
                        viewModelScope.launch {
                            statsRepo.recordTrackPlayed(artistForStats)
                        }
                    }

                    val recKey = "${snap.track.title}|${snap.track.artist}"
                    if (snap.source == PlaybackSource.EXTERNAL && snap.queue.isEmpty()) {
                        if (recKey != lastRecommendedTrackKey) {
                            lastRecommendedTrackKey = recKey
                            val cleaned = TrackMetadataCleaner.clean(snap.track.title, snap.track.artist)
                            val cachedRecs = geminiCache.getCachedRecommendations(cleaned.title, cleaned.artist)
                            if (cachedRecs != null) {
                                _recommendationsState.value = RecommendationsUiState.Success(cachedRecs)
                            } else if (isUpNextTabOpen) {
                                loadRecommendations(cleaned.title, cleaned.artist, snap.track.album)
                            } else {
                                _recommendationsState.value = RecommendationsUiState.Idle
                            }
                        }
                    } else if (snap.queue.isNotEmpty()) {
                        _recommendationsState.value = RecommendationsUiState.Idle
                    }
                } else {
                    flushPendingStatsAsync()
                    lastLoadedTrackKey = null
                    lastCountedPlayedTrackKey = null
                    lastCleanedTrackPair = null
                    lastRecommendedTrackKey = null
                    _lyricsState.value = LyricsUiState.Idle
                    _recommendationsState.value = RecommendationsUiState.Idle
                }
            }
        }

        // Smooth 200ms position ticker for seek bar timestamps
        viewModelScope.launch {
            while (true) {
                val snap = _playbackSnapshot.value
                if (snap.hasActiveSession && snap.isPlaying) {
                    _livePositionMs.value = PlaybackStateBus.interpolatedPositionMs(snap)
                }
                delay(200L)
            }
        }

        // Real-time Playback Habits & Dim Lock Duration Accumulator (persisted to DataStore)
        viewModelScope.launch {
            var lastTickElapsed = SystemClock.elapsedRealtime()
            while (true) {
                delay(1_000L)
                val now = SystemClock.elapsedRealtime()
                val rawDelta = (now - lastTickElapsed).coerceIn(0L, 5_000L)
                lastTickElapsed = now

                val snap = _playbackSnapshot.value
                val isActivelyPlaying = snap.hasActiveSession && snap.isPlaying && snap.track.title.isNotBlank()
                val isDimLocked = _isDimLockActive.value && _isForegroundActive.value

                if (isActivelyPlaying || isDimLocked) {
                    val listenIncrement = if (isActivelyPlaying) rawDelta else 0L
                    val dimIncrement = if (isDimLocked) rawDelta else 0L
                    val cleanArtist = if (isActivelyPlaying) {
                        TrackMetadataCleaner.clean(snap.track.title, snap.track.artist).artist
                    } else {
                        null
                    }
                    val current = _pendingStatsDelta.value
                    val artistChanged = current.listenMs > 0L &&
                        current.artistName != null &&
                        cleanArtist != null &&
                        !current.artistName.equals(cleanArtist, ignoreCase = true)
                    if (artistChanged) {
                        flushPendingStats()
                    }
                    val base = _pendingStatsDelta.value
                    val updated = PendingStatsDelta(
                        listenMs = base.listenMs + listenIncrement,
                        dimLockMs = base.dimLockMs + dimIncrement,
                        currentDimLockSessionMs = if (isDimLocked) {
                            base.currentDimLockSessionMs + dimIncrement
                        } else {
                            0L
                        },
                        externalListenMs = base.externalListenMs +
                            if (snap.source == PlaybackSource.EXTERNAL) listenIncrement else 0L,
                        localListenMs = base.localListenMs +
                            if (snap.source == PlaybackSource.LOCAL) listenIncrement else 0L,
                        artistName = cleanArtist ?: base.artistName,
                        source = snap.source
                    )
                    _pendingStatsDelta.value = updated

                    if (updated.listenMs + updated.dimLockMs >= 5_000L) {
                        flushPendingStats()
                    }
                } else if (_pendingStatsDelta.value.listenMs > 0L || _pendingStatsDelta.value.dimLockMs > 0L) {
                    flushPendingStats()
                }
            }
        }

        // Foreground Inactivity Timeout -> auto-activates Pocket Dim Lock Mode
        viewModelScope.launch {
            combine(
                _settings,
                _isDimLockActive,
                _isForegroundActive,
                _userInteractionTicks
            ) { s, dimLocked, foreground, tick ->
                QuadTimeoutInput(s.dimLockTimeoutSeconds, dimLocked, foreground, tick)
            }
                .distinctUntilChanged()
                .collectLatest { (timeoutSeconds, dimLocked, foreground, _) ->
                    if (foreground && !dimLocked && timeoutSeconds > 0) {
                        delay(timeoutSeconds * 1000L)
                        setDimLockActive(true)
                    }
                }
        }
    }

    private data class QuadTimeoutInput(
        val timeoutSeconds: Int,
        val dimLocked: Boolean,
        val foreground: Boolean,
        val interactionTick: Long
    )

    fun notifyUserInteraction() {
        _userInteractionTicks.value = _userInteractionTicks.value + 1L
    }

    fun setAppForegroundActive(active: Boolean) {
        _isForegroundActive.value = active
        if (active) {
            notifyUserInteraction()
        }
    }

    fun refreshNotificationAccess() {
        PlaybackStateBus.setNotificationAccessGranted(
            MediaNotificationListenerService.isNotificationAccessEnabled(appContext)
        )
    }

    fun refreshMediaVolume() {
        val curr = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        _mediaVolume.value = curr to max
    }

    fun setMediaVolume(volume: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val clamped = volume.coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
        if (clamped > 0) lastSavedNonZeroVolume = clamped
        _mediaVolume.value = clamped to max
    }

    fun stepVolumeUp() {
        hapticManager.performSliderTick()
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
        refreshMediaVolume()
    }

    fun stepVolumeDown() {
        hapticManager.performSliderTick()
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
        refreshMediaVolume()
    }

    fun switchSource(source: PlaybackSource) {
        hapticManager.performTransportClick()
        PlaybackStateBus.setSelectedSource(source)
        viewModelScope.launch {
            settingsRepo.updatePreferredSource(source)
        }
    }

    fun togglePlayPause() {
        hapticManager.performTransportClick()
        PlaybackStateBus.playPause()
    }

    fun skipNext() {
        hapticManager.performTransportClick()
        PlaybackStateBus.next()
    }

    fun skipPrevious() {
        hapticManager.performTransportClick()
        PlaybackStateBus.previous()
    }

    fun seekTo(ms: Long) {
        hapticManager.performSliderTick()
        _livePositionMs.value = ms
        PlaybackStateBus.seekTo(ms)
    }

    fun toggleShuffle() {
        hapticManager.performTransportClick()
        PlaybackStateBus.toggleShuffle()
    }

    fun cycleRepeat() {
        hapticManager.performTransportClick()
        PlaybackStateBus.cycleRepeat()
    }

    fun skipToQueueItem(item: QueueTrack) {
        hapticManager.performTransportClick()
        PlaybackStateBus.skipToQueueItem(item)
    }

    fun startSleepTimer(minutes: Int) {
        hapticManager.performTransportClick()
        PlaybackStateBus.startSleepTimer(minutes)
        showStatusToast("Sleep timer set for $minutes min")
        if (_playbackSnapshot.value.hasActiveSession) {
            PlaybackForegroundService.syncExternalNotification(appContext)
        }
    }

    fun startEndOfTrackSleepTimer() {
        hapticManager.performTransportClick()
        PlaybackStateBus.startEndOfTrackSleepTimer()
        showStatusToast("Sleep timer: pausing at end of track")
        if (_playbackSnapshot.value.hasActiveSession) {
            PlaybackForegroundService.syncExternalNotification(appContext)
        }
    }

    fun addSleepTimerMinutes(extraMinutes: Int) {
        hapticManager.performTransportClick()
        PlaybackStateBus.addSleepTimerMinutes(extraMinutes)
        if (_playbackSnapshot.value.hasActiveSession) {
            PlaybackForegroundService.syncExternalNotification(appContext)
        }
    }

    fun cancelSleepTimer() {
        hapticManager.performTransportClick()
        PlaybackStateBus.cancelSleepTimer()
        showStatusToast("Sleep timer turned off")
    }

    fun triggerSongRadio() {
        hapticManager.performTransportClick()
        PlaybackStateBus.requestSongRadio(appContext)
    }

    fun performSliderHapticTick() {
        hapticManager.performSliderTick()
    }

    fun setUpNextTabVisible(visible: Boolean) {
        isUpNextTabOpen = visible
        if (!visible) return
        val snap = _playbackSnapshot.value
        if (snap.hasActiveSession &&
            snap.source == PlaybackSource.EXTERNAL &&
            snap.queue.isEmpty() &&
            snap.track.title.isNotBlank() &&
            _recommendationsState.value is RecommendationsUiState.Idle
        ) {
            val cleaned = TrackMetadataCleaner.clean(snap.track.title, snap.track.artist)
            loadRecommendations(cleaned.title, cleaned.artist, snap.track.album)
        }
    }

    fun scanLocalLibrary() {
        if (_isScanningLibrary.value) return
        viewModelScope.launch {
            _isScanningLibrary.value = true
            _localLibrary.value = LocalMediaScanner.scanAudioLibrary(appContext)
            _isScanningLibrary.value = false
        }
    }

    fun playLocalTracks(tracks: List<LocalAudioTrack>, startIndex: Int = 0) {
        PlaybackForegroundService.playLocalTracks(appContext, tracks, startIndex)
    }

    fun retryLyrics() {
        val snap = _playbackSnapshot.value
        if (snap.hasActiveSession && snap.track.title.isNotBlank()) {
            val cacheKey = "${snap.track.title.trim()}|${snap.track.artist.trim()}|${snap.track.localUri}"
            lyricsSessionCache.remove(cacheKey)
            loadLyricsForTrack(snap)
        }
    }

    fun nudgeVocalOffset(deltaMs: Long) {
        hapticManager.performSliderTick()
        val current = _lyricsState.value
        if (current is LyricsUiState.Ready) {
            val newOffset = (current.vocalOffsetMs + deltaMs).coerceIn(-10_000L, 20_000L)
            val updated = current.copy(vocalOffsetMs = newOffset)
            _lyricsState.value = updated
            lastLoadedTrackKey?.let { lyricsSessionCache[it] = updated }
            lastCleanedTrackPair?.let { (t, a) ->
                geminiCache.updateCachedVocalOffset(t, a, newOffset)
            }
        }
    }

    fun retryRecommendations() {
        val snap = _playbackSnapshot.value
        if (snap.track.title.isNotBlank()) {
            val cleaned = TrackMetadataCleaner.clean(snap.track.title, snap.track.artist)
            loadRecommendations(cleaned.title, cleaned.artist, snap.track.album, forceRefresh = true)
        }
    }

    private fun loadLyricsForTrack(snap: PlaybackSnapshot) {
        lyricsJob?.cancel()
        val track = snap.track
        val sessionKey = "${track.title.trim()}|${track.artist.trim()}|${track.localUri}"
        lyricsSessionCache[sessionKey]?.let { cached ->
            _lyricsState.value = cached
            return
        }

        val cleaned = TrackMetadataCleaner.clean(track.title, track.artist)
        lastCleanedTrackPair = cleaned.title to cleaned.artist

        lyricsJob = viewModelScope.launch {
            _lyricsState.value = LyricsUiState.Loading
            try {
                var rawLines: List<LyricLine> = emptyList()
                var isSynced = false
                var origin = LyricsOrigin.LRCLIB

                // 1. Check ID3 embedded USLT/SYLT lyrics first for local files
                if (snap.source == PlaybackSource.LOCAL && track.localUri != null) {
                    val embedded = withContext(Dispatchers.IO) {
                        Id3LyricsExtractor.extract(appContext, track.localUri)
                    }
                    if (embedded.syncedLyrics.isNotEmpty()) {
                        rawLines = embedded.syncedLyrics
                        isSynced = true
                        origin = LyricsOrigin.ID3_EMBEDDED
                    } else if (embedded.unsyncedLyrics.isNotEmpty()) {
                        rawLines = embedded.unsyncedLyrics
                        isSynced = false
                        origin = LyricsOrigin.ID3_EMBEDDED
                    }
                }

                val durationSec = (track.durationMs / 1000L).toInt().takeIf { it > 0 }

                // 2. Source 1: LRCLIB (/api/get -> /api/search)
                if (rawLines.isEmpty()) {
                    val lrcMatch = querySyncedCatalog(
                        api = LrcLibClient.api,
                        cleanTitle = cleaned.title,
                        cleanArtist = cleaned.artist,
                        durationSec = durationSec
                    )
                    if (lrcMatch != null) {
                        val (lines, synced) = extractLinesFromCatalogRecord(lrcMatch)
                        if (lines.isNotEmpty()) {
                            rawLines = lines
                            isSynced = synced
                            origin = LyricsOrigin.LRCLIB
                        }
                    }
                }

                // 3. Source 2: Karalyr (/api/get -> /api/search)
                if (rawLines.isEmpty()) {
                    val karalyrMatch = querySyncedCatalog(
                        api = KaralyrClient.api,
                        cleanTitle = cleaned.title,
                        cleanArtist = cleaned.artist,
                        durationSec = durationSec
                    )
                    if (karalyrMatch != null) {
                        val (lines, synced) = extractLinesFromCatalogRecord(karalyrMatch)
                        if (lines.isNotEmpty()) {
                            rawLines = lines
                            isSynced = synced
                            origin = LyricsOrigin.KARALYR
                        }
                    }
                }

                // 4. Source 3: Lyrics.ovh (/v1/<artist>/<title>) plain text fallback
                if (rawLines.isEmpty() && cleaned.title.isNotBlank() && cleaned.artist.isNotBlank()) {
                    val ovhResp = withContext(Dispatchers.IO) {
                        runCatching {
                            LyricsOvhClient.api.getLyrics(
                                artist = cleaned.artist,
                                title = cleaned.title
                            )
                        }.getOrNull()
                    }?.takeIf { it.isSuccessful }?.body()
                    val plainText = ovhResp?.lyrics?.takeIf { it.isNotBlank() }
                    if (plainText != null) {
                        val plainLines = LrcParser.fromPlainText(plainText)
                        if (plainLines.isNotEmpty()) {
                            rawLines = plainLines
                            isSynced = false
                            origin = LyricsOrigin.LYRICS_OVH
                        }
                    }
                }

                // 5. All sources empty/not found -> No-Lyrics Full-Screen Artwork Mode
                if (rawLines.isEmpty()) {
                    _lyricsState.value = LyricsUiState.Empty(
                        "Lyrics for this song is not available on any source."
                    )
                    return@launch
                }

                // 6. Check persistent + memory Gemini cache before making any API call
                val cachedAi = geminiCache.getCachedLyricsRefinement(
                    trackTitle = cleaned.title,
                    artistName = cleaned.artist,
                    rawLines = rawLines
                )
                if (cachedAi != null) {
                    val readyCached = LyricsUiState.Ready(
                        lines = cachedAi.cleanedLines,
                        isSynced = isSynced,
                        origin = LyricsOrigin.AI_ENHANCED,
                        vocalOffsetMs = cachedAi.vocalOffsetMs,
                        aiNote = cachedAi.note ?: "AI cleaned & intro-aligned"
                    )
                    lyricsSessionCache[sessionKey] = readyCached
                    _lyricsState.value = readyCached
                    return@launch
                }

                // Publish fetched lyrics immediately so UI responds fast
                _lyricsState.value = LyricsUiState.Ready(
                    lines = rawLines,
                    isSynced = isSynced,
                    origin = origin,
                    vocalOffsetMs = 0L,
                    aiNote = null
                )

                val aiResult = geminiService.refineLyricsAndEstimateOffset(
                    title = cleaned.title,
                    artist = cleaned.artist,
                    durationMs = track.durationMs,
                    rawLines = rawLines
                )

                aiResult.onSuccess { refined ->
                    geminiCache.saveLyricsRefinement(cleaned.title, cleaned.artist, refined)
                    val readyState = LyricsUiState.Ready(
                        lines = refined.cleanedLines,
                        isSynced = isSynced,
                        origin = LyricsOrigin.AI_ENHANCED,
                        vocalOffsetMs = refined.vocalOffsetMs,
                        aiNote = refined.note ?: "AI cleaned & intro-aligned"
                    )
                    lyricsSessionCache[sessionKey] = readyState
                    _lyricsState.value = readyState
                }.onFailure {
                    val fallbackReady = LyricsUiState.Ready(
                        lines = rawLines,
                        isSynced = isSynced,
                        origin = origin,
                        vocalOffsetMs = 0L,
                        aiNote = null
                    )
                    lyricsSessionCache[sessionKey] = fallbackReady
                    _lyricsState.value = fallbackReady
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading lyrics across sources", e)
                _lyricsState.value = LyricsUiState.Empty(
                    "Lyrics for this song is not available on any source."
                )
            }
        }
    }

    private suspend fun querySyncedCatalog(
        api: LrcLibApi,
        cleanTitle: String,
        cleanArtist: String,
        durationSec: Int?
    ): LrcLibTrackResponse? {
        if (cleanTitle.isBlank()) return null

        var matched = if (cleanArtist.isNotBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    api.getLyrics(
                        trackName = cleanTitle,
                        artistName = cleanArtist,
                        albumName = null,
                        durationSeconds = durationSec
                    )
                }.getOrNull()
            }?.takeIf { it.isSuccessful }?.body()
        } else {
            null
        }

        if (matched == null || matched.syncedLyrics.isNullOrBlank()) {
            val primaryQuery = listOf(cleanTitle, cleanArtist)
                .filter { it.isNotBlank() }
                .joinToString(" ")

            val searchResults = withContext(Dispatchers.IO) {
                runCatching { api.searchLyrics(primaryQuery) }.getOrNull()
            }?.takeIf { it.isSuccessful }?.body().orEmpty()

            var bestFromSearch = LrcSearchMatcher.pickBestMatch(
                candidates = searchResults,
                cleanTitle = cleanTitle,
                cleanArtist = cleanArtist,
                durationSeconds = durationSec
            )

            if (bestFromSearch == null && cleanArtist.isNotBlank()) {
                val titleOnlyResults = withContext(Dispatchers.IO) {
                    runCatching { api.searchLyrics(cleanTitle) }.getOrNull()
                }?.takeIf { it.isSuccessful }?.body().orEmpty()
                bestFromSearch = LrcSearchMatcher.pickBestMatch(
                    candidates = titleOnlyResults,
                    cleanTitle = cleanTitle,
                    cleanArtist = cleanArtist,
                    durationSeconds = durationSec
                )
            }

            if (bestFromSearch != null) {
                matched = bestFromSearch
            }
        }
        return matched
    }

    private fun extractLinesFromCatalogRecord(record: LrcLibTrackResponse): Pair<List<LyricLine>, Boolean> {
        val synced = record.syncedLyrics?.let { LrcParser.parse(it) }.orEmpty()
        if (synced.isNotEmpty()) return synced to true
        val plain = record.plainLyrics?.let { LrcParser.fromPlainText(it) }.orEmpty()
        return plain to false
    }

    private fun loadRecommendations(
        title: String,
        artist: String,
        album: String,
        forceRefresh: Boolean = false
    ) {
        recommendationsJob?.cancel()
        if (!forceRefresh) {
            geminiCache.getCachedRecommendations(title, artist)?.let { cached ->
                _recommendationsState.value = RecommendationsUiState.Success(cached)
                return
            }
        }

        recommendationsJob = viewModelScope.launch {
            _recommendationsState.value = RecommendationsUiState.Loading
            val result = geminiService.generateFallbackRecommendations(title, artist, album)
            result.onSuccess { list ->
                geminiCache.saveRecommendations(title, artist, list)
                _recommendationsState.value = RecommendationsUiState.Success(list)
            }.onFailure {
                // Clean fallback without exposing raw 429 Quota Exceeded / stack trace
                _recommendationsState.value = RecommendationsUiState.Fallback(
                    badge = "Standard Queue Mode",
                    message = "Curated suggestions are resting due to rate limits or offline status. Play from your local library below or retry anytime."
                )
            }
        }
    }

    fun launchSearchIntent(context: Context, rec: AiRecommendation) {
        val query = "${rec.title} ${rec.artist}".trim()
        val mediaSearchIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_TITLE, rec.title)
            putExtra(MediaStore.EXTRA_MEDIA_ARTIST, rec.artist)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(mediaSearchIntent)
        }.onFailure {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(webIntent) }
        }
    }

    // --- Pocket / Dim Lock Mode & Customizable Hardware/Motion Gestures ---

    fun setDimLockActive(active: Boolean) {
        val wasActive = _isDimLockActive.value
        hapticManager.performGestureConfirmation()
        _isDimLockActive.value = active
        if (active) {
            if (!wasActive) {
                _pendingStatsDelta.value = _pendingStatsDelta.value.copy(currentDimLockSessionMs = 0L)
                viewModelScope.launch { statsRepo.recordDimLockSessionStarted() }
            }
            val s = _settings.value
            if (s.wristFlickEnabled || s.faceDownEnabled) {
                motionDetector.start()
            }
            showGestureFeedback("Pocket Dim Lock On")
        } else {
            motionDetector.stop()
            _gestureFeedback.value = null
            notifyUserInteraction()
            if (wasActive) {
                viewModelScope.launch {
                    flushPendingStats()
                    _pendingStatsDelta.value = _pendingStatsDelta.value.copy(currentDimLockSessionMs = 0L)
                }
            }
        }
    }

    fun handleDimVolumeDownShortPress() {
        executeGestureAction(_settings.value.volDownShortAction, "Vol-")
    }

    fun handleDimVolumeUpShortPress() {
        executeGestureAction(_settings.value.volUpShortAction, "Vol+")
    }

    fun handleDimVolumeUpLongPress() {
        executeGestureAction(_settings.value.volUpLongAction, "Hold Vol+")
    }

    fun handleDimVolumeDownLongPress() {
        executeGestureAction(_settings.value.volDownLongAction, "Hold Vol-")
    }

    private fun executeGestureAction(action: GestureAction, triggerLabel: String) {
        if (action != GestureAction.NONE) {
            hapticManager.performGestureConfirmation()
        }
        when (action) {
            GestureAction.PLAY_PAUSE -> {
                PlaybackStateBus.playPause()
                val nowPlaying = !PlaybackStateBus.currentSnapshot().isPlaying
                showGestureFeedback("$triggerLabel: ${if (nowPlaying) "Resumed" else "Paused"}")
            }
            GestureAction.NEXT_TRACK -> {
                PlaybackStateBus.next()
                showGestureFeedback("$triggerLabel: Next Track")
            }
            GestureAction.PREVIOUS_TRACK -> {
                PlaybackStateBus.previous()
                showGestureFeedback("$triggerLabel: Previous Track")
            }
            GestureAction.VOLUME_UP -> {
                stepVolumeUp()
                val (curr, max) = _mediaVolume.value
                showGestureFeedback("$triggerLabel: Volume ${(curr * 100) / max}%")
            }
            GestureAction.VOLUME_DOWN -> {
                stepVolumeDown()
                val (curr, max) = _mediaVolume.value
                showGestureFeedback("$triggerLabel: Volume ${(curr * 100) / max}%")
            }
            GestureAction.MUTE_TOGGLE -> {
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (current > 0) {
                    lastSavedNonZeroVolume = current
                    setMediaVolume(0)
                    showGestureFeedback("$triggerLabel: Muted")
                } else {
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    setMediaVolume(lastSavedNonZeroVolume.coerceIn(2, max))
                    showGestureFeedback("$triggerLabel: Unmuted")
                }
            }
            GestureAction.NONE -> Unit
        }
    }

    private fun showGestureFeedback(message: String) {
        feedbackJob?.cancel()
        _gestureFeedback.value = message
        feedbackJob = viewModelScope.launch {
            delay(2000L)
            _gestureFeedback.value = null
        }
    }

    private fun showStatusToast(message: String) {
        toastJob?.cancel()
        _statusToast.value = message
        toastJob = viewModelScope.launch {
            delay(2800L)
            _statusToast.value = null
        }
    }

    // --- Equalizer & Settings Mutators ---

    fun setEqEnabled(enabled: Boolean) {
        audioEffectsManager.setEnabled(enabled)
        viewModelScope.launch { settingsRepo.updateEqEnabled(enabled) }
    }

    fun selectEqPreset(preset: EqPreset) {
        hapticManager.performTransportClick()
        audioEffectsManager.applyPreset(preset, _settings.value.customBandLevels)
        val levels = audioEffectsManager.state.value.bands.map { it.levelMb.toInt() }
        viewModelScope.launch { settingsRepo.updateEqPreset(preset, levels) }
    }

    fun setEqBandLevel(bandIndex: Short, levelMb: Short) {
        audioEffectsManager.setBandLevel(bandIndex, levelMb)
        val levels = audioEffectsManager.state.value.bands.map { it.levelMb.toInt() }
        viewModelScope.launch { settingsRepo.updateEqPreset(EqPreset.CUSTOM, levels) }
    }

    fun setBassBoost(strength: Int) {
        audioEffectsManager.setBassBoostStrength(strength)
        viewModelScope.launch { settingsRepo.updateBassBoost(strength) }
    }

    fun setMotionGesturesEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateMotionGestures(enabled) }
        if (!enabled) {
            motionDetector.stop()
        } else if (_isDimLockActive.value) {
            motionDetector.start()
        }
    }

    fun setMotionSensitivity(sensitivity: Float) {
        motionDetector.updateSensitivity(sensitivity)
        viewModelScope.launch { settingsRepo.updateMotionSensitivity(sensitivity) }
    }

    fun setWristFlickEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateWristFlickEnabled(enabled) }
    }

    fun setWristFlickSensitivity(sensitivity: Float) {
        viewModelScope.launch { settingsRepo.updateWristFlickSensitivity(sensitivity) }
    }

    fun setFaceDownEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateFaceDownEnabled(enabled) }
    }

    fun setFaceDownSensitivity(sensitivity: Float) {
        viewModelScope.launch { settingsRepo.updateFaceDownSensitivity(sensitivity) }
    }

    fun updateVolUpShortAction(action: GestureAction) {
        viewModelScope.launch { settingsRepo.updateVolUpShortAction(action) }
    }

    fun updateVolDownShortAction(action: GestureAction) {
        viewModelScope.launch { settingsRepo.updateVolDownShortAction(action) }
    }

    fun updateVolUpLongAction(action: GestureAction) {
        viewModelScope.launch { settingsRepo.updateVolUpLongAction(action) }
    }

    fun updateVolDownLongAction(action: GestureAction) {
        viewModelScope.launch { settingsRepo.updateVolDownLongAction(action) }
    }

    fun updateWristFlickAction(action: GestureAction) {
        viewModelScope.launch { settingsRepo.updateWristFlickAction(action) }
    }

    fun updateFaceDownAction(action: GestureAction) {
        viewModelScope.launch { settingsRepo.updateFaceDownAction(action) }
    }

    fun setThemeStyle(style: AppThemeStyle) {
        viewModelScope.launch { settingsRepo.updateThemeStyle(style) }
    }

    fun setUseAmoledBlack(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateUseAmoledBlack(enabled) }
    }

    fun setGlassBlurIntensity(intensity: Float) {
        viewModelScope.launch { settingsRepo.updateGlassBlurIntensity(intensity) }
    }

    fun importCustomFont(uri: Uri) {
        viewModelScope.launch {
            _fontUploadStatus.value = "Importing font…"
            val result = FontManager.importFontFromUri(appContext, uri)
            result.onSuccess { (path, name) ->
                lastLoadedFontPath = path
                _customFontFamily.value = withContext(Dispatchers.IO) {
                    FontManager.loadFontFamily(path)
                }
                settingsRepo.updateCustomFont(path, name)
                _fontUploadStatus.value = "Applied font: $name"
            }.onFailure { err ->
                _fontUploadStatus.value = err.message ?: "Could not load selected font."
            }
        }
    }

    fun resetCustomFont() {
        viewModelScope.launch {
            FontManager.clearCustomFont(appContext)
            lastLoadedFontPath = null
            _customFontFamily.value = null
            settingsRepo.updateCustomFont(null, null)
            _fontUploadStatus.value = "Reset to default Plus Jakarta Sans"
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        hapticManager.enabled = enabled
        if (enabled) hapticManager.previewIntensity(_settings.value.hapticIntensity)
        viewModelScope.launch { settingsRepo.updateHapticsEnabled(enabled) }
    }

    fun setHapticIntensity(intensity: HapticIntensity) {
        hapticManager.intensity = intensity
        hapticManager.previewIntensity(intensity)
        viewModelScope.launch { settingsRepo.updateHapticIntensity(intensity) }
    }

    fun setHapticOnTransport(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateHapticOnTransport(enabled) }
    }

    fun setHapticOnSliders(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateHapticOnSliders(enabled) }
    }

    fun setHapticOnGestures(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateHapticOnGestures(enabled) }
    }

    fun setUse24HourClock(use24h: Boolean) {
        viewModelScope.launch { settingsRepo.updateUse24HourClock(use24h) }
    }

    fun setDimLockTimeoutSeconds(seconds: Int) {
        notifyUserInteraction()
        viewModelScope.launch { settingsRepo.updateDimLockTimeoutSeconds(seconds) }
    }

    fun setCustomAccentHex(hex: String?, switchToCustomTheme: Boolean = true) {
        viewModelScope.launch { settingsRepo.updateCustomAccentHex(hex, switchToCustomTheme) }
    }

    fun setCustomFontHex(hex: String?) {
        viewModelScope.launch { settingsRepo.updateCustomFontHex(hex) }
    }

    fun setLyricsTextScale(scale: Float) {
        viewModelScope.launch { settingsRepo.updateLyricsTextScale(scale) }
    }

    fun writeLyricsToUri(uri: Uri) {
        val state = _lyricsState.value
        val snap = _playbackSnapshot.value
        if (state !is LyricsUiState.Ready) {
            showStatusToast("No lyrics available to export")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val title = snap.track.title.ifBlank { "Untitled" }
                val artist = snap.track.artist.ifBlank { "Unknown Artist" }
                val sb = StringBuilder()
                sb.appendLine("[ti:$title]")
                sb.appendLine("[ar:$artist]")
                sb.appendLine("[al:${snap.track.album}]")
                sb.appendLine("[generator:MinimalPlayback]")
                sb.appendLine()
                state.lines.forEach { line ->
                    if (line.timestampMs > 0L) {
                        val m = line.timestampMs / 60_000L
                        val s = (line.timestampMs % 60_000L) / 1000L
                        val cs = (line.timestampMs % 1000L) / 10L
                        sb.appendLine(String.format(java.util.Locale.US, "[%02d:%02d.%02d] %s", m, s, cs, line.text))
                    } else {
                        sb.appendLine(line.text)
                    }
                }
                appContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) {
                    showStatusToast("Lyrics saved successfully")
                }
            }.onFailure { err ->
                Log.e(TAG, "Failed to write lyrics to uri", err)
                withContext(Dispatchers.Main) {
                    showStatusToast("Could not save lyrics")
                }
            }
        }
    }

    fun exportLyrics(context: Context) {
        val state = _lyricsState.value
        val snap = _playbackSnapshot.value
        if (state !is LyricsUiState.Ready) {
            showStatusToast("No synced lyrics available to export")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val title = snap.track.title.ifBlank { "Untitled" }
                val artist = snap.track.artist.ifBlank { "Unknown Artist" }
                val safeTitle = title.replace(Regex("[^A-Za-z0-9-_ ]"), "").trim()
                val safeArtist = artist.replace(Regex("[^A-Za-z0-9-_ ]"), "").trim()
                val file = java.io.File(appContext.cacheDir, "lyrics_${safeTitle}_${safeArtist}.lrc")

                val sb = StringBuilder()
                sb.appendLine("[ti:$title]")
                sb.appendLine("[ar:$artist]")
                sb.appendLine("[al:${snap.track.album}]")
                sb.appendLine("[generator:MinimalPlayback]")
                sb.appendLine()
                state.lines.forEach { line ->
                    val m = line.timestampMs / 60_000L
                    val s = (line.timestampMs % 60_000L) / 1000L
                    val cs = (line.timestampMs % 1000L) / 10L
                    sb.appendLine(String.format(java.util.Locale.US, "[%02d:%02d.%02d] %s", m, s, cs, line.text))
                }
                file.writeText(sb.toString())

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Lyrics: $title - $artist")
                    putExtra(Intent.EXTRA_TEXT, "Timestamped lyrics for $title by $artist generated by MinimalPlayback.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Export Lyrics (.lrc)").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                withContext(Dispatchers.Main) {
                    showStatusToast("Lyrics exported successfully")
                }
            }.onFailure { err ->
                Log.e(TAG, "Failed to export lyrics", err)
                withContext(Dispatchers.Main) {
                    showStatusToast("Could not export lyrics")
                }
            }
        }
    }

    fun resetColorsToDefault() {
        hapticManager.performTransportClick()
        viewModelScope.launch {
            settingsRepo.resetColorsToDefault()
            showStatusToast("Colors reset to theme default")
        }
    }

    fun onRecordAudioPermissionChanged(granted: Boolean) {
        audioVisualizerManager.setPermissionGranted(granted)
        val snap = _playbackSnapshot.value
        val session = if (snap.source == PlaybackSource.LOCAL && snap.activeAudioSessionId > 0) {
            snap.activeAudioSessionId
        } else {
            0
        }
        audioVisualizerManager.updatePlaybackContext(
            sessionId = session,
            isPlaying = snap.isPlaying && snap.hasActiveSession,
            isLifecycleResumed = isLifecycleResumed
        )
    }

    fun setVisualizerLifecycleResumed(resumed: Boolean) {
        isLifecycleResumed = resumed
        val snap = _playbackSnapshot.value
        val session = if (snap.source == PlaybackSource.LOCAL && snap.activeAudioSessionId > 0) {
            snap.activeAudioSessionId
        } else {
            0
        }
        audioVisualizerManager.updatePlaybackContext(
            sessionId = session,
            isPlaying = snap.isPlaying && snap.hasActiveSession,
            isLifecycleResumed = resumed
        )
    }

    private fun flushPendingStatsAsync() {
        viewModelScope.launch { flushPendingStats() }
    }

    private suspend fun flushPendingStats() {
        statsFlushMutex.withLock {
            val pending = _pendingStatsDelta.value
            if (pending.listenMs <= 0L && pending.dimLockMs <= 0L) return
            withContext(NonCancellable) {
                statsRepo.recordProgressDelta(
                    listeningDeltaMs = pending.listenMs,
                    dimLockDeltaMs = pending.dimLockMs,
                    currentDimLockSessionMs = pending.currentDimLockSessionMs,
                    artistName = pending.artistName,
                    source = pending.source
                )
            }
            val latest = _pendingStatsDelta.value
            _pendingStatsDelta.value = latest.copy(
                listenMs = (latest.listenMs - pending.listenMs).coerceAtLeast(0L),
                dimLockMs = (latest.dimLockMs - pending.dimLockMs).coerceAtLeast(0L),
                externalListenMs = (latest.externalListenMs - pending.externalListenMs).coerceAtLeast(0L),
                localListenMs = (latest.localListenMs - pending.localListenMs).coerceAtLeast(0L)
            )
        }
    }

    fun resetPlaybackStats() {
        hapticManager.performTransportClick()
        viewModelScope.launch {
            statsFlushMutex.withLock {
                _pendingStatsDelta.value = PendingStatsDelta()
                statsRepo.resetStats()
            }
            showStatusToast("Playback statistics cleared")
        }
    }

    override fun onCleared() {
        val pending = _pendingStatsDelta.value
        if (pending.listenMs > 0L || pending.dimLockMs > 0L) {
            CoroutineScope(Dispatchers.IO + NonCancellable).launch {
                runCatching {
                    statsRepo.recordProgressDelta(
                        listeningDeltaMs = pending.listenMs,
                        dimLockDeltaMs = pending.dimLockMs,
                        currentDimLockSessionMs = pending.currentDimLockSessionMs,
                        artistName = pending.artistName,
                        source = pending.source
                    )
                }
            }
        }
        runCatching { appContext.unregisterReceiver(volumeReceiver) }
        motionDetector.stop()
        audioEffectsManager.release()
        audioVisualizerManager.release()
        super.onCleared()
    }
}
