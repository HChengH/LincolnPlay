package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Arms the CarPlay host after boot when the user has enabled the startup option.
 *
 * Two pieces, both invisible:
 * - the foreground service pins the process with a "waiting for iPhone" notification;
 * - the host activity launches silently (parked behind the launcher before its
 *   window is added, no starting window) so permissions, VPN consent, the bootstrap
 *   and the USB controller are all ready the moment the cable arrives.
 *
 * If the activity start fails, nothing is lost: the USB filter cold-launches it on
 * plug, silent as well. The DiPlay settings screen is never opened by the boot path.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val launchEnabled = AirPlayPersistence.loadAutoStartOnBoot(context)
        StartupDiagnosticSnapshot.received(context, launchEnabled)
        if (!launchEnabled) return

        val start = Intent(context, DiPlaySessionService::class.java)
            .setAction(DiPlaySessionService.ACTION_BOOT_WAIT)
        try {
            context.startForegroundService(start)
            StartupDiagnosticSnapshot.launchResult(context)
        } catch (error: RuntimeException) {
            StartupDiagnosticSnapshot.launchResult(context, error)
            Log.w(TAG, "Boot auto-start could not start DiPlaySessionService", error)
        }

        val launch = Intent(context, CarPlayHostActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("com.shilapi.xcertplay.EXTRA_SILENT_CONNECT", true)
        }
        try {
            context.startActivity(launch)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Boot auto-start could not launch CarPlayHostActivity", error)
        }
    }

    private companion object {
        const val TAG = "xcertplay-boot"
    }
}
