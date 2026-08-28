package com.example.ytdlp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpLoggerTest {

    @Test
    fun `sanitizeUrl strips query parameters containing sensitive tokens`() {
        val sensitiveUrl = "https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/172000?token=secret123&key=mykey"
        val sanitized = YtDlpLogger.sanitizeUrl(sensitiveUrl)

        assertFalse(sanitized.contains("secret123"))
        assertFalse(sanitized.contains("mykey"))
        assertTrue(sanitized.contains("manifest.googlevideo.com"))
    }

    @Test
    fun `sanitizeUrl preserves standard clean video URLs`() {
        val cleanUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val sanitized = YtDlpLogger.sanitizeUrl(cleanUrl)

        assertTrue(sanitized.contains("dQw4w9WgXcQ"))
    }
}
