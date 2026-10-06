package com.shilapi.xcertplay

/**
 * The night-ON lux threshold, aligned with how auto headlamps work: night below this value
 * (VW/Audi-style examples sit around 400 lux), day only above [DAY_FACTOR] times it (about
 * 1000 lux) - the hysteresis band in between keeps the current state so dusk and tunnel
 * edges do not flap.
 */
data class AmbientLightThreshold(val lux: Int = DEFAULT_LUX) {
    init {
        require(isValid(lux)) { "Lux threshold is outside the supported range" }
    }

    val dayLux: Float get() = lux * DAY_FACTOR

    companion object {
        const val DEFAULT_LUX = 400
        const val DAY_FACTOR = 2.5f
        const val MIN_LUX = 1
        const val MAX_LUX = 200_000
        private const val LEGACY_DEFAULT_LUX = 30

        fun isValid(lux: Int): Boolean = lux in MIN_LUX..MAX_LUX

        fun fromStored(lux: Int): AmbientLightThreshold {
            // The old single-threshold default reads far below any OEM headlamp trigger and
            // left lit garages classified as day; treat it as unset rather than a choice.
            if (lux == LEGACY_DEFAULT_LUX) return AmbientLightThreshold()
            return if (isValid(lux)) AmbientLightThreshold(lux) else AmbientLightThreshold()
        }
    }
}
