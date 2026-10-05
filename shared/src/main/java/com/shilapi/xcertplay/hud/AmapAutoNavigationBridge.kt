package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.Intent
import android.util.Log
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Publishes CarPlay arrows and distance as standard AmapAuto navigation broadcasts
 * (AUTONAVI_STANDARD_SEND_RECV) for aftermarket decoder boxes that mirror the factory
 * instrument cluster and HUD. The extra names match the public AmapAuto protocol that
 * head-unit vendors' receivers parse; keep them centralized so a captured trace can
 * correct any variant detail in one place.
 */
internal object AmapAutoNavigationBridge {
    private const val TAG = "DiPlay-AmapAuto"
    // This board's decoder listens for the BYD variant action (captured from its own AmapAuto
    // on the car: KEY_TYPE 13022 pings and 10019/EXTRA_STATE=40 idle keepalives), not the
    // public AUTONAVI_STANDARD_SEND_RECV spelling.
    const val ACTION = "AUTONAVI_STANDARD_BROADCAST_SEND"
    private const val KEY_GUIDANCE = 10001
    private const val KEY_STATE = 10019
    private const val STATE_ENDED = 9
    private const val KEEPALIVE_TICKS = 5
    private const val FLAG_RECEIVER_INCLUDE_BACKGROUND = 0x01000000 // hidden Intent flag, as sent by stock clients

    private val lock = Any()
    private val route = BydHudRouteState()
    private var context: Context? = null
    private var senderStarted = false
    private var lastSent: BydClusterFrame? = null
    private var ticksSinceSend = 0
    private var guidanceLogged = false

    fun initialize(appContext: Context) = synchronized(lock) {
        if (context != null) return@synchronized
        context = appContext.applicationContext
        Log.i(TAG, "manifest receivers for $ACTION: ${describeReceivers(appContext)}")
        if (!senderStarted) {
            senderStarted = true
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "diplay-amap-auto").apply { isDaemon = true }
            }.scheduleAtFixedRate(::tick, 1, 1, TimeUnit.SECONDS)
        }
    }

    fun onFrame(frame: Iap2Frame) = synchronized(lock) {
        when (route.accept(frame.messageId, frame.payload)) {
            BydHudRouteChange.GUIDANCE -> sendCurrentLocked(force = false)
            BydHudRouteChange.CLEAR -> if (lastSent != null) sendEndLocked()
            BydHudRouteChange.NONE -> Unit
        }
    }

    fun clear() = synchronized(lock) {
        route.clear() // Prevent the keepalive from restoring guidance after cleanup.
        if (lastSent != null) sendEndLocked()
    }

    private fun tick() = synchronized(lock) {
        // Guidance can expire without a frame (a list that stays empty), so check every second.
        if (lastSent != null && route.currentApple() == null) sendEndLocked()
        else if (++ticksSinceSend >= KEEPALIVE_TICKS) sendCurrentLocked(force = true)
    }

    private fun sendCurrentLocked(force: Boolean) {
        val appContext = context ?: return
        if (!AmapOutputSettings.enabled(appContext)) {
            if (lastSent != null) sendEndLocked()
            return
        }
        val apple = route.currentApple()
        val frame = apple?.let(BydClusterFrame::from)
        if (frame == null) {
            if (lastSent != null) sendEndLocked()
            return
        }
        if (!force && frame == lastSent) return
        if (broadcastLocked(appContext, guidanceIntent(frame))) {
            // Every distinct frame lands in the exportable log next to the sniffer's ground
            // truth and the raw 0x5201/0x5202 dumps, so a wrong arrow on the cluster is
            // attributable end to end.
            com.shilapi.xcertplay.DiagnosticSnifferHook.post(
                "Amap out: appleType=${apple.type} side=${apple.drivingSide} -> $frame",
            )
            lastSent = frame
            ticksSinceSend = 0
            if (!guidanceLogged) {
                guidanceLogged = true
                Log.i(TAG, "guidance sent $frame")
            }
        }
    }

    private fun sendEndLocked() {
        val appContext = context ?: return
        if (!broadcastLocked(appContext, endIntent())) return
        lastSent = null
        guidanceLogged = false
        com.shilapi.xcertplay.DiagnosticSnifferHook.post("Amap out: guidance ended")
        Log.i(TAG, "guidance ended")
    }

    fun guidanceIntent(frame: BydClusterFrame): Intent = baseIntent(KEY_GUIDANCE).apply {
        putExtra("TYPE", 0)
        putExtra("EXTRA_STATE", 0)
        putExtra("EXTRA_IS_FOREGROUND", 0)
        putExtra("NEW_ICON", frame.icon)
        // AmapAuto sends both the new and legacy icon spellings; a fixed-format cluster
        // widget may read either (ours rendered distance but not the arrow until both went out).
        putExtra("ICON", frame.icon)
        putExtra("addIcon", 0)
        putExtra("ROUNG_ABOUT_NUM", frame.roundaboutExit)
        putExtra("SEG_REMAIN_DIS", frame.distanceMeters)
        putExtra("SEG_REMAIN_DIS_AUTO", autoDistance(frame.distanceMeters))
        putExtra("SEG_REMAIN_TIME", -1)
        putExtra("CUR_ROAD_NAME", frame.road)
        putExtra("NEXT_ROAD_NAME", frame.road)
        putExtra("NEXT_NEXT_ROAD_NAME", "")
        putExtra("ROUTE_REMAIN_DIS", frame.routeRemainingMeters)
        putExtra("ROUTE_REMAIN_TIME", frame.routeRemainingSeconds)
    }

    /** AmapAuto's AUTO formatting: "123,m" below a kilometre, "1.5,km" above. */
    private fun autoDistance(meters: Int): String =
        if (meters in 0..999) "$meters,m" else {
            val km = meters / 1000.0
            if (km >= 10) "${km.toInt()},km" else String.format(Locale.US, "%.1f,km", km)
        }

    fun endIntent(): Intent = baseIntent(KEY_STATE).apply {
        putExtra("EXTRA_STATE", STATE_ENDED)
        putExtra("EXTRA_IS_FOREGROUND", 1)
        putExtra("NEW_ICON", -1)
        putExtra("ICON", -1)
        putExtra("SEG_REMAIN_DIS", -1)
        putExtra("SEG_REMAIN_DIS_AUTO", "")
        putExtra("CUR_ROAD_NAME", "")
        putExtra("NEXT_ROAD_NAME", "")
        putExtra("ROUTE_REMAIN_DIS", -1)
        putExtra("ROUTE_REMAIN_TIME", -1)
    }

    // Implicit on purpose: the decoder component's package is unknown until probed on the car.
    private fun baseIntent(keyType: Int) = Intent(ACTION).apply {
        addFlags(FLAG_RECEIVER_INCLUDE_BACKGROUND)
        putExtra("KEY_TYPE", keyType)
    }

    private fun broadcastLocked(appContext: Context, intent: Intent): Boolean = try {
        appContext.sendBroadcast(intent)
        true
    } catch (error: RuntimeException) {
        Log.w(TAG, "broadcast failed", error)
        false
    }

    private fun describeReceivers(appContext: Context): String = try {
        val receivers = appContext.packageManager.queryBroadcastReceivers(Intent(ACTION), 0)
        if (receivers.isEmpty()) "none declared" else receivers.joinToString { it.activityInfo.packageName }
    } catch (error: RuntimeException) {
        "query failed: ${error.message}"
    }
}
