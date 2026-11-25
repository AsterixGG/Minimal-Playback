package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ArtistHabitStat
import com.example.model.PlaybackSource
import com.example.model.PlaybackStatsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.roundToLong

private val Context.statsDataStore: DataStore<Preferences> by preferencesDataStore(name = "minimal_playback_stats")

data class StoredArtistStat(
    val artistName: String,
    val listenMs: Long,
    val playCount: Int
)

object PlaybackStatsCodec {
    private const val MAX_STORED_ARTISTS = 60

    fun normalizeArtistKey(artistName: String?): String? {
        val trimmed = artistName?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        val lower = trimmed.lowercase()
        if (lower == "unknown" || lower == "unknown artist" || lower == "<unknown>") {
            return null
        }
        return lower
    }

    fun encodeArtistMap(map: Map<String, StoredArtistStat>): String {
        if (map.isEmpty()) return ""
        return map.values
            .sortedWith(
                compareByDescending<StoredArtistStat> { it.listenMs }
                    .thenByDescending { it.playCount }
            )
            .take(MAX_STORED_ARTISTS)
            .joinToString("\n") { stat ->
                val encodedName = URLEncoder.encode(stat.artistName, "UTF-8")
                "$encodedName|${stat.listenMs.coerceAtLeast(0L)}|${stat.playCount.coerceAtLeast(0)}"
            }
    }

    fun decodeArtistMap(raw: String?): MutableMap<String, StoredArtistStat> {
        val result = linkedMapOf<String, StoredArtistStat>()
        if (raw.isNullOrBlank()) return result
        raw.lineSequence().forEach { line ->
            val parts = line.split('|')
            if (parts.size >= 3) {
                val decodedName = runCatching { URLDecoder.decode(parts[0], "UTF-8") }
                    .getOrNull()
                    ?.trim()
                val key = normalizeArtistKey(decodedName) ?: return@forEach
                val listenMs = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val playCount = parts[2].toIntOrNull()?.coerceAtLeast(0) ?: 0
                if (decodedName != null && (listenMs > 0L || playCount > 0)) {
                    val existing = result[key]
                    if (existing == null) {
                        result[key] = StoredArtistStat(decodedName, listenMs, playCount)
                    } else {
                        result[key] = existing.copy(
                            listenMs = existing.listenMs + listenMs,
                            playCount = existing.playCount + playCount
                        )
                    }
                }
            }
        }
        return result
    }

    fun mergeArtistListenDelta(
        map: MutableMap<String, StoredArtistStat>,
        artistName: String?,
        listenDeltaMs: Long,
        playIncrement: Int = 0
    ) {
        val key = normalizeArtistKey(artistName) ?: return
        val cleanDisplay = artistName!!.trim()
        val delta = listenDeltaMs.coerceAtLeast(0L)
        val plays = playIncrement.coerceAtLeast(0)
        if (delta == 0L && plays == 0) return

        val current = map[key]
        if (current == null) {
            map[key] = StoredArtistStat(
                artistName = cleanDisplay,
                listenMs = delta,
                playCount = plays
            )
        } else {
            map[key] = current.copy(
                artistName = if (current.artistName.length >= cleanDisplay.length) current.artistName else cleanDisplay,
                listenMs = current.listenMs + delta,
                playCount = current.playCount + plays
            )
        }
    }

    fun computeTopArtists(
        map: Map<String, StoredArtistStat>,
        limit: Int = 5
    ): List<ArtistHabitStat> {
        if (map.isEmpty()) return emptyList()
        val sorted = map.values
            .filter { it.listenMs > 0L || it.playCount > 0 }
            .sortedWith(
                compareByDescending<StoredArtistStat> { it.listenMs }
                    .thenByDescending { it.playCount }
            )
            .take(limit)
        if (sorted.isEmpty()) return emptyList()

        val maxListen = sorted.maxOf { it.listenMs }.coerceAtLeast(1L)
        val maxPlays = sorted.maxOf { it.playCount }.coerceAtLeast(1)

        return sorted.map { entry ->
            val share = if (entry.listenMs > 0L) {
                (entry.listenMs.toFloat() / maxListen.toFloat()).coerceIn(0.08f, 1f)
            } else {
                (entry.playCount.toFloat() / maxPlays.toFloat()).coerceIn(0.08f, 1f)
            }
            ArtistHabitStat(
                artistName = entry.artistName,
                listenMs = entry.listenMs,
                playCount = entry.playCount,
                shareFraction = share
            )
        }
    }

    fun formatHabitDuration(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1000.0).roundToLong()
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}

class PlaybackStatsRepository(private val context: Context) {

    private object Keys {
        val TOTAL_LISTENING_MS = longPreferencesKey("total_listening_ms")
        val TOTAL_DIM_LOCK_MS = longPreferencesKey("total_dim_lock_ms")
        val LONGEST_DIM_LOCK_SESSION_MS = longPreferencesKey("longest_dim_lock_session_ms")
        val DIM_LOCK_SESSION_COUNT = intPreferencesKey("dim_lock_session_count")
        val TOTAL_TRACKS_PLAYED = intPreferencesKey("total_tracks_played")
        val EXTERNAL_LISTEN_MS = longPreferencesKey("external_listen_ms")
        val LOCAL_LISTEN_MS = longPreferencesKey("local_listen_ms")
        val ARTIST_STATS_ENCODED = stringPreferencesKey("artist_stats_v1")
    }

    val statsFlow: Flow<PlaybackStatsState> = context.statsDataStore.data.map { prefs ->
        val artistMap = PlaybackStatsCodec.decodeArtistMap(prefs[Keys.ARTIST_STATS_ENCODED])
        PlaybackStatsState(
            totalListeningMs = (prefs[Keys.TOTAL_LISTENING_MS] ?: 0L).coerceAtLeast(0L),
            totalDimLockMs = (prefs[Keys.TOTAL_DIM_LOCK_MS] ?: 0L).coerceAtLeast(0L),
            longestDimLockSessionMs = (prefs[Keys.LONGEST_DIM_LOCK_SESSION_MS] ?: 0L).coerceAtLeast(0L),
            dimLockSessionCount = (prefs[Keys.DIM_LOCK_SESSION_COUNT] ?: 0).coerceAtLeast(0),
            totalTracksPlayed = (prefs[Keys.TOTAL_TRACKS_PLAYED] ?: 0).coerceAtLeast(0),
            externalListenMs = (prefs[Keys.EXTERNAL_LISTEN_MS] ?: 0L).coerceAtLeast(0L),
            localListenMs = (prefs[Keys.LOCAL_LISTEN_MS] ?: 0L).coerceAtLeast(0L),
            topArtists = PlaybackStatsCodec.computeTopArtists(artistMap, limit = 5),
            uniqueArtistsCount = artistMap.size
        )
    }

    suspend fun recordProgressDelta(
        listeningDeltaMs: Long,
        dimLockDeltaMs: Long,
        currentDimLockSessionMs: Long,
        artistName: String?,
        source: PlaybackSource
    ) {
        val listenDelta = listeningDeltaMs.coerceAtLeast(0L)
        val dimDelta = dimLockDeltaMs.coerceAtLeast(0L)
        if (listenDelta == 0L && dimDelta == 0L) return

        context.statsDataStore.edit { prefs ->
            if (listenDelta > 0L) {
                prefs[Keys.TOTAL_LISTENING_MS] =
                    ((prefs[Keys.TOTAL_LISTENING_MS] ?: 0L) + listenDelta).coerceAtLeast(0L)
                when (source) {
                    PlaybackSource.EXTERNAL -> {
                        prefs[Keys.EXTERNAL_LISTEN_MS] =
                            ((prefs[Keys.EXTERNAL_LISTEN_MS] ?: 0L) + listenDelta).coerceAtLeast(0L)
                    }
                    PlaybackSource.LOCAL -> {
                        prefs[Keys.LOCAL_LISTEN_MS] =
                            ((prefs[Keys.LOCAL_LISTEN_MS] ?: 0L) + listenDelta).coerceAtLeast(0L)
                    }
                }
                val artistMap = PlaybackStatsCodec.decodeArtistMap(prefs[Keys.ARTIST_STATS_ENCODED])
                PlaybackStatsCodec.mergeArtistListenDelta(artistMap, artistName, listenDelta, playIncrement = 0)
                prefs[Keys.ARTIST_STATS_ENCODED] = PlaybackStatsCodec.encodeArtistMap(artistMap)
            }

            if (dimDelta > 0L) {
                prefs[Keys.TOTAL_DIM_LOCK_MS] =
                    ((prefs[Keys.TOTAL_DIM_LOCK_MS] ?: 0L) + dimDelta).coerceAtLeast(0L)
                val previousBest = prefs[Keys.LONGEST_DIM_LOCK_SESSION_MS] ?: 0L
                if (currentDimLockSessionMs > previousBest) {
                    prefs[Keys.LONGEST_DIM_LOCK_SESSION_MS] = currentDimLockSessionMs
                }
            }
        }
    }

    suspend fun recordTrackPlayed(artistName: String?) {
        context.statsDataStore.edit { prefs ->
            prefs[Keys.TOTAL_TRACKS_PLAYED] = ((prefs[Keys.TOTAL_TRACKS_PLAYED] ?: 0) + 1).coerceAtLeast(1)
            val artistMap = PlaybackStatsCodec.decodeArtistMap(prefs[Keys.ARTIST_STATS_ENCODED])
            PlaybackStatsCodec.mergeArtistListenDelta(artistMap, artistName, listenDeltaMs = 0L, playIncrement = 1)
            prefs[Keys.ARTIST_STATS_ENCODED] = PlaybackStatsCodec.encodeArtistMap(artistMap)
        }
    }

    suspend fun recordDimLockSessionStarted() {
        context.statsDataStore.edit { prefs ->
            prefs[Keys.DIM_LOCK_SESSION_COUNT] = ((prefs[Keys.DIM_LOCK_SESSION_COUNT] ?: 0) + 1).coerceAtLeast(1)
        }
    }

    suspend fun resetStats() {
        context.statsDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
