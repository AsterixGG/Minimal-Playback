package com.example.media

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.os.SystemClock
import com.example.model.PlaybackSnapshot
import com.example.model.PlaybackSource
import com.example.model.QueueTrack
import com.example.model.SleepTimerMode
import com.example.model.SleepTimerState
import com.example.model.TrackMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

object PlaybackStateBus {

    private val busScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var appContext: Context? = null

    private val _selectedSource = MutableStateFlow(PlaybackSource.EXTERNAL)
    val selectedSource: StateFlow<PlaybackSource> = _selectedSource.asStateFlow()

    private val _externalSnapshot = MutableStateFlow(
        PlaybackSnapshot(source = PlaybackSource.EXTERNAL)
    )
    val externalSnapshot: StateFlow<PlaybackSnapshot> = _externalSnapshot.asStateFlow()

    private val _localSnapshot = MutableStateFlow(
        PlaybackSnapshot(source = PlaybackSource.LOCAL)
    )
    val localSnapshot: StateFlow<PlaybackSnapshot> = _localSnapshot.asStateFlow()

    private val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted.asStateFlow()

    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    private val _radioStatusEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val radioStatusEvents: SharedFlow<String> = _radioStatusEvents.asSharedFlow()

    private val _externalRadioFallbackEvents = MutableSharedFlow<TrackMetadata>(extraBufferCapacity = 4)
    val externalRadioFallbackEvents: SharedFlow<TrackMetadata> = _externalRadioFallbackEvents.asSharedFlow()

    private var sleepTimerJob: Job? = null
    private var sleepDeadlineElapsedMs: Long = 0L
    private var endOfTrackSignature: String? = null
    private var savedFadeStartVolume: Int? = null

    @Volatile
    var activeExternalController: MediaController? = null

    @Volatile
    var externalShuffleHandler: (() -> Unit)? = null

    @Volatile
    var localActionHandler: LocalTransportHandler? = null

    @Volatile
    var localVolumeScaleHandler: ((Float) -> Unit)? = null

    val activeSnapshot = combine(_selectedSource, _externalSnapshot, _localSnapshot) { source, ext, loc ->
        when (source) {
            PlaybackSource.EXTERNAL -> ext
            PlaybackSource.LOCAL -> loc
        }
    }

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun setSelectedSource(source: PlaybackSource) {
        _selectedSource.value = source
    }

    fun updateExternalSnapshot(snapshot: PlaybackSnapshot) {
        _externalSnapshot.value = snapshot
    }

    fun updateLocalSnapshot(snapshot: PlaybackSnapshot) {
        _localSnapshot.value = snapshot
    }

    fun setNotificationAccessGranted(granted: Boolean) {
        _notificationAccessGranted.value = granted
    }

    fun currentSnapshot(): PlaybackSnapshot {
        return when (_selectedSource.value) {
            PlaybackSource.EXTERNAL -> _externalSnapshot.value
            PlaybackSource.LOCAL -> _localSnapshot.value
        }
    }

    fun playPause() {
        when (_selectedSource.value) {
            PlaybackSource.LOCAL -> localActionHandler?.togglePlayPause()
            PlaybackSource.EXTERNAL -> {
                val controller = activeExternalController ?: return
                val isPlaying = _externalSnapshot.value.isPlaying
                if (isPlaying) {
                    controller.transportControls.pause()
                } else {
                    controller.transportControls.play()
                }
            }
        }
    }

    fun pause() {
        when (_selectedSource.value) {
            PlaybackSource.LOCAL -> localActionHandler?.pause()
            PlaybackSource.EXTERNAL -> activeExternalController?.transportControls?.pause()
        }
    }

    fun next() {
        when (_selectedSource.value) {
            PlaybackSource.LOCAL -> localActionHandler?.skipToNext()
            PlaybackSource.EXTERNAL -> activeExternalController?.transportControls?.skipToNext()
        }
    }

    fun previous() {
        when (_selectedSource.value) {
            PlaybackSource.LOCAL -> localActionHandler?.skipToPrevious()
            PlaybackSource.EXTERNAL -> activeExternalController?.transportControls?.skipToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        when (_selectedSource.value) {
            PlaybackSource.LOCAL -> localActionHandler?.seekTo(positionMs)
            PlaybackSource.EXTERNAL -> activeExternalController?.transportControls?.seekTo(positionMs)
        }
    }

    fun toggleShuffle() {
        when (_selectedSource.value) {
            PlaybackSource.LOCAL -> localActionHandler?.toggleShuffle()
            PlaybackSource.EXTERNAL -> externalShuffleHandler?.invoke()
        }
    }

    fun cycleRepeat() {
        if (_selectedSource.value == PlaybackSource.LOCAL) {
            localActionHandler?.cycleRepeat()
        }
    }

    fun skipToQueueItem(item: QueueTrack) {
        when (_selectedSource.value) {
            PlaybackSource.LOCAL -> {
                if (item.localIndex >= 0) {
                    localActionHandler?.skipToIndex(item.localIndex)
                }
            }
            PlaybackSource.EXTERNAL -> {
                if (item.queueId >= 0L) {
                    activeExternalController?.transportControls?.skipToQueueItem(item.queueId)
                }
            }
        }
    }

    fun requestSongRadio(contextOverride: Context? = null) {
        val ctx = contextOverride ?: appContext ?: return
        val snap = currentSnapshot()
        busScope.launch {
            SongRadioManager.triggerSongRadio(ctx, snap)
        }
    }

    fun emitRadioStatus(message: String) {
        _radioStatusEvents.tryEmit(message)
    }

    fun emitExternalRadioFallback(track: TrackMetadata) {
        _externalRadioFallbackEvents.tryEmit(track)
    }

    fun startSleepTimer(minutes: Int) {
        restoreVolumeIfNeeded()
        val clamped = minutes.coerceIn(1, 240)
        val totalMs = clamped * 60_000L
        endOfTrackSignature = null
        sleepDeadlineElapsedMs = SystemClock.elapsedRealtime() + totalMs
        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            mode = SleepTimerMode.DURATION,
            totalDurationMs = totalMs,
            remainingMs = totalMs,
            selectedMinutes = clamped,
            isFadingOut = false
        )
        launchDurationSleepTicker()
    }

    fun startEndOfTrackSleepTimer() {
        restoreVolumeIfNeeded()
        val snap = currentSnapshot()
        if (!snap.hasActiveSession) {
            emitRadioStatus("Play a track to set End of Track sleep timer")
            return
        }
        val sig = trackSignature(snap)
        endOfTrackSignature = sig
        sleepDeadlineElapsedMs = 0L
        val remaining = (snap.durationMs - interpolatedPositionMs(snap)).coerceAtLeast(0L)
        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            mode = SleepTimerMode.END_OF_TRACK,
            totalDurationMs = snap.durationMs,
            remainingMs = remaining,
            selectedMinutes = 0,
            isFadingOut = false
        )
        launchEndOfTrackTicker(sig)
    }

    fun cycleSleepTimerPreset() {
        val current = _sleepTimerState.value
        when {
            !current.isActive -> startSleepTimer(15)
            current.mode == SleepTimerMode.DURATION && current.selectedMinutes < 30 -> startSleepTimer(30)
            current.mode == SleepTimerMode.DURATION && current.selectedMinutes < 45 -> startSleepTimer(45)
            current.mode == SleepTimerMode.DURATION && current.selectedMinutes < 60 -> startSleepTimer(60)
            current.mode == SleepTimerMode.DURATION -> startEndOfTrackSleepTimer()
            else -> cancelSleepTimer()
        }
    }

    fun addSleepTimerMinutes(extraMinutes: Int) {
        val current = _sleepTimerState.value
        if (!current.isActive || current.mode != SleepTimerMode.DURATION) {
            startSleepTimer(extraMinutes)
            return
        }
        restoreVolumeIfNeeded()
        val extraMs = extraMinutes * 60_000L
        sleepDeadlineElapsedMs += extraMs
        val newRemaining = (sleepDeadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(1000L)
        _sleepTimerState.value = current.copy(
            totalDurationMs = current.totalDurationMs + extraMs,
            remainingMs = newRemaining,
            selectedMinutes = current.selectedMinutes + extraMinutes,
            isFadingOut = false
        )
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepDeadlineElapsedMs = 0L
        endOfTrackSignature = null
        restoreVolumeIfNeeded()
        _sleepTimerState.value = SleepTimerState()
    }

    private fun launchDurationSleepTicker() {
        sleepTimerJob?.cancel()
        sleepTimerJob = busScope.launch {
            while (true) {
                val remaining = sleepDeadlineElapsedMs - SystemClock.elapsedRealtime()
                if (remaining <= 5_000L) {
                    performSmoothFadeOutAndPause(remaining.coerceIn(500L, 5_000L))
                    break
                }
                val current = _sleepTimerState.value
                _sleepTimerState.value = current.copy(
                    isActive = true,
                    mode = SleepTimerMode.DURATION,
                    remainingMs = remaining,
                    isFadingOut = false
                )
                delay(1000L)
            }
        }
    }

    private fun launchEndOfTrackTicker(targetSignature: String) {
        sleepTimerJob?.cancel()
        sleepTimerJob = busScope.launch {
            while (true) {
                val snap = currentSnapshot()
                if (!snap.hasActiveSession) {
                    _sleepTimerState.value = SleepTimerState()
                    break
                }
                val currentSig = trackSignature(snap)
                if (currentSig != targetSignature) {
                    // Track transitioned cleanly — pause immediately
                    pause()
                    _sleepTimerState.value = SleepTimerState()
                    endOfTrackSignature = null
                    break
                }
                val remaining = if (snap.durationMs > 0L) {
                    (snap.durationMs - interpolatedPositionMs(snap)).coerceAtLeast(0L)
                } else {
                    0L
                }
                if (snap.durationMs > 12_000L && snap.isPlaying && remaining in 1L..5_200L) {
                    performSmoothFadeOutAndPause(remaining.coerceIn(1_200L, 5_000L))
                    break
                }
                _sleepTimerState.value = _sleepTimerState.value.copy(
                    isActive = true,
                    mode = SleepTimerMode.END_OF_TRACK,
                    totalDurationMs = snap.durationMs,
                    remainingMs = remaining,
                    isFadingOut = false
                )
                delay(500L)
            }
        }
    }

    private suspend fun performSmoothFadeOutAndPause(fadeDurationMs: Long) {
        val audioManager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val startVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        savedFadeStartVolume = startVol.takeIf { it > 0 }

        val steps = 20
        val stepDelayMs = (fadeDurationMs / steps).coerceAtLeast(50L)

        for (step in 1..steps) {
            val remainingFrac = 1f - (step.toFloat() / steps.toFloat())
            val remainingFadeMs = ((steps - step) * stepDelayMs).coerceAtLeast(0L)
            _sleepTimerState.value = _sleepTimerState.value.copy(
                isActive = true,
                remainingMs = remainingFadeMs,
                isFadingOut = true
            )
            localVolumeScaleHandler?.invoke(remainingFrac.coerceIn(0f, 1f))
            if (audioManager != null && startVol > 0) {
                val targetVol = (startVol * remainingFrac).toInt().coerceIn(0, startVol)
                runCatching {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                }
            }
            delay(stepDelayMs)
        }

        pause()
        _sleepTimerState.value = SleepTimerState()
        sleepDeadlineElapsedMs = 0L
        endOfTrackSignature = null

        delay(850L)
        restoreVolumeIfNeeded()
    }

    private fun restoreVolumeIfNeeded() {
        localVolumeScaleHandler?.invoke(1f)
        val saved = savedFadeStartVolume ?: return
        savedFadeStartVolume = null
        val audioManager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, saved.coerceIn(1, max), 0)
        }
    }

    private fun trackSignature(snap: PlaybackSnapshot): String {
        return "${snap.track.id}|${snap.track.title.trim()}|${snap.track.artist.trim()}"
    }

    fun interpolatedPositionMs(snapshot: PlaybackSnapshot): Long {
        if (!snapshot.hasActiveSession) return 0L
        val base = snapshot.positionMs
        if (!snapshot.isPlaying || snapshot.lastPositionUpdateElapsedMs <= 0L) {
            return base.coerceIn(0L, snapshot.durationMs.coerceAtLeast(base))
        }
        val delta = (SystemClock.elapsedRealtime() - snapshot.lastPositionUpdateElapsedMs)
            .coerceAtLeast(0L)
        val estimated = base + (delta * snapshot.playbackSpeed).toLong()
        return if (snapshot.durationMs > 0L) {
            estimated.coerceIn(0L, snapshot.durationMs)
        } else {
            estimated.coerceAtLeast(0L)
        }
    }
}

interface LocalTransportHandler {
    fun togglePlayPause()
    fun pause()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun skipToIndex(index: Int)
}
