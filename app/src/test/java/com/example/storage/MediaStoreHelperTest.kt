package com.example.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreHelperTest {

    @Test
    fun `getMimeType resolves standard video and audio extensions correctly`() {
        assertEquals("video/mp4", MediaStoreHelper.getMimeType("video.mp4"))
        assertEquals("video/x-matroska", MediaStoreHelper.getMimeType("video.mkv"))
        assertEquals("video/webm", MediaStoreHelper.getMimeType("video.webm"))
        assertEquals("audio/mpeg", MediaStoreHelper.getMimeType("audio.mp3"))
        assertEquals("audio/mp4", MediaStoreHelper.getMimeType("audio.m4a"))
        assertEquals("audio/opus", MediaStoreHelper.getMimeType("audio.opus"))
        assertEquals("audio/wav", MediaStoreHelper.getMimeType("audio.wav"))
        assertEquals("audio/flac", MediaStoreHelper.getMimeType("audio.flac"))
    }
}
