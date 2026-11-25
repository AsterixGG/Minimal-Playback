package com.example.media

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.data.TrackMetadataCleaner
import com.example.model.LocalAlbumGroup
import com.example.model.LocalAudioTrack
import com.example.model.LocalFolderGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LocalLibraryScanResult(
    val songs: List<LocalAudioTrack>,
    val albums: List<LocalAlbumGroup>,
    val folders: List<LocalFolderGroup>
)

object LocalMediaScanner {

    private val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "m4a", "wav", "ogg", "aac", "opus")

    suspend fun scanAudioLibrary(context: Context): LocalLibraryScanResult = withContext(Dispatchers.IO) {
        val songs = mutableListOf<LocalAudioTrack>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC
        )

        val selection = "${MediaStore.Audio.Media.DURATION} > 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = cursor.getString(dataCol).orEmpty()
                    val mime = cursor.getString(mimeCol).orEmpty()
                    val ext = path.substringAfterLast('.', "").lowercase()

                    val isAudioFormat = ext in SUPPORTED_EXTENSIONS ||
                        mime.startsWith("audio/")

                    if (!isAudioFormat) continue

                    val parentFile = File(path).parentFile
                    val folderName = parentFile?.name ?: "Music"
                    val folderPath = parentFile?.absolutePath ?: "/"

                    val rawTitle = cursor.getString(titleCol).orEmpty().ifBlank {
                        File(path).nameWithoutExtension
                    }
                    val rawArtist = cursor.getString(artistCol).orEmpty()
                    val cleaned = if (TrackMetadataCleaner.isUnknownArtist(rawArtist)) {
                        val fileNameFallback = File(path).nameWithoutExtension
                        val candidateTitle = if (rawTitle.contains(" - ")) rawTitle else fileNameFallback
                        TrackMetadataCleaner.clean(candidateTitle, rawArtist)
                    } else {
                        null
                    }

                    val resolvedTitle = cleaned?.takeIf { it.wasParsedFromFilename }?.title ?: rawTitle
                    val resolvedArtist = cleaned?.artist?.takeIf { it.isNotBlank() }
                        ?: rawArtist.takeIf { !TrackMetadataCleaner.isUnknownArtist(it) }
                        ?: "Unknown Artist"

                    songs.add(
                        LocalAudioTrack(
                            id = id,
                            contentUri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                id
                            ),
                            title = resolvedTitle,
                            artist = resolvedArtist,
                            album = cursor.getString(albumCol).orEmpty()
                                .takeIf { it != "<unknown>" && it.isNotBlank() }
                                ?: "Single",
                            albumId = cursor.getLong(albumIdCol),
                            durationMs = cursor.getLong(durationCol),
                            folderName = folderName,
                            folderPath = folderPath,
                            mimeType = mime
                        )
                    )
                }
            }
        }

        val albums = songs.groupBy { it.album }
            .map { (albumName, tracks) ->
                LocalAlbumGroup(
                    albumName = albumName,
                    artistName = tracks.firstOrNull()?.artist ?: "Unknown Artist",
                    tracks = tracks
                )
            }
            .sortedBy { it.albumName.lowercase() }

        val folders = songs.groupBy { it.folderPath }
            .map { (path, tracks) ->
                LocalFolderGroup(
                    folderName = tracks.firstOrNull()?.folderName ?: path,
                    folderPath = path,
                    tracks = tracks
                )
            }
            .sortedBy { it.folderName.lowercase() }

        LocalLibraryScanResult(songs, albums, folders)
    }
}
