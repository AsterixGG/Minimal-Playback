package com.example.util

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FontManager {

    private const val FONT_DIR = "custom_fonts"

    suspend fun importFontFromUri(context: Context, uri: Uri): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val displayName = queryDisplayName(context, uri) ?: "CustomFont.ttf"
                val ext = displayName.substringAfterLast('.', "").lowercase()
                if (ext !in setOf("ttf", "otf")) {
                    throw IllegalArgumentException("Please select a valid .ttf or .otf font file.")
                }

                val dir = File(context.filesDir, FONT_DIR).apply { mkdirs() }
                // Clean older custom fonts so internal storage doesn't accumulate stale copies
                dir.listFiles()?.forEach { it.delete() }

                val destFile = File(dir, "active_custom_font.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Unable to open selected font file.")

                if (destFile.length() < 12L || !hasValidSfntHeader(destFile)) {
                    destFile.delete()
                    throw IllegalArgumentException("The selected file is not a valid TrueType or OpenType font.")
                }

                // Verify Android Typeface parser accepts the font before persisting
                val typeface = Typeface.createFromFile(destFile)
                if (typeface === Typeface.DEFAULT) {
                    destFile.delete()
                    throw IllegalArgumentException("Could not load font glyphs from this file.")
                }

                destFile.absolutePath to displayName
            }
        }

    fun loadFontFamily(path: String?): FontFamily? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val file = File(path)
            if (!file.exists() || !file.isFile) return null
            val androidTypeface = Typeface.createFromFile(file)
            FontFamily(androidx.compose.ui.text.font.Typeface(androidTypeface))
        }.getOrNull()
    }

    fun clearCustomFont(context: Context) {
        runCatching {
            File(context.filesDir, FONT_DIR).listFiles()?.forEach { it.delete() }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun hasValidSfntHeader(file: File): Boolean {
        val header = ByteArray(4)
        file.inputStream().use { stream ->
            if (stream.read(header) < 4) return false
        }
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        val b2 = header[2].toInt() and 0xFF
        val b3 = header[3].toInt() and 0xFF

        val isTrueType = b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00
        val ascii = String(header, Charsets.US_ASCII)
        val isOpenTypeOrCollection = ascii == "OTTO" || ascii == "true" || ascii == "typ1" || ascii == "ttcf"
        return isTrueType || isOpenTypeOrCollection
    }
}
