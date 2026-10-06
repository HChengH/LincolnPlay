package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientLightThresholdTest {
    @Test fun acceptsThresholdsWithinTheSupportedRange() {
        for (lux in listOf(1, 50, 200_000)) assertTrue(AmbientLightThreshold.isValid(lux))
    }

    @Test fun rejectsOutOfRangeThresholdsAndMigratesTheLegacyDefault() {
        for (lux in listOf(-1, 0, 200_001, Int.MAX_VALUE)) {
            assertFalse(AmbientLightThreshold.isValid(lux))
            assertEquals(AmbientLightThreshold(), AmbientLightThreshold.fromStored(lux))
        }
        // The pre-headlamp 30 lux default reads as unset rather than a deliberate choice.
        assertEquals(AmbientLightThreshold(), AmbientLightThreshold.fromStored(30))
        assertEquals(AmbientLightThreshold(200), AmbientLightThreshold.fromStored(200))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidThresholdCannotReachTheControllerOrPersistence() {
        AmbientLightThreshold(0)
    }
}
