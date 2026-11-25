package com.example.media

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.util.Log
import com.example.data.TrackMetadataCleaner
import com.example.model.LocalAudioTrack
import com.example.model.PlaybackSnapshot
import com.example.model.PlaybackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

object SongRadioManager {

    private const val TAG = "SongRadioManager"

    suspend fun triggerSongRadio(context: Context, snapshot: PlaybackSnapshot) {
        if (!snapshot.hasActiveSession || snapshot.track.title.isBlank()) {
            PlaybackStateBus.emitRadioStatus("Start playing a track to launch Song Radio")
            return
        }

        val cleaned = TrackMetadataCleaner.clean(snapshot.track.title, snapshot.track.artist)

        if (snapshot.source == PlaybackSource.LOCAL) {
            startLocalRadio(context, snapshot, cleaned.title, cleaned.artist)
        } else {
            startExternalRadio(context, snapshot, cleaned.title, cleaned.artist)
        }
    }

    private suspend fun startLocalRadio(
        context: Context,
        snapshot: PlaybackSnapshot,
        cleanTitle: String,
        cleanArtist: String
    ) {
        val scanResult = withContext(Dispatchers.IO) {
            LocalMediaScanner.scanAudioLibrary(context)
        }
        val allSongs = scanResult.songs
        if (allSongs.isEmpty()) {
            PlaybackStateBus.emitRadioStatus("No local songs available for Local Radio")
            return
        }

        val currentTrack = allSongs.firstOrNull {
            it.contentUri == snapshot.track.localUri ||
                it.title.equals(snapshot.track.title, ignoreCase = true)
        }

        val candidatePool = allSongs.filter { track ->
            track.contentUri != snapshot.track.localUri
        }

        if (candidatePool.isEmpty()) {
            PlaybackStateBus.emitRadioStatus("Add more local tracks to mix Local Radio")
            return
        }

        val seedTitleWords = extractKeywords("$cleanTitle ${snapshot.track.album}")
        val targetArtist = cleanArtist.lowercase()
        val targetAlbum = (currentTrack?.album ?: snapshot.track.album).trim().lowercase()
        val targetFolder = currentTrack?.folderPath.orEmpty()
        val targetDuration = snapshot.durationMs

        val scoredQueue = candidatePool
            .map { track ->
                var score = Random.nextInt(0, 26)
                val trackArtist = TrackMetadataCleaner.cleanArtistString(track.artist).lowercase()
                val trackAlbum = track.album.trim().lowercase()

                if (targetArtist.isNotEmpty() && trackArtist == targetArtist) {
                    score += 75
                } else if (targetArtist.isNotEmpty() &&
                    (trackArtist.contains(targetArtist) || targetArtist.contains(trackArtist))
                ) {
                    score += 45
                }

                if (targetAlbum.isNotEmpty() && trackAlbum == targetAlbum) {
                    score += 40
                }

                if (targetFolder.isNotEmpty() && track.folderPath == targetFolder) {
                    score += 32
                }

                if (targetDuration > 0L && track.durationMs > 0L) {
                    val diff = abs(track.durationMs - targetDuration)
                    if (diff <= 35_000L) score += 16
                }

                val trackWords = extractKeywords("${track.title} ${track.album} ${track.folderName}")
                val overlap = seedTitleWords.intersect(trackWords).size
                score += overlap * 12

                track to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(30)

        PlaybackForegroundService.playLocalTracks(context, scoredQueue, startIndex = 0)
        PlaybackStateBus.emitRadioStatus("Local Radio started • ${scoredQueue.size} tracks")
    }

    private fun startExternalRadio(
        context: Context,
        snapshot: PlaybackSnapshot,
        cleanTitle: String,
        cleanArtist: String
    ) {
        val radioQuery = listOf(cleanTitle, cleanArtist, "Radio")
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val pkg = snapshot.track.sourcePackageName.takeIf { it.isNotBlank() }
        val pm = context.packageManager

        val mediaSearchIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            if (pkg != null) setPackage(pkg)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, radioQuery)
            putExtra(MediaStore.EXTRA_MEDIA_TITLE, cleanTitle)
            putExtra(MediaStore.EXTRA_MEDIA_ARTIST, cleanArtist)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (mediaSearchIntent.resolveActivity(pm) != null) {
            val launched = runCatching { context.startActivity(mediaSearchIntent) }.isSuccess
            if (launched) {
                PlaybackStateBus.emitRadioStatus("Launching $cleanTitle Radio…")
                return
            }
        }

        if (pkg != null) {
            val appSearchIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(pkg)
                putExtra(SearchManager.QUERY, radioQuery)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (appSearchIntent.resolveActivity(pm) != null) {
                val launched = runCatching { context.startActivity(appSearchIntent) }.isSuccess
                if (launched) {
                    PlaybackStateBus.emitRadioStatus("Searching $radioQuery…")
                    return
                }
            }
        }

        Log.i(TAG, "External app ($pkg) did not handle radio search intent; falling back to AI Radio")
        PlaybackStateBus.emitExternalRadioFallback(snapshot.track)
        PlaybackStateBus.emitRadioStatus("Curating AI Song Radio for $cleanTitle…")
    }

    private fun extractKeywords(input: String): Set<String> {
        return input.lowercase()
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.length >= 3 }
            .toSet()
    }
}
