package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], manifest = Config.NONE)
class AmapAutoNavigationBridgeTest {
    @Test
    fun `guidance intent carries standard amap extras`() {
        val frame = BydClusterFrame.from(BydAppleManeuver(250, 1, 0, "Main Street"))
        val intent = AmapAutoNavigationBridge.guidanceIntent(frame)
        assertEquals(AmapAutoNavigationBridge.ACTION, intent.action)
        assertNull(intent.`package`)
        assertEquals("AUTONAVI_STANDARD_BROADCAST_SEND", intent.action)
        assertEquals(10001, intent.getIntExtra("KEY_TYPE", -1))
        assertEquals(2, intent.getIntExtra("NEW_ICON", -1)) // Apple type 1 -> Amap LEFT
        assertEquals(250, intent.getIntExtra("SEG_REMAIN_DIS", -1))
        assertEquals("Main Street", intent.getStringExtra("NEXT_ROAD_NAME"))
        assertEquals(0, intent.getIntExtra("ROUNG_ABOUT_NUM", -1))
        assertFalse(intent.hasExtra("IS_BYD_MAP"))
    }

    @Test
    fun `roundabout frame carries exit number`() {
        val frame = BydClusterFrame.from(BydAppleManeuver(120, 29, 0))
        val intent = AmapAutoNavigationBridge.guidanceIntent(frame)
        assertEquals(11, intent.getIntExtra("NEW_ICON", -1))
        assertEquals(2, intent.getIntExtra("ROUNG_ABOUT_NUM", -1))
    }

    @Test
    fun `end intent announces ended state`() {
        val intent = AmapAutoNavigationBridge.endIntent()
        assertEquals(10019, intent.getIntExtra("KEY_TYPE", -1))
        assertEquals(9, intent.getIntExtra("EXTRA_STATE", -1))
        assertEquals(-1, intent.getIntExtra("NEW_ICON", 0))
        assertEquals("", intent.getStringExtra("NEXT_ROAD_NAME"))
    }
}
