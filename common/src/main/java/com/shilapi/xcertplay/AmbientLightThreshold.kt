package com.shilapi.xcertplay

/**
 * The night-ON lux threshold, aligned with how auto headlamps work: night below this value
 * (VW/Audi-style examples sit around 400 lux), day only above [dayFactor] times it (800 lux
 * at the default 2x) - the hysteresis band in between keeps the current state so dusk and
 * tunnel edges do not flap. The factor is user-tunable so the dusk-crossing time can be
 * shortened (smaller band) or widened (more hold).
 */
data class AmbientLightThreshold(
    val lux: Int = DEFAULT_LUX,
    val dayFactor: Float = DEFAULT_DAY_FACTOR,
) {
    init {
        require(isValid(lux)) { "Lux threshold is outside the supported range" }
        require(isValidDayFactor(dayFactor)) { "Day factor is outside the supported range" }
    }

    val dayLux: Float get() = lux * dayFactor

    companion object {
        const val DEFAULT_LUX = 400
        const val DEFAULT_DAY_FACTOR = 2.0f
        const val MIN_LUX = 1
        const val MAX_LUX = 200_000
        const val MIN_DAY_FACTOR = 1.1f
        const val MAX_DAY_FACTOR = 4.0f
        private const val LEGACY_DEFAULT_LUX = 30

        fun isValid(lux: Int): Boolean = lux in MIN_LUX..MAX_LUX

        fun isValidDayFactor(dayFactor: Float): Boolean =
            dayFactor.isFinite() && dayFactor in MIN_DAY_FACTOR..MAX_DAY_FACTOR

        fun fromStored(lux: Int, dayFactor: Float = DEFAULT_DAY_FACTOR): AmbientLightThreshold {
            // The old single-threshold default reads far below any OEM headlamp trigger and
            // left lit garages classified as day; treat it as unset rather than a choice.
            if (lux == LEGACY_DEFAULT_LUX) return AmbientLightThreshold(dayFactor = sanitized(dayFactor))
            return if (isValid(lux)) AmbientLightThreshold(lux, sanitized(dayFactor))
            else AmbientLightThreshold(dayFactor = sanitized(dayFactor))
        }

        private fun sanitized(dayFactor: Float): Float =
            if (isValidDayFactor(dayFactor)) dayFactor else DEFAULT_DAY_FACTOR
    }
}
