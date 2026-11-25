package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MinimalPlayback", appName)
    }

    @Test
    fun `lrc parser parses synced timestamps accurately`() {
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
}
