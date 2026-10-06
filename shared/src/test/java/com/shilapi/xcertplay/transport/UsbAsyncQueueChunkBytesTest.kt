package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android 8.x throws (rather than returning false) from UsbRequest.queue(ByteBuffer) above
 *  16384 remaining bytes; the buffer must shrink before it ever reaches queue(). Regression
 *  guard for the 0.2.13 merge that silently dropped this clamp and killed wired bring-up on
 *  the API 27 head unit with "USBMUX read failed". */
@RunWith(RobolectricTestRunner::class)
class UsbAsyncQueueChunkBytesTest {
    @Test
    @Config(sdk = [27], manifest = Config.NONE)
    fun api27CapsEveryAsyncChunkAtSixteenKilobytes() {
        assertEquals(16_384, usbAsyncQueueChunkBytes(65_536))
        assertEquals(16_384, usbAsyncQueueChunkBytes(32_768))
        assertEquals(16_384, usbAsyncQueueChunkBytes(16_384))
    }

    @Test
    @Config(sdk = [28], manifest = Config.NONE)
    fun api28AndUpKeepThePreferredChunk() {
        assertEquals(65_536, usbAsyncQueueChunkBytes(65_536))
        assertEquals(32_768, usbAsyncQueueChunkBytes(32_768))
    }
}
