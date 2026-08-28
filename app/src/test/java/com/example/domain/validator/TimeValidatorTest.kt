package com.example.domain.validator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeValidatorTest {

    @Test
    fun `parseTimeToSeconds parses HH MM SS format correctly`() {
        assertEquals(3665, TimeValidator.parseTimeToSeconds("01:01:05"))
        assertEquals(90, TimeValidator.parseTimeToSeconds("00:01:30"))
        assertEquals(0, TimeValidator.parseTimeToSeconds("00:00:00"))
    }

    @Test
    fun `parseTimeToSeconds parses MM SS format correctly`() {
        assertEquals(90, TimeValidator.parseTimeToSeconds("01:30"))
        assertEquals(45, TimeValidator.parseTimeToSeconds("00:45"))
    }

    @Test
    fun `parseTimeToSeconds parses raw seconds correctly`() {
        assertEquals(120, TimeValidator.parseTimeToSeconds("120"))
        assertEquals(0, TimeValidator.parseTimeToSeconds("0"))
    }

    @Test
    fun `parseTimeToSeconds returns null for invalid formats`() {
        assertNull(TimeValidator.parseTimeToSeconds("invalid"))
        assertNull(TimeValidator.parseTimeToSeconds("01:65")) // invalid minute/sec
        assertNull(TimeValidator.parseTimeToSeconds("-10"))
    }

    @Test
    fun `formatSecondsToTimestamp formats properly`() {
        assertEquals("00:00:00", TimeValidator.formatSecondsToTimestamp(0))
        assertEquals("00:01:30", TimeValidator.formatSecondsToTimestamp(90))
        assertEquals("01:02:03", TimeValidator.formatSecondsToTimestamp(3723))
    }

    @Test
    fun `validate succeeds when start time is less than end time`() {
        val result = TimeValidator.validate("00:00:00", "00:01:30", 300)
        assertTrue(result is TimeValidationResult.Success)
        val success = result as TimeValidationResult.Success
        assertEquals(0, success.startSeconds)
        assertEquals(90, success.endSeconds)
        assertEquals("00:00:00", success.formattedStart)
        assertEquals("00:01:30", success.formattedEnd)
    }

    @Test
    fun `validate fails when start time is greater than or equal to end time`() {
        val result = TimeValidator.validate("00:02:00", "00:01:00", 300)
        assertTrue(result is TimeValidationResult.Error)
    }

    @Test
    fun `validate fails when start time exceeds video duration`() {
        val result = TimeValidator.validate("00:05:01", "00:06:00", 300)
        assertTrue(result is TimeValidationResult.Error)
    }
}
