package com.shilapi.xcertplay

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class StartupDiagnosticSnapshotTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        app.getSharedPreferences("diplay_startup_diagnostics", Context.MODE_PRIVATE).edit().clear().commit()
        AirPlayPersistence.saveAutoStartOnBoot(app, false)
    }

    @Test fun disabledBootIsRecordedWithoutLaunchingAnythingOrChangingTheSetting() {
        var launched = false
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { launched = true }
            override fun startForegroundService(service: Intent): android.content.ComponentName? { launched = true; return null }
        }
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(launched)
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(app))
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("launchEnabledAtBoot=false launchResult=disabled"))
    }

    @Test fun bootWithToggleOnRecordsDeliveryButLaunchesNothing() {
        // Pre-warm was removed: a boot host made the first plug a warm attach that bounced
        // the task to the front and back before CarPlay. The first plug must stay a cold
        // silent launch, so boot never starts an activity or service.
        AirPlayPersistence.saveAutoStartOnBoot(app, true)
        var launched = false
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { launched = true }
            override fun startForegroundService(service: Intent): android.content.ComponentName? { launched = true; return null }
        }
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(launched)
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("launchEnabledAtBoot=true"))
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("launchResult=skipped-pre-warm-removed"))
    }

    @Test fun unrelatedBroadcastsDoNotCreateOrOverwriteBootEvidence() {
        BootReceiver().onReceive(app, Intent("custom.intent.token=secret"))
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("received=false"))
    }
}
