package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Arms the CarPlay host after boot when the user has enabled the startup option.
 *
 * Boot only starts the foreground service: no activity is created, so nothing can
 * appear over the car's own UI. The service keeps the process warm (and says it is
 * waiting for the iPhone). When the cable arrives, the system cold-launches
 * CarPlayHostActivity through its USB filter; that launch connects silently behind
 * the launcher and surfaces only when CarPlay is ready. The DiPlay settings screen
 * is never opened by the boot path.
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
    }

    private companion object {
        const val TAG = "xcertplay-boot"
    }
}
