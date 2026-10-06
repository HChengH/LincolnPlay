package com.shilapi.xcertplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TwilightTest {
    // Beijing: around 2026-10-06 the sun rises ~06:10 and sets ~17:40 local.
    private val beijing = TimeZone.getTimeZone("GMT+8")
    private val latitude = 39.9042
    private val longitude = 116.4074

    private fun local(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(beijing).apply {
            set(2026, Calendar.OCTOBER, 6, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun classifiesBeijingOctoberDayAndNight() {
        assertFalse(Twilight.isNight(local(7), latitude, longitude, beijing))
        assertFalse(Twilight.isNight(local(12), latitude, longitude, beijing))
        assertFalse(Twilight.isNight(local(17), latitude, longitude, beijing))
        assertTrue(Twilight.isNight(local(4), latitude, longitude, beijing))
        assertTrue(Twilight.isNight(local(19), latitude, longitude, beijing))
        assertTrue(Twilight.isNight(local(23, 30), latitude, longitude, beijing))
    }

    @Test
    fun summerEveningStaysDayLongerThanOctober() {
        val summerEvening = Calendar.getInstance(beijing).apply {
            set(2026, Calendar.JULY, 6, 19, 0, 0)
        }.timeInMillis
        assertFalse(Twilight.isNight(summerEvening, latitude, longitude, beijing))
    }

    @Test
    fun nullLocationFallsBackToFixedWindow() {
        assertFalse(Twilight.isNight(local(12), null, null, beijing))
        assertFalse(Twilight.isNight(local(18, 59), null, null, beijing))
        assertTrue(Twilight.isNight(local(19), null, null, beijing))
        assertTrue(Twilight.isNight(local(5, 59), null, null, beijing))
        assertFalse(Twilight.isNight(local(6), null, null, beijing))
    }
}
