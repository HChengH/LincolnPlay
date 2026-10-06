package com.shilapi.xcertplay.diag

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.DiagnosticSnifferHook

/**
 * Debug-build wide net for the head unit's day/night state. No known dialect has fired on
 * this board, so this diffs every System/Secure/Global settings table plus configuration
 * changes; whatever key or config flips when the car changes state names itself in the log.
 */
object SettingsDiffSniffer {
    private const val TAG = "DiPlay-SettingsDiff"
    private const val INTERVAL_MILLIS = 2_000L

    @JvmStatic
    fun start(context: Context) {
        synchronized(this) {
            if (started) return
            started = true
        }
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())

        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    val line = "SettingsDiff: configuration changed uiNight=" +
                        when (night) {
                            Configuration.UI_MODE_NIGHT_YES -> "yes"
                            Configuration.UI_MODE_NIGHT_NO -> "no"
                            else -> "undefined"
                        }
                    DiagnosticSnifferHook.post(line)
                }
            },
            IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )

        var previous = snapshot(app)
        val ticker = object : Runnable {
            override fun run() {
                val current = snapshot(app)
                diff("system", previous.first, current.first)
                diff("secure", previous.second, current.second)
                diff("global", previous.third, current.third)
                previous = current
                main.postDelayed(this, INTERVAL_MILLIS)
            }
        }
        main.post(ticker)
        DiagnosticSnifferHook.post("SettingsDiff sniffer started (${previous.first.size} system keys)")
    }

    private fun snapshot(context: Context): Triple<Map<String, String>, Map<String, String>, Map<String, String>> =
        Triple(
            readTable(context, Settings.System.CONTENT_URI),
            readTable(context, Settings.Secure.CONTENT_URI),
            readTable(context, Settings.Global.CONTENT_URI),
        )

    private fun readTable(context: Context, uri: android.net.Uri): Map<String, String> {
        val values = mutableMapOf<String, String>()
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val value = cursor.getString(1) ?: continue
                    values[name] = value.take(64)
                }
            }
        }
        return values
    }

    private fun diff(label: String, before: Map<String, String>, after: Map<String, String>) {
        after.forEach { (key, value) ->
            val old = before[key]
            if (old != value) DiagnosticSnifferHook.post("SettingsDiff: $label $key: ${old ?: "<new>"} -> $value")
        }
        before.keys.filter { it !in after }.forEach { key ->
            DiagnosticSnifferHook.post("SettingsDiff: $label $key removed")
        }
    }

    @Volatile
    private var started = false
}
