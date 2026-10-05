package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.airplay.AirPlayContact
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Coalesces transient (move-only) touch updates onto [executor].
 *
 * A slow event-socket burst must not replay stale intermediate finger positions as
 * rubber-banding; a move only needs the latest position. Boundaries (down/up/cancel)
 * bypass this class and queue directly on the same executor, and FIFO ordering keeps
 * them after every coalesced move that preceded them.
 */
internal class TransientTouchCoalescer(
    private val executor: Executor,
    private val send: (List<AirPlayContact>) -> Unit,
) {
    private val pending = AtomicReference<List<AirPlayContact>?>()
    private val drainScheduled = AtomicBoolean(false)

    fun offer(contacts: List<AirPlayContact>) {
        pending.set(contacts)
        // The drain clears its ownership before taking the slot: a set() that lands
        // after the take either arms a fresh drain or is picked up by one already
        // queued, so no stash is ever left behind.
        if (drainScheduled.compareAndSet(false, true)) {
            executor.execute {
                drainScheduled.set(false)
                pending.getAndSet(null)?.let(send)
            }
        }
    }
}
