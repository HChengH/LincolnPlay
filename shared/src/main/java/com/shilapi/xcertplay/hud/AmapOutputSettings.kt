package com.shilapi.xcertplay.hud

import android.content.Context

/** One user switch for the AmapAuto-compatible navigation output. */
object AmapOutputSettings {
    private const val PREFS = "diplay_amap_output"
    private const val KEY_ENABLED = "navigation_enabled"

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
