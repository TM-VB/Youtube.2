package com.example.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameSanitizerTest {

    @Test
    fun `sanitize replaces illegal characters with underscore`() {
        val raw = "My Video: What? / <Best> * 2024 \"Edition\" | Episode 1"
        val result = FileNameSanitizer.sanitize(raw, "mp4")
        assertFalse(result.contains(":"))
        assertFalse(result.contains("?"))
        assertFalse(result.contains("/"))
        assertFalse(result.contains("<"))
        assertFalse(result.contains(">"))
        assertFalse(result.contains("*"))
        assertFalse(result.contains("\""))
        assertFalse(result.contains("|"))
        assertTrue(result.endsWith(".mp4"))
    }

    @Test
    fun `sanitize handles empty or blank string gracefully`() {
        val result = FileNameSanitizer.sanitize("   ", "mp4")
        assertTrue(result.startsWith("video_"))
        assertTrue(result.endsWith(".mp4"))
    }

    @Test
    fun `sanitize preserves normal title and extension`() {
        val result = FileNameSanitizer.sanitize("Clean Title", "mkv")
        assertEquals("Clean Title.mkv", result)
    }

    @Test
    fun `sanitize truncates excessively long title`() {
        val longTitle = "A".repeat(300)
        val result = FileNameSanitizer.sanitize(longTitle, "mp4")
        assertTrue(result.length <= 130)
        assertTrue(result.endsWith(".mp4"))
    }
}
