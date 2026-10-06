package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterpolatedGainTest {
    @Test
    fun endpointsAreExactAndMonotonic() {
        assertEquals(0.12f, interpolatedGain(0.12f, 1f, 0f), 1e-6f)
        assertEquals(1f, interpolatedGain(0.12f, 1f, 1f), 1e-6f)
        assertEquals(0.12f, interpolatedGain(1f, 0.12f, 1f), 1e-6f)
        var previous = interpolatedGain(1f, 0.12f, 0f)
        for (step in 1..20) {
            val value = interpolatedGain(1f, 0.12f, step / 20f)
            assertTrue(value < previous)
            previous = value
        }
    }

    @Test
    fun followsTheSmoothstepCurve() {
        // 3t^2 - 2t^3: the quarter point sits below linear, the midpoint lands exactly on
        // it, and the curve is symmetric.
        assertEquals(0.15625f, 3 * 0.25f * 0.25f - 2 * 0.25f * 0.25f * 0.25f, 1e-7f)
        val span = 1f - 0.12f
        assertEquals(0.12f + span * 0.15625f, interpolatedGain(0.12f, 1f, 0.25f), 1e-6f)
        assertEquals(0.12f + span * 0.5f, interpolatedGain(0.12f, 1f, 0.5f), 1e-6f)
        assertEquals(0.12f + span * 0.84375f, interpolatedGain(0.12f, 1f, 0.75f), 1e-6f)
    }

    @Test
    fun startsAndEndsWithZeroSlope() {
        // The ease-in/out property: neighboring samples at the ends barely differ.
        val span = 1f - 0.12f
        val first = interpolatedGain(0.12f, 1f, 0.01f) - 0.12f
        val linearFirst = span * 0.01f
        assertTrue(first < linearFirst / 5f)
        val last = 1f - interpolatedGain(0.12f, 1f, 0.99f)
        val linearLast = span * 0.01f
        assertTrue(last < linearLast / 5f)
    }
}
