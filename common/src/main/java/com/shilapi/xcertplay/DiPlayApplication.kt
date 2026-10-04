package com.shilapi.xcertplay

import android.app.Application

/** Installs the outermost crash net before any component runs. */
class DiPlayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
