package com.shilapi.xcertplay

/**
 * Bridge for debug-only diagnostic producers such as the navigation-broadcast sniffer
 * (mobile/src/debug). Release builds have no producer, so nothing is ever posted.
 */
object DiagnosticSnifferHook {
    @Volatile var line: ((String) -> Unit)? = null

    fun post(text: String) {
        line?.invoke(text)
    }
}
