package com.shilapi.xcertplay

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class HeadLampDialectsTest {
    private val dayNightStatus = "DayNightStatus"
    private val fly = "FLY.ANDROID.NAVI.MSG.SENDER"
    private val adayo = "adayo_navi_lamplet_changed_action"
    private val gaei = "gaei.action.DAY_NIGHT_ACTION"

    @Test
    fun parsesThisBoardsDayNightStatusDialect() {
        assertTrue(HeadLampDialects.nightFrom(dayNightStatus, Intent().putExtra("data", 1))!!)
        assertFalse(HeadLampDialects.nightFrom(dayNightStatus, Intent().putExtra("data", 2))!!)
        assertTrue(HeadLampDialects.nightFrom(dayNightStatus, Intent().putExtra("data", true))!!)
        assertFalse(HeadLampDialects.nightFrom(dayNightStatus, Intent().putExtra("data", false))!!)
        assertTrue(HeadLampDialects.nightFrom(dayNightStatus, Intent().putExtra("night", true))!!)
        assertFalse(HeadLampDialects.nightFrom(dayNightStatus, Intent().putExtra("night", false))!!)
        assertNull(HeadLampDialects.nightFrom(dayNightStatus, Intent()))
    }

    @Test
    fun parsesTheFlyDialectFromAnyOfItsThreeExtras() {
        assertFalse(HeadLampDialects.nightFrom(fly, Intent(fly).putExtra("FLY_KEY_VALUE", "MODE_DAY"))!!)
        assertTrue(HeadLampDialects.nightFrom(fly, Intent(fly).putExtra("FLY_DAYNIGHT_MODE", "MODE_NIGHT"))!!)
        assertTrue(HeadLampDialects.nightFrom(fly, Intent(fly).putExtra("FLY_PM_MODE", "MODE_NIGHT"))!!)
        assertNull(HeadLampDialects.nightFrom(fly, Intent(fly)))
        assertNull(HeadLampDialects.nightFrom(fly, Intent(fly).putExtra("FLY_KEY_VALUE", "MODE_TWILIGHT")))
    }

    @Test
    fun parsesTheAdayoLampletDialect() {
        assertTrue(HeadLampDialects.nightFrom(adayo, Intent(adayo).putExtra("lamplet", 1))!!)
        assertFalse(HeadLampDialects.nightFrom(adayo, Intent(adayo).putExtra("lamplet", 0))!!)
        assertNull(HeadLampDialects.nightFrom(adayo, Intent(adayo)))
    }

    @Test
    fun parsesTheGaeiDialect() {
        assertTrue(HeadLampDialects.nightFrom(gaei, Intent(gaei).putExtra("ACTION", 0))!!)
        assertFalse(HeadLampDialects.nightFrom(gaei, Intent(gaei).putExtra("ACTION", 1))!!)
        assertNull(HeadLampDialects.nightFrom(gaei, Intent(gaei)))
    }

    @Test
    fun carmodeDialectsReadTheSettingsValue() {
        assertNull(HeadLampDialects.nightFrom("com.neusoft.action.carmodechange", Intent()))
        assertFalse(HeadLampDialects.carModeNight(0))
        assertTrue(HeadLampDialects.carModeNight(1))
        assertEquals(null, HeadLampDialects.nightFrom(null, Intent()))
    }
}
