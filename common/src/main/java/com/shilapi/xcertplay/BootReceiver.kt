package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Boot is deliberately a no-op beyond recording delivery. The pre-warm launch made the
 * first plug a WARM attach: the system raised the parked host's task to deliver the USB
 * intent and we parked it again — a visible bounce before CarPlay appeared. Without a
 * boot host the first plug is a cold silent launch (parked in onCreate, before its
 * window is ever added), which never flashes; the pre-warm's 1-2 s saving was not worth
 * the bounce.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        StartupDiagnosticSnapshot.received(context, AirPlayPersistence.loadAutoStartOnBoot(context))
        StartupDiagnosticSnapshot.launchResult(context)
    }
}
