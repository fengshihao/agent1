package com.agent1.android.productivity.logic.business

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IncomingShareIntentParserTest {

    @Test
    fun parse_nullIntentReturnsNull() {
        assertNull(IncomingShareIntentParser.parse(null))
    }

    @Test
    fun parse_sendTextPlain() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "  hello share  ")
        }
        val payload = IncomingShareIntentParser.parse(intent)
        assertTrue(payload != null)
        assertEquals(emptyList<Uri>(), payload!!.fileUris)
        assertEquals("hello share", payload.sharedText)
    }

    @Test
    fun parse_sendWithStream() {
        val uri = Uri.parse("content://test/doc/1")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        val payload = IncomingShareIntentParser.parse(intent)
        assertEquals(listOf(uri), payload?.fileUris)
    }

    @Test
    fun parse_sendMultiple() {
        val a = Uri.parse("content://test/a")
        val b = Uri.parse("content://test/b")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(a, b))
        }
        val payload = IncomingShareIntentParser.parse(intent)
        assertEquals(listOf(a, b), payload?.fileUris)
    }

    @Test
    fun parse_unsupportedActionReturnsNull() {
        val intent = Intent(Intent.ACTION_VIEW)
        assertNull(IncomingShareIntentParser.parse(intent))
    }
}
