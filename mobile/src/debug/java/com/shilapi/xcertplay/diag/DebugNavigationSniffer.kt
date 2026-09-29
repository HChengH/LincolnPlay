package com.shilapi.xcertplay.diag

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.DiagnosticSnifferHook

/**
 * Debug-build sniffer for AmapAuto navigation broadcasts. Dynamic registration is the only
 * way to observe implicit broadcasts on Android 8+; run the AmapAuto app and navigate to
 * capture the ground-truth fields this head unit's decoder expects.
 */
object DebugNavigationSniffer {
    private const val TAG = "DiPlay-AmapSniff"
    private val actions = arrayOf(
        "AUTONAVI_STANDARD_SEND_RECV",
        "AUTONAVI_STANDARD_BROADCAST_SEND",
    )

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
        Log.i(TAG, "sniffer started for ${actions.joinToString()}")
        DiagnosticSnifferHook.post("Amap sniffer started (${actions.joinToString()})")
    }

    private var started = false

    private fun describe(intent: Intent): String {
        val extras = intent.extras ?: return "${intent.action} (no extras)"
        val pairs = extras.keySet().joinToString(", ") { key ->
            extras.get(key)?.let { "$key=$it" } ?: "$key=null"
        }
        return "${intent.action}: $pairs"
    }
}
