package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.airplay.AirPlayContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class TransientTouchCoalescerTest {

    private fun move(id: Int) = listOf(AirPlayContact(id, id.toDouble(), 0.0, down = true))
    private val boundary = listOf(AirPlayContact(0, 0.0, 0.0, down = false))

    @Test
    fun latestMoveWinsWhileDrainIsPending() {
        val tasks = ArrayDeque<Runnable>()
        val sent = mutableListOf<List<AirPlayContact>>()
        val coalescer = TransientTouchCoalescer(Executor { tasks.add(it) }) { sent.add(it) }

        coalescer.offer(move(1))
        coalescer.offer(move(2))
        coalescer.offer(move(3))

        assertEquals(1, tasks.size)
        tasks.poll().run()
        assertEquals(listOf(move(3)), sent)
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun boundaryQueuedAfterAMoveRunsAfterItsDrain() {
        val tasks = ArrayDeque<Runnable>()
        val sent = mutableListOf<List<AirPlayContact>>()
        val coalescer = TransientTouchCoalescer(Executor { tasks.add(it) }) { sent.add(it) }

        coalescer.offer(move(1))
        tasks.add { sent.add(boundary) } // a direct (non-transient) send on the same executor

        while (tasks.isNotEmpty()) tasks.poll().run()
        assertEquals(listOf(move(1), boundary), sent)
    }

    @Test
    fun reArmsAfterADrain() {
        val sent = mutableListOf<List<AirPlayContact>>()
        val coalescer = TransientTouchCoalescer(Executor { it.run() }) { sent.add(it) }

        coalescer.offer(move(1))
        coalescer.offer(move(2))

        assertEquals(listOf(move(1), move(2)), sent)
    }

    @Test
    fun concurrentOffersNeverLoseTheFinalMarker() {
        val executor = Executors.newSingleThreadExecutor()
        val sent = Collections.synchronizedList(mutableListOf<List<AirPlayContact>>())
        val coalescer = TransientTouchCoalescer(executor) { sent.add(it) }

        val workers = (0 until 4).map { worker ->
            Thread {
                repeat(1_000) { index -> coalescer.offer(move(worker * 100_000 + index)) }
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }

        val final = listOf(AirPlayContact(-1, -1.0, -1.0, down = true))
        coalescer.offer(final)
        executor.submit { }.get() // barrier: everything queued before this has run
        executor.shutdown()

        assertTrue(sent.isNotEmpty())
        // No lost wakeup: whatever drained, the last delivered move is the final marker.
        assertEquals(final, sent.last())
    }
}
