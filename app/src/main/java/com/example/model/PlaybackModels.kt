package com.example.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap

enum class PlaybackSource {
    EXTERNAL,
    LOCAL
}

enum class RepeatModeState {
    OFF,
    ALL,
    ONE
}

enum class EqPreset(val label: String) {
    FLAT("Flat"),
    BASS_BOOST("Bass Boost"),
    VOCAL("Vocal"),
    TREBLE("Treble"),
    CUSTOM("Custom")
}

enum class AppThemeStyle(val label: String) {
    ADAPTIVE_ART("Dynamic Album Art Accent"),
    AMOLED_PURE_BLACK("AMOLED Pure Black"),
    MIDNIGHT_BLUE("Midnight Blue"),
    CRIMSON_DARK("Crimson Dark"),
    EMERALD_DARK("Emerald Dark"),
    CUSTOM("Custom Color")
}

enum class DimLockTimeoutOption(val seconds: Int, val label: String) {
    DISABLED(0, "Disabled"),
    SEC_30(30, "30 sec"),
    MIN_1(60, "1 min"),
    MIN_2(120, "2 min"),
    MIN_5(300, "5 min"),
    MIN_10(600, "10 min")
}

enum class FontColorPreset(val label: String, val hex: String?) {
    AUTO("Auto / Default", null),
    PURE_WHITE("Pure White", "#FFFFFF"),
    SOFT_CREAM("Soft Cream", "#F5F5DC"),
    WARM_AMBER("Warm Amber", "#FFB300"),
    NEON_CYAN("Neon Cyan", "#00E5FF"),
    CUSTOM("Custom", null)
}

enum class GestureAction(val label: String) {
    PLAY_PAUSE("Play / Pause"),
    NEXT_TRACK("Next Track"),
    PREVIOUS_TRACK("Previous Track"),
    VOLUME_UP("Volume Up"),
    VOLUME_DOWN("Volume Down"),
    MUTE_TOGGLE("Mute / Unmute"),
    NONE("None")
}

enum class HapticIntensity(val label: String) {
    LIGHT("Light"),
    MEDIUM("Medium"),
    STRONG("Strong")
}

enum class SleepTimerMode {
    OFF,
    DURATION,
    END_OF_TRACK
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val mode: SleepTimerMode = SleepTimerMode.OFF,
    val totalDurationMs: Long = 0L,
    val remainingMs: Long = 0L,
    val selectedMinutes: Int = 0,
    val isFadingOut: Boolean = false
)

data class TrackArtworkState(
    val trackKey: String = "",
    val bitmap: Bitmap? = null,
    val imageBitmap: ImageBitmap? = null
)

data class TrackMetadata(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkBitmap: Bitmap? = null,
    val sourceAppLabel: String = "",
    val sourcePackageName: String = "",
    val localUri: Uri? = null
)

data class PlaybackSnapshot(
    val source: PlaybackSource = PlaybackSource.EXTERNAL,
    val hasActiveSession: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val lastPositionUpdateElapsedMs: Long = 0L,
    val track: TrackMetadata = TrackMetadata(),
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF,
    val queue: List<QueueTrack> = emptyList(),
    val activeAudioSessionId: Int = 0
)

data class QueueTrack(
    val queueId: Long,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val isCurrent: Boolean = false,
    val isNext: Boolean = false,
    val localIndex: Int = -1
)

data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val endTimestampMs: Long = 0L
)

enum class LyricsOrigin {
    ID3_EMBEDDED,
    LRCLIB,
    KARALYR,
    LYRICS_OVH,
    AI_ENHANCED
}

sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data object AiAligning : LyricsUiState
    data class Ready(
        val lines: List<LyricLine>,
        val isSynced: Boolean,
        val origin: LyricsOrigin,
        val vocalOffsetMs: Long = 0L,
        val aiNote: String? = null
    ) : LyricsUiState
    data class Empty(val message: String) : LyricsUiState
    data class Error(val message: String) : LyricsUiState
}

data class AiRecommendation(
    val title: String,
    val artist: String,
    val vibeTag: String,
    val reason: String,
    val accentHex: String
)

sealed interface RecommendationsUiState {
    data object Idle : RecommendationsUiState
    data object Loading : RecommendationsUiState
    data class Success(val items: List<AiRecommendation>) : RecommendationsUiState
    data class Fallback(val badge: String, val message: String) : RecommendationsUiState
    data class Error(val message: String) : RecommendationsUiState
}

data class LocalAudioTrack(
    val id: Long,
    val contentUri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val folderName: String,
    val folderPath: String,
    val mimeType: String
)

data class LocalAlbumGroup(
    val albumName: String,
    val artistName: String,
    val tracks: List<LocalAudioTrack>
)

data class LocalFolderGroup(
    val folderName: String,
    val folderPath: String,
    val tracks: List<LocalAudioTrack>
)

data class EqBandInfo(
    val bandIndex: Short,
    val centerFreqHz: Int,
    val minLevelMb: Short,
    val maxLevelMb: Short,
    val levelMb: Short
)

data class EqualizerState(
    val isHardwareAvailable: Boolean = false,
    val enabled: Boolean = false,
    val preset: EqPreset = EqPreset.FLAT,
    val bands: List<EqBandInfo> = defaultEqBands(),
    val bassBoostStrength: Int = 0
)

fun defaultEqBands(): List<EqBandInfo> = listOf(
    EqBandInfo(0, 60, -1500, 1500, 0),
    EqBandInfo(1, 230, -1500, 1500, 0),
    EqBandInfo(2, 910, -1500, 1500, 0),
    EqBandInfo(3, 3600, -1500, 1500, 0),
    EqBandInfo(4, 14000, -1500, 1500, 0)
)

data class ArtistHabitStat(
    val artistName: String,
    val listenMs: Long,
    val playCount: Int,
    val shareFraction: Float
)

data class PlaybackStatsState(
    val totalListeningMs: Long = 0L,
    val totalDimLockMs: Long = 0L,
    val longestDimLockSessionMs: Long = 0L,
    val dimLockSessionCount: Int = 0,
    val totalTracksPlayed: Int = 0,
    val externalListenMs: Long = 0L,
    val localListenMs: Long = 0L,
    val topArtists: List<ArtistHabitStat> = emptyList(),
    val uniqueArtistsCount: Int = 0
)
