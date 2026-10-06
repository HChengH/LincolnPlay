package com.shilapi.xcertplay

import android.content.ComponentName
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

    @Test fun disabledBootIsRecordedWithoutStartingAnythingOrChangingTheSetting() {
        var started = false
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { started = true }
            override fun startForegroundService(service: Intent): ComponentName? { started = true; return null }
        }
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(started)
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(app))
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("launchEnabledAtBoot=false launchResult=disabled"))
    }

    @Test fun successfulBootArmsTheWaitingServiceAndSilentlyLaunchesTheHost() {
        AirPlayPersistence.saveAutoStartOnBoot(app, true)
        var activityLaunch: Intent? = null
        var serviceStart: Intent? = null
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { activityLaunch = intent }
            override fun startForegroundService(service: Intent): ComponentName? {
                serviceStart = service
                return ComponentName(app, DiPlaySessionService::class.java)
            }
        }
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(DiPlaySessionService::class.java.name, serviceStart!!.component!!.className)
        assertEquals(DiPlaySessionService.ACTION_BOOT_WAIT, serviceStart!!.action)
        assertEquals(CarPlayHostActivity::class.java.name, activityLaunch!!.component!!.className)
        assertTrue(activityLaunch!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(activityLaunch!!.getBooleanExtra("com.shilapi.xcertplay.EXTRA_SILENT_CONNECT", false))
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("launchResult=startForegroundService-returned"))
    }

    @Test fun launchFailureRecordsOnlyItsClassAndASecondBootReplacesOldEvidence() {
        AirPlayPersistence.saveAutoStartOnBoot(app, true)
        val context = object : ContextWrapper(app) {
            override fun startForegroundService(service: Intent): ComponentName? { throw SecurityException("Jane's phone token=private-data") }
        }
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        val failed = StartupDiagnosticSnapshot.report(app)
        assertTrue(failed.contains("launchResult=failed failureClass=SecurityException"))
        assertFalse(failed.contains("Jane")); assertFalse(failed.contains("private-data"))
        assertNotNull(DiagnosticRedactor.redact(failed))
        AirPlayPersistence.saveAutoStartOnBoot(app, false)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("launchResult=disabled failureClass=none"))
    }

    @Test fun unrelatedBroadcastsDoNotCreateOrOverwriteBootEvidence() {
        BootReceiver().onReceive(app, Intent("custom.intent.token=secret"))
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("received=false"))
    }
}
