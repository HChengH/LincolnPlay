package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * The vendor headlamp/day-night dialects AmapAuto carries in its SetLampStatusFuncRepository.
 * Whichever dialect this head unit speaks fires here and drives CarPlay night mode directly,
 * Amap-style: last writer wins over the stale uiMode and the ambient sensor (the sensor stays
 * the fallback on units where no dialect ever fires).
 */
internal object HeadLampDialects {
    private const val FLY = "FLY.ANDROID.NAVI.MSG.SENDER"
    private const val ADAYO = "adayo_navi_lamplet_changed_action"
    private const val GAEI = "gaei.action.DAY_NIGHT_ACTION"
    private const val NEUSOFT = "com.neusoft.action.carmodechange"
    private const val INCALL = "com.incall.action.carmodechange"
    private const val CAR_MODE = "car_mode"

    /** The dialect's night value, or null when the intent carries no usable one. */
    fun nightFrom(action: String?, intent: Intent): Boolean? {
        val extras = intent.extras ?: return null
        return when (action) {
            FLY -> {
                val value = sequenceOf("FLY_KEY_VALUE", "FLY_DAYNIGHT_MODE", "FLY_PM_MODE")
                    .mapNotNull { extras.getString(it) }
                    .firstOrNull()
                when (value) {
                    "MODE_DAY" -> false
                    "MODE_NIGHT" -> true
                    else -> null
                }
            }
            // lamplet 1 = headlamps on = night.
            ADAYO -> extras.getInt("lamplet", -1).takeIf { it != -1 }?.let { it == 1 }
            // ACTION 1 = day, 0 = night.
            GAEI -> extras.getInt("ACTION", -1).takeIf { it != -1 }?.let { it == 0 }
            // The carmodechange broadcasts carry no stable extra; Amap's adapters read the
            // value from Settings.System car_mode instead (see [carModeNight]).
            else -> null
        }
    }

    fun carModeNight(value: Int): Boolean = value != 0

    @Volatile
    private var onNight: ((night: Boolean, source: String) -> Unit)? = null

    private var registered = false
    private val main = Handler(Looper.getMainLooper())

    /** Wires the current activity's listener; receivers live on the application context. */
    fun attach(context: Context, listener: (night: Boolean, source: String) -> Unit) {
        onNight = listener
        synchronized(this) {
            if (registered) return
            registered = true
        }
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val action = intent.action
                when (nightFrom(action, intent)) {
                    true -> onNight?.invoke(true, action ?: "unknown")
                    false -> onNight?.invoke(false, action ?: "unknown")
                    null -> if (action == NEUSOFT || action == INCALL) {
                        val mode = runCatching { Settings.System.getInt(app.contentResolver, CAR_MODE) }
                            .getOrNull() ?: return
                        onNight?.invoke(carModeNight(mode), "$action car_mode=$mode")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            listOf(FLY, ADAYO, GAEI, NEUSOFT, INCALL).forEach(::addAction)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        app.contentResolver.registerContentObserver(
            Settings.System.getUriFor(CAR_MODE),
            false,
            object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) {
                    val mode = runCatching { Settings.System.getInt(app.contentResolver, CAR_MODE) }
                        .getOrNull() ?: return
                    onNight?.invoke(carModeNight(mode), "settings $CAR_MODE=$mode")
                }
            },
        )
    }

    fun detach() {
        onNight = null
    }
}
