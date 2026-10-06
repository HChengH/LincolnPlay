package com.shilapi.xcertplay

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Day/night from the sunrise equation (geometric sun at -0.833° zenith): the time-based
 * prior for CarPlay night mode. This board's uiMode is stuck and the DayNightStatus
 * broadcast is edge-triggered, so the state at connect comes from here; sensor and dialect
 * transitions refine it afterwards. Without a location it falls back to a fixed 06:00-19:00
 * night window, like Amap's TwilightManager fallback.
 */
internal object Twilight {
    private const val DAY_MILLIS = 86_400_000.0
    private const val UNIX_TO_JULIAN = 2_440_587.5
    private const val FALLBACK_NIGHT_START_HOUR = 19
    private const val FALLBACK_NIGHT_END_HOUR = 6

    fun isNight(
        nowMillis: Long,
        latitude: Double?,
        longitude: Double?,
        zone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        if (latitude == null || longitude == null ||
            !latitude.isFinite() || !longitude.isFinite() || abs(latitude) > 80.0
        ) {
            return fallbackIsNight(nowMillis, zone)
        }
        val julianNow = nowMillis / DAY_MILLIS + UNIX_TO_JULIAN
        val days = ceil(julianNow - 2_451_545.0 + 0.0008 - longitude / 360.0)
        val starDate = days - longitude / 360.0
        val meanAnomaly = mod360(357.5291 + 0.98560028 * starDate)
        val center = 1.9148 * sinDegrees(meanAnomaly) +
            0.02 * sinDegrees(2 * meanAnomaly) +
            0.0003 * sinDegrees(3 * meanAnomaly)
        val eclipticLongitude = mod360(meanAnomaly + center + 180.0 + 102.9372)
        val transitJulian = 2_451_545.0 + starDate + 0.0053 * sinDegrees(meanAnomaly) -
            0.0069 * sinDegrees(2 * eclipticLongitude)
        val declination = asin(sinDegrees(eclipticLongitude) * sinDegrees(23.44))
        val cosHourAngle =
            (sinDegrees(-0.833) - sin(Math.toRadians(latitude)) * sin(declination)) /
                (cos(Math.toRadians(latitude)) * cos(declination))
        if (cosHourAngle < -1.0 || cosHourAngle > 1.0) return fallbackIsNight(nowMillis, zone)
        val hourAngle = Math.toDegrees(acos(cosHourAngle))
        val riseMillis = ((transitJulian - hourAngle / 360.0 - UNIX_TO_JULIAN) * DAY_MILLIS).toLong()
        val setMillis = ((transitJulian + hourAngle / 360.0 - UNIX_TO_JULIAN) * DAY_MILLIS).toLong()
        return nowMillis < riseMillis || nowMillis >= setMillis
    }

    private fun fallbackIsNight(nowMillis: Long, zone: TimeZone): Boolean {
        val hour = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
        return hour >= FALLBACK_NIGHT_START_HOUR || hour < FALLBACK_NIGHT_END_HOUR
    }

    private fun sinDegrees(degrees: Double): Double = sin(Math.toRadians(degrees))

    private fun mod360(value: Double): Double {
        val wrapped = value % 360.0
        return if (wrapped < 0) wrapped + 360.0 else wrapped
    }
}
