package com.example.data

import android.content.Context
import com.example.ai.GeminiLyricsRefinement
import com.example.model.AiRecommendation
import com.example.model.LyricLine
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class GeminiCacheStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "gemini_response_cache"
        private const val PREFIX_LYRICS = "lyrics_"
        private const val PREFIX_RECS = "recs_"

        fun buildCacheKey(trackTitle: String, artistName: String): String {
            val t = trackTitle.trim().lowercase().replace(Regex("""\s+"""), " ")
            val a = artistName.trim().lowercase().replace(Regex("""\s+"""), " ")
            return "$t|$a"
        }
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lyricsMemCache = ConcurrentHashMap<String, CachedLyricsPayload>()
    private val recsMemCache = ConcurrentHashMap<String, List<AiRecommendation>>()

    private data class CachedLyricsPayload(
        val vocalOffsetMs: Long,
        val note: String?,
        val cleanedTexts: List<String>
    )

    fun getCachedLyricsRefinement(
        trackTitle: String,
        artistName: String,
        rawLines: List<LyricLine>
    ): GeminiLyricsRefinement? {
        if (rawLines.isEmpty()) return null
        val key = buildCacheKey(trackTitle, artistName)
        val payload = lyricsMemCache[key] ?: loadLyricsFromPrefs(key)?.also {
            lyricsMemCache[key] = it
        } ?: return null

        val updatedLines = rawLines.mapIndexed { idx, original ->
            val cleaned = payload.cleanedTexts.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: original.text
            if (cleaned != original.text || original.words.isEmpty()) {
                LrcParser.withRebuiltWords(original.copy(text = cleaned))
            } else {
                original
            }
        }
        return GeminiLyricsRefinement(
            cleanedLines = updatedLines,
            vocalOffsetMs = payload.vocalOffsetMs,
            note = payload.note
        )
    }

    fun saveLyricsRefinement(
        trackTitle: String,
        artistName: String,
        refinement: GeminiLyricsRefinement
    ) {
        val key = buildCacheKey(trackTitle, artistName)
        val payload = CachedLyricsPayload(
            vocalOffsetMs = refinement.vocalOffsetMs,
            note = refinement.note,
            cleanedTexts = refinement.cleanedLines.map { it.text }
        )
        lyricsMemCache[key] = payload
        val json = JSONObject().apply {
            put("vocalOffsetMs", payload.vocalOffsetMs)
            put("note", payload.note ?: "")
            put("cleanedTexts", JSONArray(payload.cleanedTexts))
        }
        prefs.edit().putString(PREFIX_LYRICS + key, json.toString()).apply()
    }

    fun updateCachedVocalOffset(trackTitle: String, artistName: String, newOffsetMs: Long) {
        val key = buildCacheKey(trackTitle, artistName)
        val existing = lyricsMemCache[key] ?: loadLyricsFromPrefs(key) ?: return
        val updated = existing.copy(vocalOffsetMs = newOffsetMs)
        lyricsMemCache[key] = updated
        val json = JSONObject().apply {
            put("vocalOffsetMs", updated.vocalOffsetMs)
            put("note", updated.note ?: "")
            put("cleanedTexts", JSONArray(updated.cleanedTexts))
        }
        prefs.edit().putString(PREFIX_LYRICS + key, json.toString()).apply()
    }

    private fun loadLyricsFromPrefs(key: String): CachedLyricsPayload? {
        val raw = prefs.getString(PREFIX_LYRICS + key, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val offset = json.optLong("vocalOffsetMs", 0L)
            val note = json.optString("note").takeIf { it.isNotBlank() }
            val arr = json.optJSONArray("cleanedTexts")
            val texts = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        add(arr.optString(i))
                    }
                }
            }
            CachedLyricsPayload(offset, note, texts)
        }.getOrNull()
    }

    fun getCachedRecommendations(trackTitle: String, artistName: String): List<AiRecommendation>? {
        val key = buildCacheKey(trackTitle, artistName)
        recsMemCache[key]?.let { return it }
        val raw = prefs.getString(PREFIX_RECS + key, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        AiRecommendation(
                            title = obj.optString("title", "Unknown Track"),
                            artist = obj.optString("artist", "Unknown Artist"),
                            vibeTag = obj.optString("vibeTag", "Mix"),
                            reason = obj.optString("reason", ""),
                            accentHex = obj.optString("accentHex", "#38BDF8")
                        )
                    )
                }
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()?.also {
            recsMemCache[key] = it
        }
    }

    fun saveRecommendations(
        trackTitle: String,
        artistName: String,
        items: List<AiRecommendation>
    ) {
        if (items.isEmpty()) return
        val key = buildCacheKey(trackTitle, artistName)
        recsMemCache[key] = items
        val arr = JSONArray().apply {
            items.forEach { rec ->
                put(
                    JSONObject().apply {
                        put("title", rec.title)
                        put("artist", rec.artist)
                        put("vibeTag", rec.vibeTag)
                        put("reason", rec.reason)
                        put("accentHex", rec.accentHex)
                    }
                )
            }
        }
        prefs.edit().putString(PREFIX_RECS + key, arr.toString()).apply()
    }
}
