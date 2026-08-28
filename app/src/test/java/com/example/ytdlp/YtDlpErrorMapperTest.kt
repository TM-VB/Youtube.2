package com.example.ytdlp

import com.example.domain.model.DownloadError
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpErrorMapperTest {

    @Test
    fun `maps private video error message correctly`() {
        val error = YtDlpErrorMapper.map(Exception("ERROR: [youtube] 12345: Private video. Sign in if you've been granted access to this video"))
        assertTrue(error is DownloadError.PrivateVideo)
    }

    @Test
    fun `maps sign in required error message correctly`() {
        val error = YtDlpErrorMapper.map(Exception("ERROR: Sign in to confirm you’re not a bot"))
        assertTrue(error is DownloadError.SigninRequired)
    }

    @Test
    fun `maps unavailable video message correctly`() {
        val error = YtDlpErrorMapper.map(Exception("ERROR: [youtube] abc: Video unavailable. This video has been removed by the uploader"))
        assertTrue(error is DownloadError.VideoUnavailable)
    }

    @Test
    fun `maps network error message correctly`() {
        val error = YtDlpErrorMapper.map(Exception("ERROR: Unable to download webpage: <urlopen error [Errno 101] Network is unreachable>"))
        assertTrue(error is DownloadError.NetworkError)
    }

    @Test
    fun `maps geo restricted message correctly`() {
        val error = YtDlpErrorMapper.map(Exception("ERROR: The uploader has not made this video available in your country"))
        assertTrue(error is DownloadError.GeoRestricted)
    }

    @Test
    fun `maps generic error when no pattern matches`() {
        val error = YtDlpErrorMapper.map(Exception("Unknown extractor crash occurred"))
        assertTrue(error is DownloadError.YtDlpError)
    }
}
