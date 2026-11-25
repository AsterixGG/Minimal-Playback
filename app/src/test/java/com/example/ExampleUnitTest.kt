package com.example

import androidx.compose.ui.graphics.Color
import com.example.data.GeminiCacheStore
import com.example.data.LrcLibTrackResponse
import com.example.data.LrcParser
import com.example.data.LrcSearchMatcher
import com.example.data.PlaybackStatsCodec
import com.example.data.StoredArtistStat
import com.example.data.TrackMetadataCleaner
import com.example.model.AppThemeStyle
import com.example.model.DimLockTimeoutOption
import com.example.ui.theme.colorToHexString
import com.example.ui.theme.parseColorHex
import com.example.ui.theme.resolveThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun lrcParser_parsesSyncedTimestamps() {
        val sample = """
            [00:12.50] First verse line
            [01:05.120] Chorus entry
        """.trimIndent()

        val parsed = LrcParser.parse(sample)
        assertEquals(2, parsed.size)
        assertEquals(12_500L, parsed[0].timestampMs)
        assertEquals("First verse line", parsed[0].text)
        assertEquals(65_120L, parsed[1].timestampMs)
        assertEquals("Chorus entry", parsed[1].text)
    }

    @Test
    fun trackMetadataCleaner_stripsSuffixesAndParsesFilename() {
        val local = TrackMetadataCleaner.clean("Blank Space - Taylor Swift.mp3", "Unknown Artist")
        assertEquals("Blank Space", local.title)
        assertEquals("Taylor Swift", local.artist)
        assertTrue(local.wasParsedFromFilename)

        val noisy = TrackMetadataCleaner.clean(
            "All of Me (Deluxe Edition) - Remastered 2014 (Official Audio)",
            "John Legend feat. Someone"
        )
        assertEquals("All of Me", noisy.title)
        assertEquals("John Legend", noisy.artist)
    }

    @Test
    fun lrcSearchMatcher_prefersSyncedLyricsOverPlain() {
        val plainOnly = LrcLibTrackResponse(
            id = 1L,
            trackName = "All of Me",
            artistName = "John Legend",
            duration = 269.0,
            plainLyrics = "What would I do without your smart mouth",
            syncedLyrics = null
        )
        val synced = LrcLibTrackResponse(
            id = 2L,
            trackName = "All of Me",
            artistName = "John Legend",
            duration = 269.0,
            plainLyrics = "What would I do without your smart mouth",
            syncedLyrics = "[00:08.10] What would I do without your smart mouth"
        )

        val best = LrcSearchMatcher.pickBestMatch(
            candidates = listOf(plainOnly, synced),
            cleanTitle = "All of Me",
            cleanArtist = "John Legend",
            durationSeconds = 269
        )
        assertNotNull(best)
        assertEquals(2L, best?.id)
    }

    @Test
    fun geminiCacheKey_normalizesCaseAndWhitespace() {
        val k1 = GeminiCacheStore.buildCacheKey("  All Of Me ", "John   Legend ")
        val k2 = GeminiCacheStore.buildCacheKey("all of me", "john legend")
        assertEquals(k1, k2)
    }

    @Test
    fun formatCountdownMs_formatsMinutesAndHoursCorrectly() {
        assertEquals("0:00", com.example.util.formatCountdownMs(0L))
        assertEquals("0:01", com.example.util.formatCountdownMs(500L))
        assertEquals("15:00", com.example.util.formatCountdownMs(15 * 60_000L))
        assertEquals("1:00:00", com.example.util.formatCountdownMs(60 * 60_000L))
    }

    @Test
    fun lrcParser_parsesEnhancedWordTimestamps() {
        val enhanced = """
            [00:10.00] <00:10.00>Hello <00:10.50>world <00:11.20>today
            [00:14.00] Next line
        """.trimIndent()

        val parsed = LrcParser.parse(enhanced)
        assertEquals(2, parsed.size)
        val firstLine = parsed[0]
        assertEquals("Hello world today", firstLine.text)
        assertEquals(3, firstLine.words.size)
        assertEquals("Hello", firstLine.words[0].text)
        assertEquals(10_000L, firstLine.words[0].startMs)
        assertEquals(10_500L, firstLine.words[0].endMs)
        assertEquals("world", firstLine.words[1].text)
        assertEquals(10_500L, firstLine.words[1].startMs)
        assertEquals(11_200L, firstLine.words[1].endMs)
        assertEquals("today", firstLine.words[2].text)
        assertEquals(11_200L, firstLine.words[2].startMs)
        assertEquals(14_000L, firstLine.words[2].endMs)
    }

    @Test
    fun lrcParser_generatesProportionalWordTimings() {
        val standard = """
            [00:05.00] Shine bright tonight
            [00:08.00] End
        """.trimIndent()

        val parsed = LrcParser.parse(standard)
        assertEquals(2, parsed.size)
        val words = parsed[0].words
        assertEquals(3, words.size)
        assertEquals(5_000L, words.first().startMs)
        assertTrue(words[0].endMs == words[1].startMs)
        assertTrue(words[1].endMs == words[2].startMs)
        assertEquals(8_000L, words.last().endMs)
    }

    @Test
    fun lrcParser_fromPlainTextCreatesWords() {
        val lines = LrcParser.fromPlainText("Line one\n\nLine two words")
        assertEquals(2, lines.size)
        assertEquals(2, lines[0].words.size)
        assertEquals(3, lines[1].words.size)
    }

    @Test
    fun resolveThemePalette_enforcesAmoledPureBlackAndIgnoresAlbumArt() {
        val songAPrimary = Color(0xFFFF3366)
        val songASecondary = Color(0xFF33FFCC)
        val songBPrimary = Color(0xFF8844FF)
        val songBSecondary = Color(0xFFFFCC00)

        val amoledA = resolveThemePalette(
            themeStyle = AppThemeStyle.AMOLED_PURE_BLACK,
            useAmoledBlack = true,
            trackPrimary = songAPrimary,
            trackSecondary = songASecondary
        )
        val amoledB = resolveThemePalette(
            themeStyle = AppThemeStyle.AMOLED_PURE_BLACK,
            useAmoledBlack = true,
            trackPrimary = songBPrimary,
            trackSecondary = songBSecondary
        )

        assertEquals(Color.Black, amoledA.voidBackground)
        assertEquals(Color.Black, amoledA.surfaceBackground)
        assertEquals(Color.Black, amoledA.cardBackground)
        assertEquals(amoledA.primaryAccent, amoledB.primaryAccent)
        assertEquals(amoledA.secondaryAccent, amoledB.secondaryAccent)
    }

    @Test
    fun resolveThemePalette_onlyAdaptiveUsesTrackColorsAndCustomUsesHex() {
        val trackPrimary = Color(0xFFFF4081)
        val trackSecondary = Color(0xFF448AFF)
        val customAccent = Color(0xFF00E676)

        val adaptive = resolveThemePalette(
            themeStyle = AppThemeStyle.ADAPTIVE_ART,
            trackPrimary = trackPrimary,
            trackSecondary = trackSecondary
        )
        assertEquals(trackPrimary, adaptive.primaryAccent)
        assertEquals(trackSecondary, adaptive.secondaryAccent)

        val midnight = resolveThemePalette(
            themeStyle = AppThemeStyle.MIDNIGHT_BLUE,
            trackPrimary = trackPrimary,
            trackSecondary = trackSecondary
        )
        assertTrue(midnight.primaryAccent != trackPrimary)
        assertTrue(midnight.secondaryAccent != trackSecondary)

        val custom = resolveThemePalette(
            themeStyle = AppThemeStyle.CUSTOM,
            trackPrimary = trackPrimary,
            trackSecondary = trackSecondary,
            customAccentColor = customAccent
        )
        assertEquals(customAccent, custom.primaryAccent)
    }

    @Test
    fun parseAndFormatColorHex_handlesValidAndInvalidInputs() {
        val parsed6 = parseColorHex("#FF8800")
        assertNotNull(parsed6)
        assertEquals("#FF8800", colorToHexString(parsed6!!))

        val parsedNoHash = parseColorHex(" 00e5ff ")
        assertNotNull(parsedNoHash)
        assertEquals("#00E5FF", colorToHexString(parsedNoHash!!))

        val parsed8 = parseColorHex("#80123456")
        assertNotNull(parsed8)

        assertNull(parseColorHex(""))
        assertNull(parseColorHex("#12345"))
        assertNull(parseColorHex("#ZZZZZZ"))
    }

    @Test
    fun dimLockTimeoutOptions_coverExpectedDurations() {
        val secondsList = DimLockTimeoutOption.entries.map { it.seconds }
        assertEquals(listOf(0, 30, 60, 120, 300, 600), secondsList)
    }

    @Test
    fun playbackStatsCodec_roundTripsAndRanksTopArtists() {
        val map = mutableMapOf<String, StoredArtistStat>()
        PlaybackStatsCodec.mergeArtistListenDelta(map, "Taylor Swift", 120_000L, playIncrement = 2)
        PlaybackStatsCodec.mergeArtistListenDelta(map, "  taylor swift ", 60_000L, playIncrement = 1)
        PlaybackStatsCodec.mergeArtistListenDelta(map, "Daft Punk | Live", 240_000L, playIncrement = 3)
        PlaybackStatsCodec.mergeArtistListenDelta(map, "Unknown Artist", 90_000L, playIncrement = 1)

        val encoded = PlaybackStatsCodec.encodeArtistMap(map)
        val decoded = PlaybackStatsCodec.decodeArtistMap(encoded)

        assertEquals(2, decoded.size)
        val top = PlaybackStatsCodec.computeTopArtists(decoded, limit = 5)
        assertEquals(2, top.size)
        assertEquals("Daft Punk | Live", top[0].artistName)
        assertEquals(240_000L, top[0].listenMs)
        assertEquals(1.0f, top[0].shareFraction, 0.001f)
        assertEquals("Taylor Swift", top[1].artistName)
        assertEquals(180_000L, top[1].listenMs)
        assertEquals(3, top[1].playCount)
        assertEquals(0.75f, top[1].shareFraction, 0.01f)
    }

    @Test
    fun playbackStatsCodec_formatsHabitDurationsClearly() {
        assertEquals("0s", PlaybackStatsCodec.formatHabitDuration(0L))
        assertEquals("45s", PlaybackStatsCodec.formatHabitDuration(45_000L))
        assertEquals("14m 20s", PlaybackStatsCodec.formatHabitDuration(14 * 60_000L + 20_000L))
        assertEquals("2h 5m", PlaybackStatsCodec.formatHabitDuration(2 * 3_600_000L + 5 * 60_000L))
    }

    @Test
    fun persistedAudioSettings_defaultsLyricsTextScaleToOne() {
        val defaults = com.example.data.PersistedAudioSettings()
        assertEquals(1.0f, defaults.lyricsTextScale, 0.001f)
    }
}
