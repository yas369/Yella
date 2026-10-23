package com.smsledger.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MeterSegmentsTest {

    private val gap = 0.02f

    private fun seg(start: Float, end: Float, active: Boolean) = MeterSegment(start, end, active)

    private fun assertSegments(expected: List<MeterSegment>, actual: List<MeterSegment>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertEquals(e.active, a.active)
            assertEquals(e.start, a.start, 1e-4f)
            assertEquals(e.end, a.end, 1e-4f)
        }
    }

    @Test
    fun `empty meter is all track, split by the 80 percent gap`() {
        assertSegments(
            listOf(seg(0f, 0.79f, false), seg(0.81f, 1f, false)),
            meterSegments(progress = 0f, notch = 0.8f, gap = gap),
        )
    }

    @Test
    fun `below the notch the fill is one piece with a gap before the track`() {
        assertSegments(
            listOf(seg(0f, 0.5f, true), seg(0.52f, 0.79f, false), seg(0.81f, 1f, false)),
            meterSegments(progress = 0.5f, notch = 0.8f, gap = gap),
        )
    }

    @Test
    fun `past the notch the fill is split by the 80 percent gap`() {
        assertSegments(
            listOf(seg(0f, 0.79f, true), seg(0.81f, 0.9f, true), seg(0.92f, 1f, false)),
            meterSegments(progress = 0.9f, notch = 0.8f, gap = gap),
        )
    }

    @Test
    fun `a full meter has no track left`() {
        assertSegments(
            listOf(seg(0f, 0.79f, true), seg(0.81f, 1f, true)),
            meterSegments(progress = 1f, notch = 0.8f, gap = gap),
        )
    }
}
