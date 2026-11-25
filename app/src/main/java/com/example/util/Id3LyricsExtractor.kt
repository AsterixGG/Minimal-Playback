package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.model.LyricLine
import java.io.InputStream
import java.nio.charset.Charset

data class EmbeddedAudioMetadata(
    val artwork: Bitmap? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val syncedLyrics: List<LyricLine> = emptyList(),
    val unsyncedLyrics: List<LyricLine> = emptyList()
)

object Id3LyricsExtractor {
    private const val TAG = "Id3LyricsExtractor"

    fun extract(context: Context, uri: Uri): EmbeddedAudioMetadata {
        var artwork: Bitmap? = null
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null && artBytes.isNotEmpty()) {
                artwork = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever failed for $uri: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        val (sylt, uslt) = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                parseId3v2Lyrics(stream)
            } ?: (emptyList<LyricLine>() to emptyList())
        } catch (e: Exception) {
            Log.w(TAG, "ID3v2 lyrics parse skipped for $uri: ${e.message}")
            emptyList<LyricLine>() to emptyList()
        }

        return EmbeddedAudioMetadata(
            artwork = artwork,
            title = title,
            artist = artist,
            album = album,
            syncedLyrics = sylt,
            unsyncedLyrics = uslt
        )
    }

    private fun parseId3v2Lyrics(input: InputStream): Pair<List<LyricLine>, List<LyricLine>> {
        val header = ByteArray(10)
        if (input.read(header) < 10) return emptyList<LyricLine>() to emptyList()
        if (header[0].toInt() != 'I'.code || header[1].toInt() != 'D'.code || header[2].toInt() != '3'.code) {
            return emptyList<LyricLine>() to emptyList()
        }

        val majorVersion = header[3].toInt() and 0xFF
        val flags = header[5].toInt() and 0xFF
        val tagSize = syncSafeInt(header, 6).coerceAtMost(2_000_000)
        if (tagSize <= 0) return emptyList<LyricLine>() to emptyList()

        val tagBytes = ByteArray(tagSize)
        var totalRead = 0
        while (totalRead < tagSize) {
            val read = input.read(tagBytes, totalRead, tagSize - totalRead)
            if (read <= 0) break
            totalRead += read
        }

        var offset = 0
        // Skip extended header if present (bit 6)
        if ((flags and 0x40) != 0 && totalRead >= 4) {
            val extSize = if (majorVersion >= 4) syncSafeInt(tagBytes, 0) else bigEndianInt(tagBytes, 0)
            offset += extSize.coerceAtLeast(0)
        }

        val syncedLines = mutableListOf<LyricLine>()
        val unsyncedLines = mutableListOf<LyricLine>()

        while (offset + 10 <= totalRead) {
            if (tagBytes[offset].toInt() == 0) break
            val frameId = String(tagBytes, offset, 4, Charsets.ISO_8859_1)
            val frameSize = if (majorVersion >= 4) {
                syncSafeInt(tagBytes, offset + 4)
            } else {
                bigEndianInt(tagBytes, offset + 4)
            }
            offset += 10
            if (frameSize <= 0 || offset + frameSize > totalRead) break

            if (frameId == "USLT") {
                parseUsltFrame(tagBytes, offset, frameSize, unsyncedLines)
            } else if (frameId == "SYLT") {
                parseSyltFrame(tagBytes, offset, frameSize, syncedLines)
            }
            offset += frameSize
        }

        val sortedSynced = syncedLines.sortedBy { it.timestampMs }
        val enrichedSynced = sortedSynced.mapIndexed { idx, line ->
            val nextTs = sortedSynced.getOrNull(idx + 1)?.timestampMs
            val endMs = if (nextTs != null && nextTs > line.timestampMs) {
                line.timestampMs + (nextTs - line.timestampMs).coerceIn(850L, 12_000L)
            } else {
                line.timestampMs + 3_800L
            }
            com.example.data.LrcParser.withRebuiltWords(line.copy(endTimestampMs = endMs))
        }
        return enrichedSynced to unsyncedLines
    }

    private fun parseUsltFrame(
        data: ByteArray,
        start: Int,
        length: Int,
        out: MutableList<LyricLine>
    ) {
        if (length < 5) return
        val encoding = data[start].toInt() and 0xFF
        val charset = charsetForId3Encoding(encoding)
        val isTwoByte = encoding == 1 || encoding == 2
        var pos = start + 4 // skip encoding(1) + language(3)
        val end = start + length
        // skip content descriptor until null terminator
        pos = findTerminatorEnd(data, pos, end, isTwoByte)
        if (pos >= end) return
        val rawText = String(data, pos, end - pos, charset).trim('\u0000', ' ', '\r', '\n')
        if (rawText.isBlank()) return

        // If USLT contains embedded LRC timestamps, parse them!
        val lrcParsed = com.example.data.LrcParser.parse(rawText)
        if (lrcParsed.isNotEmpty()) {
            out.addAll(lrcParsed)
        } else {
            rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { idx, line ->
                out.add(LyricLine(idx * 3500L, line))
            }
        }
    }

    private fun parseSyltFrame(
        data: ByteArray,
        start: Int,
        length: Int,
        out: MutableList<LyricLine>
    ) {
        if (length < 7) return
        val encoding = data[start].toInt() and 0xFF
        val charset = charsetForId3Encoding(encoding)
        val isTwoByte = encoding == 1 || encoding == 2
        val timestampFormat = data[start + 4].toInt() and 0xFF
        if (timestampFormat != 2) return // Only millisecond timestamps supported
        var pos = start + 6 // encoding(1) + lang(3) + format(1) + contentType(1)
        val end = start + length
        pos = findTerminatorEnd(data, pos, end, isTwoByte)

        while (pos < end) {
            val textEnd = findTerminatorStart(data, pos, end, isTwoByte)
            val lineText = String(data, pos, (textEnd - pos).coerceAtLeast(0), charset)
                .replace("\r", "")
                .replace("\n", "")
                .trim()
            val afterNull = findTerminatorEnd(data, textEnd, end, isTwoByte)
            if (afterNull + 4 > end) break
            val ms = bigEndianInt(data, afterNull).toLong() and 0xFFFFFFFFL
            if (lineText.isNotEmpty()) {
                out.add(LyricLine(ms, lineText))
            }
            pos = afterNull + 4
        }
    }

    private fun charsetForId3Encoding(encoding: Int): Charset = when (encoding) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    private fun findTerminatorStart(data: ByteArray, start: Int, end: Int, twoByte: Boolean): Int {
        var i = start
        if (twoByte) {
            while (i + 1 < end) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) return i
                i += 2
            }
        } else {
            while (i < end) {
                if (data[i].toInt() == 0) return i
                i++
            }
        }
        return end
    }

    private fun findTerminatorEnd(data: ByteArray, start: Int, end: Int, twoByte: Boolean): Int {
        val term = findTerminatorStart(data, start, end, twoByte)
        return if (twoByte) (term + 2).coerceAtMost(end) else (term + 1).coerceAtMost(end)
    }

    private fun syncSafeInt(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0x7F) shl 21) or
            ((b[offset + 1].toInt() and 0x7F) shl 14) or
            ((b[offset + 2].toInt() and 0x7F) shl 7) or
            (b[offset + 3].toInt() and 0x7F)
    }

    private fun bigEndianInt(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)
    }
}
