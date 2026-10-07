package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Pre-warms the CarPlay host after boot when the user has enabled the startup option
 * (their explicit request): permissions, VPN consent, the bootstrap and the USB
 * controller are ready before the cable arrives, so the warm plug connects without the
 * cold-start latency.
 *
 * The cost, inherent to a parked host: the first plug is a warm attach — the system
 * raises the parked task to deliver the USB intent and the host parks itself again,
 * a visible bounce before CarPlay. Without a boot host the first plug is a cold silent
 * launch (parked in onCreate before its window is added) which never flashes, at
 * ~1-2 s extra connect time. The DiPlay settings screen is never opened by this path.
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
