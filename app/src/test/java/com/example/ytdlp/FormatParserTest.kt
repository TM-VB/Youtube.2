package com.example.ytdlp

import com.example.domain.model.FormatInfo
import com.example.downloader.engine.DefaultFormatProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatParserTest {

    private val provider = DefaultFormatProvider()

    @Test
    fun `categorize separates video+audio, video-only, and audio-only properly`() {
        val formats = listOf(
            FormatInfo(
                formatId = "18",
                extension = "mp4",
                resolution = "360p",
                height = 360,
                width = 640,
                hasVideo = true,
                hasAudio = true,
                bitrate = 500.0,
                filesize = 10_000_000L
            ),
            FormatInfo(
                formatId = "137",
                extension = "mp4",
                resolution = "1080p",
                height = 1080,
                width = 1920,
                hasVideo = true,
                hasAudio = false,
                bitrate = 4000.0,
                filesize = 50_000_000L
            ),
            FormatInfo(
                formatId = "136",
                extension = "mp4",
                resolution = "720p",
                height = 720,
                width = 1280,
                hasVideo = true,
                hasAudio = false,
                bitrate = 2000.0,
                filesize = 25_000_000L
            ),
            FormatInfo(
                formatId = "140",
                extension = "m4a",
                resolution = "Audio",
                hasVideo = false,
                hasAudio = true,
                bitrate = 128.0,
                filesize = 5_000_000L
            )
        )

        val categorized = provider.categorize(formats)

        assertEquals(1, categorized.videoAndAudioFormats.size)
        assertEquals(2, categorized.videoOnlyFormats.size)
        assertEquals(1, categorized.audioOnlyFormats.size)
        assertEquals(4, categorized.allFormats.size)

        assertEquals("18", categorized.videoAndAudioFormats[0].formatId)
        assertEquals("137", categorized.videoOnlyFormats[0].formatId) // sorted highest resolution first
        assertEquals("136", categorized.videoOnlyFormats[1].formatId)
        assertEquals("140", categorized.audioOnlyFormats[0].formatId)
    }

    @Test
    fun `format info display properties are formatted accurately`() {
        val videoFormat = FormatInfo(
            formatId = "22",
            extension = "mp4",
            resolution = "720p",
            height = 720,
            hasVideo = true,
            hasAudio = true,
            filesize = 52428800L // 50 MB
        )
        assertTrue(videoFormat.displayTitle.contains("720p"))
        assertTrue(videoFormat.displayTitle.contains("MP4"))
        assertTrue(videoFormat.displaySubtitle.contains("50.0 MB"))
        assertTrue(videoFormat.isVideoAndAudio)

        val audioFormat = FormatInfo(
            formatId = "251",
            extension = "opus",
            resolution = "Audio",
            bitrate = 160.0,
            hasVideo = false,
            hasAudio = true
        )
        assertTrue(audioFormat.displayTitle.contains("Audio"))
        assertTrue(audioFormat.displayTitle.contains("OPUS"))
        assertTrue(audioFormat.displaySubtitle.contains("160 kbps"))
        assertTrue(audioFormat.isAudioOnly)
    }

    @Test
    fun `smart quality presets select expected formats`() {
        val formats = listOf(
            FormatInfo(
                formatId = "18",
                extension = "mp4",
                resolution = "360p",
                height = 360,
                width = 640,
                hasVideo = true,
                hasAudio = true,
                bitrate = 500.0
            ),
            FormatInfo(
                formatId = "22",
                extension = "mp4",
                resolution = "720p",
                height = 720,
                width = 1280,
                hasVideo = true,
                hasAudio = true,
                bitrate = 1500.0
            ),
            FormatInfo(
                formatId = "137",
                extension = "mp4",
                resolution = "1080p",
                height = 1080,
                width = 1920,
                hasVideo = true,
                hasAudio = false,
                bitrate = 4000.0
            ),
            FormatInfo(
                formatId = "140",
                extension = "m4a",
                resolution = "Audio",
                hasVideo = false,
                hasAudio = true,
                bitrate = 128.0
            )
        )

        // Best quality should prefer combined video+audio with highest height/bitrate
        val best = provider.getBestQuality(formats)
        assertNotNull(best)
        assertEquals("22", best?.formatId)

        // Best video overall should select 1080p
        val bestVideo = provider.getBestVideo(formats)
        assertNotNull(bestVideo)
        assertEquals("137", bestVideo?.formatId)

        // Best audio should select 140
        val bestAudio = provider.getBestAudio(formats)
        assertNotNull(bestAudio)
        assertEquals("140", bestAudio?.formatId)

        // Find by height 720
        val p720 = provider.findByHeight(formats, 720)
        assertNotNull(p720)
        assertEquals("22", p720?.formatId)
    }
}
