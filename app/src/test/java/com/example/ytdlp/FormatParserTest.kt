package com.example.ytdlp

import com.example.domain.model.FormatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatParserTest {

    @Test
    fun `getCategorizedFormats separates video and audio properly`() {
        val formats = listOf(
            FormatOption(
                formatId = "137",
                ext = "mp4",
                resolution = "1080p",
                height = 1080,
                width = 1920,
                fps = 60.0,
                vcodec = "avc1.64002a",
                acodec = null,
                isVideoOnly = true,
                isAudioOnly = false
            ),
            FormatOption(
                formatId = "136",
                ext = "mp4",
                resolution = "720p",
                height = 720,
                width = 1280,
                fps = 30.0,
                vcodec = "avc1.4d401f",
                acodec = null,
                isVideoOnly = true,
                isAudioOnly = false
            ),
            FormatOption(
                formatId = "140",
                ext = "m4a",
                resolution = "Audio",
                height = null,
                width = null,
                fps = null,
                vcodec = null,
                acodec = "mp4a.40.2",
                bitrate = 128.0,
                isVideoOnly = false,
                isAudioOnly = true
            )
        )

        val (videoFormats, audioFormats, allFormats) = FormatParser.getCategorizedFormats(formats)

        assertEquals(2, videoFormats.size)
        assertEquals(1, audioFormats.size)
        assertEquals(3, allFormats.size)
        assertEquals("1080p", videoFormats[0].resolution)
        assertEquals("720p", videoFormats[1].resolution)
        assertEquals("Audio", audioFormats[0].resolution)
    }

    @Test
    fun `format option display properties are accurate`() {
        val videoFormat = FormatOption(
            formatId = "22",
            ext = "mp4",
            resolution = "720p",
            height = 720,
            fileSize = 52428800L, // 50 MB
            isCombined = true
        )
        assertTrue(videoFormat.displayTitle.contains("720p"))
        assertTrue(videoFormat.displayTitle.contains("mp4"))
        assertTrue(videoFormat.displaySubtitle.contains("50.0 MB"))

        val audioFormat = FormatOption(
            formatId = "251",
            ext = "opus",
            resolution = "Audio",
            bitrate = 160.0,
            isAudioOnly = true
        )
        assertTrue(audioFormat.displayTitle.contains("Audio"))
        assertTrue(audioFormat.displayTitle.contains("opus"))
        assertTrue(audioFormat.displayTitle.contains("160 kbps"))
    }
}
