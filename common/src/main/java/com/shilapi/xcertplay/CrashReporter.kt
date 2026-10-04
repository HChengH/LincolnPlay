package com.shilapi.xcertplay

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Outermost crash net for car debugging. Every uncaught exception is appended to the session
 * log and shown full-screen by [CrashReportActivity], which runs in its own process so the
 * report survives the dying main process and its text can be copied straight from the car
 * screen. Swallowing main-thread exceptions instead would leave a frozen UI, so the process
 * still ends after the report is handed off.
 */
object CrashReporter {
    private const val TAG = "DiPlay-Crash"
    private const val MAX_REPORT_CHARS = 64_000
    private const val CRASH_PROCESS_SUFFIX = ":crash"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(appContext, previous))
    }

    private class Handler(
        private val appContext: Context,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            val report = describe(appContext, thread, error)
            Log.e(TAG, report)
            runCatching { appendToSessionLog(appContext, report) }
            if (!runningInCrashProcess(appContext)) {
                runCatching {
                    appContext.startActivity(
                        Intent(appContext, CrashReportActivity::class.java).apply {
                            putExtra(CrashReportActivity.EXTRA_REPORT, report.take(MAX_REPORT_CHARS))
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        },
                    )
                    // Give the system a moment to start the report activity in the crash
                    // process before this process is torn down.
                    Thread.sleep(600)
                }
            }
            previous?.uncaughtException(thread, error)
            Process.killProcess(Process.myPid())
            Runtime.getRuntime().exit(10)
        }
    }

    private fun describe(context: Context, thread: Thread, error: Throwable): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            append("===== FATAL =====\n")
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
            append("  DiPlay ").append(version).append("  Android ").append(Build.VERSION.RELEASE)
            append(" (API ").append(Build.VERSION.SDK_INT).append(")  ").append(Build.MODEL).append('\n')
            append("Thread: ").append(thread.name).append('\n')
            append(trace)
        }
    }

    private fun appendToSessionLog(context: Context, report: String) {
        val logs = File(context.filesDir, "logs")
        if (!logs.isDirectory) logs.mkdirs()
        File(logs, "diplay.log").appendText(report + "\n")
    }

    private fun runningInCrashProcess(context: Context): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            runCatching {
                context.getSystemService(ActivityManager::class.java)
                    ?.runningAppProcesses
                    ?.firstOrNull { it.pid == Process.myPid() }
                    ?.processName
            }.getOrNull()
        }
        return name?.endsWith(CRASH_PROCESS_SUFFIX) == true
    }
}
