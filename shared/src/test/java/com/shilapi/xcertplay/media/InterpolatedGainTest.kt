package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

class InterpolatedGainTest {
    @Test
    fun endpointsAreExactAndMonotonic() {
        assertEquals(0.12f, interpolatedGain(0.12f, 1f, 0f), 1e-6f)
        assertEquals(1f, interpolatedGain(0.12f, 1f, 1f), 1e-4f)
        assertEquals(0.12f, interpolatedGain(1f, 0.12f, 1f), 1e-4f)
        var previous = interpolatedGain(1f, 0.12f, 0f)
        for (step in 1..20) {
            val value = interpolatedGain(1f, 0.12f, step / 20f)
            assertTrue(value < previous)
            previous = value
        }
    }

    @Test
    fun rampsLinearlyInDecibels() {
        // The audio-domain volume-ramp standard: equal progress covers equal loudness.
        val startDb = 20 * log10(0.12)
        val endDb = 20 * log10(1.0)
        for (step in 1..10) {
            val progress = step / 10f
            val expectedDb = startDb + (endDb - startDb) * progress
            val actualDb = 20 * log10(interpolatedGain(0.12f, 1f, progress).toDouble())
            assertTrue("progress=$progress", abs(actualDb - expectedDb) < 0.01)
        }
    }

    @Test
    fun silentEndsFallBackToLinear() {
        assertEquals(0.05f, interpolatedGain(0f, 0.2f, 0.25f), 1e-6f)
        assertEquals(0.05f, interpolatedGain(0.2f, 0f, 0.75f), 1e-6f)
    }
}
