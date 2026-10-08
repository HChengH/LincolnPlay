package com.shilapi.xcertplay

import android.os.SystemClock
import java.util.LinkedHashMap

/**
 * Per-attempt connect-stage timeline, derived from the log stream the host already sees.
 * Reset at each bring-up attempt; one summary line when the AirPlay session activates (or
 * at teardown of a failed attempt, via [summaryIfStalled]). All times are ms since the
 * attempt's USB discovery, so phone-side stalls (MFi challenge, start-session -> AirPlay
 * TCP) show up as the gap between adjacent stages.
 */
internal object ConnectTimeline {
    private val stages = LinkedHashMap<String, Long>()
    private var startedAt = 0L
    private var attempt = -1

    private val markers = listOf(
        "usb" to "wired iPhone discovered",
        "datapaths" to "opening iPhone USB data paths",
        "pairDone" to "Lockdown pairing elapsedMs=",
        "carkit" to "com.apple.carkit.service stream opened",
        "net" to "NCM/VPN AirPlay transport attached",
        "control" to "wired iAP2 runtime control starting",
        "ident" to "wired iap2 identification accepted",
        "mfi" to "wired iap2 mfi rx=0xaa05",
        "startSession" to "carplay-start-session",
        "tcpAccept" to "airplay TCP accepted",
    )

    /** Observes one log line; returns a summary to emit when the session activates. */
    @Synchronized
    fun observe(line: String): String? {
        val idle = Regex("CONNECTION_DIAGNOSTIC attempt=(\\d+) run=0 phase=IDLE").find(line)
        if (idle != null) {
            val n = idle.groupValues[1].toInt()
            if (n != attempt) {
                attempt = n
                stages.clear()
                startedAt = 0L
            }
            return null
        }
        for ((name, marker) in markers) {
            if (marker in line && name !in stages) {
                if (startedAt == 0L) startedAt = SystemClock.elapsedRealtime()
                stages[name] = SystemClock.elapsedRealtime() - startedAt
                break
            }
        }
        if ("AirPlay session active" in line && startedAt > 0L) {
            return summary()
        }
        return null
    }

    /** For failed attempts: whatever was reached, or null if nothing was recorded. */
    @Synchronized
    fun summaryIfStalled(): String? =
        if (startedAt > 0L && "tcpAccept" !in stages) summary() else null

    private fun summary(): String {
        val parts = stages.entries.joinToString(" ") { (name, at) -> "$name=${at}ms" }
        val gaps = stages.entries.zipWithNext().joinToString(" ") { (a, b) ->
            "${a.key}>${b.key}=${b.value - a.value}ms"
        }
        return "ConnectTimeline attempt=$attempt $parts | gaps $gaps"
    }
}
