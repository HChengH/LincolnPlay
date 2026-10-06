package com.shilapi.xcertplay.diag

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.DiagnosticSnifferHook

/**
 * Debug-build sniffer for the head unit's day/night (headlamp) signals. AmapAuto carries a
 * repository of vendor dialects (SetLampStatusFuncRepository in its decompile); this listens
 * to all of them plus the car_mode settings keys so one headlight toggle reveals which one
 * this board actually speaks. Whichever fires becomes DiPlay's "follow head unit" source.
 */
object DayNightSignalSniffer {
    private const val TAG = "DiPlay-DayNightSniff"
    private val actions = arrayOf(
        "FLY.ANDROID.NAVI.MSG.SENDER",
        "adayo_navi_lamplet_changed_action",
        "gaei.action.DAY_NIGHT_ACTION",
        "com.neusoft.action.carmodechange",
        "com.incall.action.carmodechange",
    )
    private val settingKeys = arrayOf("car_mode", "car_lamplet")

    @JvmStatic
    fun start(context: Context) {
        synchronized(this) {
            if (started) return
            started = true
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                describe(intent).let { line ->
                    Log.i(TAG, line)
                    DiagnosticSnifferHook.post(line)
                }
            }
        }
        val filter = IntentFilter().apply { actions.forEach { addAction(it) } }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        val main = Handler(Looper.getMainLooper())
        settingKeys.forEach { key ->
            val observer = object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) {
                    val value = runCatching { Settings.System.getInt(context.contentResolver, key) }.getOrElse { -1 }
                    val line = "DayNight sniffer: settings $key=$value"
                    Log.i(TAG, line)
                    DiagnosticSnifferHook.post(line)
                }
            }
            runCatching {
                context.contentResolver.registerContentObserver(Settings.System.getUriFor(key), false, observer)
            }
            val initial = runCatching { Settings.System.getInt(context.contentResolver, key) }.getOrElse { -1 }
            DiagnosticSnifferHook.post("DayNight sniffer: settings $key initial=$initial")
        }
        Log.i(TAG, "sniffer started for ${actions.joinToString()}")
        DiagnosticSnifferHook.post("DayNight sniffer started (${actions.joinToString()})")
    }

    private fun describe(intent: Intent): String {
        val extras = intent.extras ?: return "DayNight sniffer: ${intent.action} (no extras)"
        val pairs = StringBuilder()
        for (key in extras.keySet()) {
            if (pairs.isNotEmpty()) pairs.append(' ')
            val value = runCatching { extras.get(key) }.getOrNull()
            pairs.append(key).append('=').append(value?.toString()?.take(48) ?: "null")
        }
        return "DayNight sniffer: ${intent.action} $pairs"
    }

    @Volatile
    private var started = false
}
