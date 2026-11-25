package com.example.data

import com.example.model.LyricLine
import com.example.model.LyricWord
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@JsonClass(generateAdapter = true)
data class LrcLibTrackResponse(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

@JsonClass(generateAdapter = true)
data class LyricsOvhResponse(
    val lyrics: String? = null,
    val error: String? = null
)

interface LrcLibApi {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationSeconds: Int? = null
    ): Response<LrcLibTrackResponse>

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): Response<List<LrcLibTrackResponse>>
}

interface LyricsOvhApi {
    @GET("v1/{artist}/{title}")
    suspend fun getLyrics(
        @Path("artist") artist: String,
        @Path("title") title: String
    ): Response<LyricsOvhResponse>
}

private object SharedLyricsNetwork {
    const val USER_AGENT = "MinimalPlayback/1.0 (https://minimalplayback.app)"

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val okHttpClient: OkHttpClient by lazy {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logger)
            .connectTimeout(9, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}

object LrcLibClient {
    private const val BASE_URL = "https://lrclib.net/"

    val api: LrcLibApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(SharedLyricsNetwork.okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(SharedLyricsNetwork.moshi))
            .build()
            .create(LrcLibApi::class.java)
    }
}

object KaralyrClient {
    private const val BASE_URL = "https://karalyr.com/"

    val api: LrcLibApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(SharedLyricsNetwork.okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(SharedLyricsNetwork.moshi))
            .build()
            .create(LrcLibApi::class.java)
    }
}

object LyricsOvhClient {
    private const val BASE_URL = "https://api.lyrics.ovh/"

    val api: LyricsOvhApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(SharedLyricsNetwork.okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(SharedLyricsNetwork.moshi))
            .build()
            .create(LyricsOvhApi::class.java)
    }
}

data class CleanedTrackMetadata(
    val title: String,
    val artist: String,
    val wasParsedFromFilename: Boolean = false
)

object TrackMetadataCleaner {
    private val FILE_EXT_REGEX = Regex("""\.(mp3|flac|m4a|wav|ogg|aac|opus)$""", RegexOption.IGNORE_CASE)
    private val PAREN_NOISE_REGEX = Regex(
        """[\(\[]\s*(?:deluxe(?:\s+edition|\s+version)?|remaster(?:ed)?(?:\s+\d{4})?|\d{4}\s+remaster(?:ed)?|official\s*(?:audio|video|music\s*video|lyric\s*video|visualizer)|original\s+motion\s+picture\s+soundtrack|from\s+[^)\]]+|lyric\s*video|audio|explicit|clean|bonus\s*track|single\s*version|album\s*version|radio\s*edit|acoustic(?:\s+version)?|live(?:\s+at\s+[^\)\]]+)?|feat\.?\s*[^\)\]]+|ft\.?\s*[^\)\]]+|featuring\s+[^\)\]]+|with\s+[^\)\]]+)\s*[\)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val DASH_NOISE_REGEX = Regex(
        """\s*[-–—]\s*(?:remaster(?:ed)?(?:\s+\d{4})?|\d{4}\s+remaster(?:ed)?|deluxe(?:\s+edition|\s+version)?|official\s*(?:audio|video|music\s*video|lyric\s*video)|original\s+motion\s+picture\s+soundtrack|single(?:\s+version)?|album\s+version|radio\s+edit|bonus\s*track|explicit|clean|acoustic|live(?:\s+.*)?)$""",
        RegexOption.IGNORE_CASE
    )
    private val TRAILING_FEAT_REGEX = Regex(
        """\s+(?:feat\.?|ft\.?|featuring)\s+.+$""",
        RegexOption.IGNORE_CASE
    )
    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    fun isUnknownArtist(rawArtist: String): Boolean {
        val trimmed = rawArtist.trim()
        return trimmed.isEmpty() ||
            trimmed.equals("<unknown>", ignoreCase = true) ||
            trimmed.equals("Unknown Artist", ignoreCase = true) ||
            trimmed.equals("unknown", ignoreCase = true)
    }

    fun clean(rawTitle: String, rawArtist: String): CleanedTrackMetadata {
        var workingTitle = rawTitle.trim().replace(FILE_EXT_REGEX, "").trim()
        var workingArtist = if (isUnknownArtist(rawArtist)) "" else rawArtist.trim()
        var parsedFromFilename = false

        // Parse "Song Title - Artist" when artist metadata is missing/unknown
        if (workingArtist.isEmpty()) {
            val separators = listOf(" - ", " – ", " — ")
            for (sep in separators) {
                val idx = workingTitle.lastIndexOf(sep)
                if (idx > 0 && idx + sep.length < workingTitle.length) {
                    val left = workingTitle.substring(0, idx).trim()
                    val right = workingTitle.substring(idx + sep.length).trim()
                    val looksLikeNoiseSuffix = Regex(
                        """^(?:remaster(?:ed)?(?:\s+\d{4})?|\d{4}\s+remaster(?:ed)?|deluxe.*|official.*|bonus\s*track|explicit|clean|single.*|live.*)$""",
                        RegexOption.IGNORE_CASE
                    ).matches(right)
                    if (!looksLikeNoiseSuffix && left.isNotEmpty() && right.isNotEmpty()) {
                        workingTitle = left
                        workingArtist = right
                        parsedFromFilename = true
                        break
                    }
                }
            }
        }

        val cleanedTitle = cleanTitleString(workingTitle)
        val cleanedArtist = cleanArtistString(workingArtist)

        return CleanedTrackMetadata(
            title = cleanedTitle.ifBlank { workingTitle.ifBlank { rawTitle.trim() } },
            artist = cleanedArtist,
            wasParsedFromFilename = parsedFromFilename
        )
    }

    fun cleanTitleString(title: String): String {
        var result = title.trim().replace(FILE_EXT_REGEX, "")
        repeat(3) {
            result = result
                .replace(PAREN_NOISE_REGEX, " ")
                .replace(DASH_NOISE_REGEX, "")
                .replace(TRAILING_FEAT_REGEX, "")
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()
        }
        return result
    }

    fun cleanArtistString(artist: String): String {
        if (isUnknownArtist(artist)) return ""
        return artist.trim()
            .replace(PAREN_NOISE_REGEX, " ")
            .replace(TRAILING_FEAT_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }
}

object LrcSearchMatcher {
    fun pickBestMatch(
        candidates: List<LrcLibTrackResponse>,
        cleanTitle: String,
        cleanArtist: String,
        durationSeconds: Int?
    ): LrcLibTrackResponse? {
        val valid = candidates.filter {
            !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank()
        }
        if (valid.isEmpty()) return null

        val targetTitle = cleanTitle.lowercase().trim()
        val targetArtist = cleanArtist.lowercase().trim()

        return valid.maxByOrNull { item ->
            var score = 0
            if (!item.syncedLyrics.isNullOrBlank()) {
                score += 1000
            }

            val itemTitle = TrackMetadataCleaner.cleanTitleString(item.trackName.orEmpty()).lowercase()
            val itemArtist = item.artistName.orEmpty().lowercase().trim()

            if (targetTitle.isNotEmpty()) {
                when {
                    itemTitle == targetTitle -> score += 120
                    itemTitle.contains(targetTitle) || targetTitle.contains(itemTitle) -> score += 60
                }
            }

            if (targetArtist.isNotEmpty()) {
                when {
                    itemArtist == targetArtist -> score += 100
                    itemArtist.contains(targetArtist) || targetArtist.contains(itemArtist) -> score += 50
                }
            }

            if (durationSeconds != null && durationSeconds > 0 && item.duration != null) {
                val diff = abs(item.duration - durationSeconds)
                when {
                    diff <= 3.0 -> score += 50
                    diff <= 10.0 -> score += 30
                    diff <= 20.0 -> score += 10
                }
            }
            score
        }
    }
}

object LrcParser {
    private val LINE_TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TIMESTAMP_REGEX = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")

    private data class ParsedRawLine(
        val timestampMs: Long,
        val rawContent: String
    )

    fun parse(rawLrc: String): List<LyricLine> {
        val rawEntries = mutableListOf<ParsedRawLine>()
        rawLrc.lines().forEach { line ->
            val matches = LINE_TIMESTAMP_REGEX.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val contentAfterLineTags = line.replace(LINE_TIMESTAMP_REGEX, "").trim()
                val plainCheck = contentAfterLineTags.replace(WORD_TIMESTAMP_REGEX, "").trim()
                if (plainCheck.isNotEmpty()) {
                    matches.forEach { m ->
                        val ts = parseMatchTimestamp(m)
                        rawEntries.add(ParsedRawLine(ts, contentAfterLineTags))
                    }
                }
            }
        }

        val sorted = rawEntries.sortedBy { it.timestampMs }
        return sorted.mapIndexed { idx, entry ->
            val nextTs = sorted.getOrNull(idx + 1)?.timestampMs
            val lineEndMs = if (nextTs != null && nextTs > entry.timestampMs) {
                (entry.timestampMs + (nextTs - entry.timestampMs).coerceIn(850L, 12_000L))
            } else {
                entry.timestampMs + 3_800L
            }

            val wordMatches = WORD_TIMESTAMP_REGEX.findAll(entry.rawContent).toList()
            val cleanText = entry.rawContent
                .replace(WORD_TIMESTAMP_REGEX, " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

            val words = if (wordMatches.isNotEmpty()) {
                parseEnhancedWords(entry.rawContent, entry.timestampMs, lineEndMs)
            } else {
                buildProportionalWords(cleanText, entry.timestampMs, lineEndMs)
            }

            LyricLine(
                timestampMs = entry.timestampMs,
                text = cleanText,
                words = words,
                endTimestampMs = lineEndMs
            )
        }
    }

    fun fromPlainText(rawPlain: String): List<LyricLine> {
        return rawPlain.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { idx, txt ->
                val start = idx * 3_500L
                val end = start + 3_500L
                LyricLine(
                    timestampMs = start,
                    text = txt,
                    words = buildProportionalWords(txt, start, end),
                    endTimestampMs = end
                )
            }
    }

    fun withRebuiltWords(line: LyricLine): LyricLine {
        val endMs = if (line.endTimestampMs > line.timestampMs) {
            line.endTimestampMs
        } else {
            line.timestampMs + 3_500L
        }
        return line.copy(
            endTimestampMs = endMs,
            words = buildProportionalWords(line.text, line.timestampMs, endMs)
        )
    }

    private fun parseMatchTimestamp(match: MatchResult): Long {
        val min = match.groupValues[1].toLongOrNull() ?: 0L
        val sec = match.groupValues[2].toLongOrNull() ?: 0L
        val fracStr = match.groupValues[3]
        val msPart = when (fracStr.length) {
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            3 -> fracStr.toLongOrNull() ?: 0L
            else -> 0L
        }
        return min * 60_000L + sec * 1_000L + msPart
    }

    private fun parseEnhancedWords(
        rawContent: String,
        lineStartMs: Long,
        lineEndMs: Long
    ): List<LyricWord> {
        val matches = WORD_TIMESTAMP_REGEX.findAll(rawContent).toList()
        if (matches.isEmpty()) return emptyList()

        val temp = mutableListOf<Pair<Long, String>>()
        // Handle leading text before first <mm:ss.xx> tag if any
        val prefix = rawContent.substring(0, matches.first().range.first).trim()
        if (prefix.isNotEmpty()) {
            temp.add(lineStartMs to prefix)
        }

        matches.forEachIndexed { idx, match ->
            val tagTime = parseMatchTimestamp(match)
            val textStart = match.range.last + 1
            val textEnd = matches.getOrNull(idx + 1)?.range?.first ?: rawContent.length
            val segment = rawContent.substring(textStart, textEnd).trim()
            if (segment.isNotEmpty()) {
                // Split multi-word segment proportionally within this tag window if needed
                temp.add(tagTime to segment)
            }
        }

        val words = mutableListOf<LyricWord>()
        temp.forEachIndexed { idx, (segStart, segText) ->
            val segEnd = temp.getOrNull(idx + 1)?.first?.takeIf { it > segStart }
                ?: lineEndMs.coerceAtLeast(segStart + 300L)
            val subWords = segText.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            if (subWords.size <= 1) {
                if (subWords.size == 1) {
                    words.add(LyricWord(subWords[0], segStart, segEnd))
                }
            } else {
                words.addAll(buildProportionalWords(segText, segStart, segEnd))
            }
        }
        return words
    }

    fun buildProportionalWords(
        text: String,
        lineStartMs: Long,
        lineEndMs: Long
    ): List<LyricWord> {
        val tokens = text.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val duration = (lineEndMs - lineStartMs).coerceAtLeast(tokens.size * 120L)
        val totalWeight = tokens.sumOf { (it.length + 1) }.coerceAtLeast(1)

        var cursorMs = lineStartMs
        return tokens.mapIndexed { idx, token ->
            val weight = token.length + 1
            val slice = if (idx == tokens.lastIndex) {
                (lineStartMs + duration - cursorMs).coerceAtLeast(120L)
            } else {
                ((duration * weight) / totalWeight).coerceAtLeast(90L)
            }
            val word = LyricWord(
                text = token,
                startMs = cursorMs,
                endMs = cursorMs + slice
            )
            cursorMs += slice
            word
        }
    }
}
