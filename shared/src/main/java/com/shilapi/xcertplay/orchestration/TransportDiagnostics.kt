package com.shilapi.xcertplay.orchestration

/**
 * Process-wide switch for high-volume transport diagnostics (per-frame iAP2 link traces,
 * raw route-frame dumps, USBMUX frame logging). Set from the debug-logs setting at
 * controller start: these lines flood the rotating session log within minutes at
 * connection-establishment rates and only pay off during protocol debugging.
 */
internal object TransportDiagnostics {
    @Volatile var verbose: Boolean = false
}
