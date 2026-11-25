package com.example.ai

import android.util.Log
import com.example.model.AiRecommendation
import com.example.model.LyricLine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class GeminiLyricsRefinement(
    val cleanedLines: List<LyricLine>,
    val vocalOffsetMs: Long,
    val note: String?
)

class GeminiQuotaOrOfflineException(
    val isQuotaLimit: Boolean,
    message: String
) : Exception(message)

class GeminiLyricsAndQueueService {

    companion object {
        private const val TAG = "GeminiAudioAI"
        private const val MODEL_NAME = "gemini-3.8-flash"

        fun isQuotaOrRateLimit(e: Throwable): Boolean {
            if (e is GeminiQuotaOrOfflineException && e.isQuotaLimit) return true
            val msg = generateSequence(e) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" ")
                .lowercase()
            return msg.contains("429") ||
                msg.contains("quota") ||
                msg.contains("rate limit") ||
                msg.contains("resource_exhausted") ||
                msg.contains("too many requests")
        }

        fun isOfflineOrNetwork(e: Throwable): Boolean {
            if (e is UnknownHostException || e is ConnectException || e is SocketTimeoutException) return true
            val msg = generateSequence(e) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" ")
                .lowercase()
            return msg.contains("unable to resolve host") ||
                msg.contains("network") ||
                msg.contains("offline") ||
                msg.contains("failed to connect") ||
                msg.contains("timeout")
        }
    }

    private val lyricsSchema = Schema.obj(
        properties = mapOf(
            "vocalOffsetMs" to Schema.integer(
                description = "Recommended millisecond offset adjustment to align the first vocal entry if there is an instrumental intro (range -3000 to +12000, 0 if no adjustment needed)."
            ),
            "alignmentNote" to Schema.string(
                description = "Short 4-7 word note explaining cleanup or intro timing adjustment."
            ),
            "cleanedTexts" to Schema.array(
                items = Schema.string(),
                description = "Cleaned lyric lines in the exact same order and count as the input lines, with typos, stray metadata, or broken encoding fixed."
            )
        )
    )

    private val recommendationsSchema = Schema.obj(
        properties = mapOf(
            "recommendations" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Recommended song title"),
                        "artist" to Schema.string(description = "Primary artist name"),
                        "vibeTag" to Schema.string(description = "Short 1-2 word genre or mood tag"),
                        "reason" to Schema.string(description = "One concise sentence explaining why it fits next"),
                        "accentHex" to Schema.string(description = "6-character hex accent color like #38BDF8")
                    )
                ),
                description = "Exactly 5 curated similar tracks to play next."
            )
        )
    )

    private val lyricsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = lyricsSchema
            }
        )
    }

    private val recommendationsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = recommendationsSchema
            }
        )
    }

    suspend fun refineLyricsAndEstimateOffset(
        title: String,
        artist: String,
        durationMs: Long,
        rawLines: List<LyricLine>
    ): Result<GeminiLyricsRefinement> {
        if (rawLines.isEmpty()) {
            return Result.failure(IllegalArgumentException("No lyrics lines to refine"))
        }

        val cappedLines = rawLines.take(120)
        val formattedInput = cappedLines.mapIndexed { index, line ->
            "$index|[${line.timestampMs}ms]|${line.text}"
        }.joinToString("\n")

        val prompt = """
            You are a precision music lyrics editor and audio timing assistant.
            Track: "$title" by "$artist" (Duration: ${durationMs / 1000}s).

            Tasks:
            1. Clean obvious typos, stray LRC credit tags, broken Unicode characters, or awkward casing in each lyric line below.
               CRITICAL: Keep the EXACT same number of elements in "cleanedTexts" (${cappedLines.size}) in the exact same order.
            2. Inspect the first few timestamps and your musical knowledge of "$title" by "$artist" to estimate if the lyrics jump early during an instrumental intro.
               Return "vocalOffsetMs" in milliseconds (e.g., 0 if already well-synced, or positive ms like 1500 if vocals enter slightly later than the first tag). Clamp between -3000 and 12000.
            3. Provide a concise "alignmentNote" (max 7 words).

            Input lines:
            $formattedInput
        """.trimIndent()

        return try {
            val response = withTimeout(45_000L) {
                lyricsModel.generateContent(prompt)
            }
            val jsonStr = response.text ?: throw IllegalStateException("Empty Gemini lyrics response")
            val json = JSONObject(jsonStr)
            val offsetMs = json.optLong("vocalOffsetMs", 0L).coerceIn(-3000L, 12_000L)
            val note = json.optString("alignmentNote").takeIf { it.isNotBlank() }
            val cleanedArr = json.optJSONArray("cleanedTexts")

            val updatedLines = rawLines.mapIndexed { idx, original ->
                val cleanedText = if (cleanedArr != null && idx < cleanedArr.length()) {
                    cleanedArr.optString(idx).takeIf { it.isNotBlank() } ?: original.text
                } else {
                    original.text
                }
                if (cleanedText != original.text || original.words.isEmpty()) {
                    com.example.data.LrcParser.withRebuiltWords(original.copy(text = cleanedText))
                } else {
                    original
                }
            }

            Result.success(
                GeminiLyricsRefinement(
                    cleanedLines = updatedLines,
                    vocalOffsetMs = offsetMs,
                    note = note
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Lyrics refinement timed out; using standard LRCLIB timing")
            Result.failure(GeminiQuotaOrOfflineException(false, "Standard LRCLIB Sync"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini lyrics refinement unavailable: ${e.message}")
            val isQuota = isQuotaOrRateLimit(e)
            Result.failure(GeminiQuotaOrOfflineException(isQuota, "Standard LRCLIB Sync"))
        }
    }

    suspend fun generateFallbackRecommendations(
        title: String,
        artist: String,
        album: String
    ): Result<List<AiRecommendation>> {
        val prompt = """
            Recommend 5 songs to play next after "$title" by "$artist" (Album: "${album.ifBlank { "Unknown" }}").
            Match the tempo, mood, production texture, and genre seamlessly.
            Do not include "$title" itself.
            Provide a valid 6-char hex color for accentHex (e.g. #38BDF8, #F59E0B, #A78BFA, #34D399, #FB7185).
        """.trimIndent()

        return try {
            val response = withTimeout(45_000L) {
                recommendationsModel.generateContent(prompt)
            }
            val jsonStr = response.text ?: throw IllegalStateException("Empty Gemini queue response")
            val json = JSONObject(jsonStr)
            val arr = json.getJSONArray("recommendations")
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        AiRecommendation(
                            title = obj.optString("title", "Unknown Track"),
                            artist = obj.optString("artist", "Unknown Artist"),
                            vibeTag = obj.optString("vibeTag", "Mix"),
                            reason = obj.optString("reason", "Pairs smoothly with the active track."),
                            accentHex = obj.optString("accentHex", "#38BDF8")
                        )
                    )
                }
            }
            if (list.isEmpty()) {
                Result.failure(GeminiQuotaOrOfflineException(false, "Standard Queue Mode"))
            } else {
                Result.success(list)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Gemini recommendations timed out")
            Result.failure(GeminiQuotaOrOfflineException(false, "Standard Queue Mode"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini recommendations unavailable: ${e.message}")
            val isQuota = isQuotaOrRateLimit(e)
            Result.failure(GeminiQuotaOrOfflineException(isQuota, "Standard Queue Mode"))
        }
    }
}
