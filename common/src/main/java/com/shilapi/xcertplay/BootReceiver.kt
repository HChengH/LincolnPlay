package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts the CarPlay host after boot when the user has enabled the startup option.
 *
 * The host launches silently: parked behind the launcher before its window is
 * added, no starting window, no notification — boot stays completely invisible.
 * Being alive early front-loads permissions, VPN consent, the bootstrap and the
 * USB controller, so the cable connects immediately when it arrives. The
 * foreground service (and its notification) only appears once a session is
 * actually active. The DiPlay settings screen is never opened by the boot path.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val launchEnabled = AirPlayPersistence.loadAutoStartOnBoot(context)
        StartupDiagnosticSnapshot.received(context, launchEnabled)
        if (!launchEnabled) return

        val launch = Intent(context, CarPlayHostActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("com.shilapi.xcertplay.EXTRA_SILENT_CONNECT", true)
        }
        try {
            context.startActivity(launch)
            StartupDiagnosticSnapshot.launchResult(context)
        } catch (error: RuntimeException) {
            StartupDiagnosticSnapshot.launchResult(context, error)
            Log.w(TAG, "Boot auto-start could not launch CarPlayHostActivity", error)
        }
    }

    private companion object {
        const val TAG = "xcertplay-boot"
    }
}
