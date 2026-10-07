package com.shilapi.xcertplay.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.io.Closeable

/**
 * Encodes 20 ms chunks of 48 kHz mono PCM into raw Opus access units for the CarPlay
 * microphone uplink.
 *
 * Two interchangeable backends: the platform MediaCodec encoder when the ROM ships one,
 * and the bundled libopus (see jni/opus) everywhere else — notably Android 8.x, whose
 * MediaCodec has no Opus encoder. Both produce one raw access unit per 20 ms frame, so
 * the packetizer is backend-agnostic.
 */
internal class OpusEncoder(bitrate: Int) : Closeable {
    private val codec: MediaCodec? = try {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_OPUS,
            SAMPLE_RATE,
            CHANNELS,
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
        }
        MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).also {
            it.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE,
            )
            it.start()
            Log.i(TAG, "Opus microphone encoder started bitrate=$bitrate backend=mediacodec")
        }
    } catch (error: Exception) {
        Log.w(TAG, "Opus microphone encoder unavailable", error)
        null
    }

    /** Zero when neither backend could be created. */
    private val nativeHandle: Long = if (codec == null) {
        runCatching {
            NativeOpusEncoder.createEncoder(
                SAMPLE_RATE,
                CHANNELS,
                NativeOpusEncoder.APPLICATION_VOIP,
                bitrate,
            )
        }.getOrElse { 0L }.also {
            if (it == 0L) Log.w(TAG, "native Opus microphone encoder unavailable")
            else Log.i(TAG, "Opus microphone encoder started bitrate=$bitrate backend=libopus")
        }
    } else 0L

    private val bufferInfo = MediaCodec.BufferInfo()
    private var presentationTimeUs = 0L
    private var closed = false
    private var outputPackets = 0

    val available: Boolean get() = !closed && (codec != null || nativeHandle != 0L)

    /**
     * Queues one 20 ms PCM frame and returns all Opus access units made available.
     */
    fun encode(pcm: ByteArray): List<ByteArray> {
        if (closed) return emptyList()
        return if (codec != null) encodeWithCodec(pcm) else encodeNatively(pcm)
    }

    private fun encodeWithCodec(pcm: ByteArray): List<ByteArray> {
        val codec = codec ?: return emptyList()
        val inputIndex = try {
            codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        } catch (error: Exception) {
            Log.w(TAG, "Opus microphone input dequeue failed", error)
            return emptyList()
        }
        if (inputIndex >= 0) {
            val input = codec.getInputBuffer(inputIndex)
            if (input == null || pcm.size > input.remaining()) {
                codec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0)
            } else {
                input.clear()
                input.put(pcm)
                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    pcm.size,
                    presentationTimeUs,
                    0,
                )
                presentationTimeUs += INPUT_DURATION_US
            }
        }
        return drain()
    }

    private fun encodeNatively(pcm: ByteArray): List<ByteArray> {
        val handle = nativeHandle
        if (handle == 0L) return emptyList()
        val output = ByteArray(MAX_PACKET_BYTES)
        val written = NativeOpusEncoder.encode(handle, pcm, pcm.size / BYTES_PER_SAMPLE, output)
        if (written < 0) {
            Log.w(TAG, "native Opus encode failed code=$written")
            return emptyList()
        }
        if (written == 0) return emptyList()
        val packet = output.copyOf(written)
        outputPackets++
        if (outputPackets <= FIRST_PACKET_LOG_COUNT) {
            Log.i(
                TAG,
                "Opus microphone packet=$outputPackets bytes=${packet.size} " +
                    "head=${packet.copyOf(minOf(packet.size, 16)).toHexString()} backend=libopus",
            )
        }
        return listOf(packet)
    }

    private fun drain(): List<ByteArray> {
        val codec = codec ?: return emptyList()
        val output = ArrayList<ByteArray>()
        while (!closed) {
            val index = try {
                codec.dequeueOutputBuffer(bufferInfo, 0)
            } catch (error: Exception) {
                Log.w(TAG, "Opus microphone output dequeue failed", error)
                return output
            }
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return output
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> continue
                index >= 0 -> {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        codec.releaseOutputBuffer(index, false)
                        continue
                    }
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && bufferInfo.size > 0) {
                        val bytes = ByteArray(bufferInfo.size)
                        buffer.position(bufferInfo.offset)
                        buffer.limit(bufferInfo.offset + bufferInfo.size)
                        buffer.get(bytes)
                        output.add(bytes)
                        outputPackets++
                        if (outputPackets <= FIRST_PACKET_LOG_COUNT) {
                            Log.i(
                                TAG,
                                "Opus microphone packet=$outputPackets bytes=${bytes.size} " +
                                    "head=${bytes.copyOf(minOf(bytes.size, 16)).toHexString()}",
                            )
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                }
            }
        }
        return output
    }

    override fun close() {
        if (closed) return
        closed = true
        val codec = codec
        if (codec != null) {
            try {
                codec.stop()
            } catch (_: Exception) {
                // Best effort.
            }
            try {
                codec.release()
            } catch (_: Exception) {
                // Best effort.
            }
        }
        val handle = nativeHandle
        if (handle != 0L) {
            runCatching { NativeOpusEncoder.destroy(handle) }
        }
    }

    private companion object {
        const val TAG = "xcertplay-usb"
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 1
        const val BYTES_PER_SAMPLE = 2
        const val INPUT_TIMEOUT_US = 10_000L
        const val INPUT_DURATION_US = 20_000L
        const val MAX_INPUT_BYTES = 4_096
        // One 20 ms frame never exceeds 1275 payload bytes; the margin covers any
        // safety padding libopus may add.
        const val MAX_PACKET_BYTES = 2_048
        const val FIRST_PACKET_LOG_COUNT = 3
    }
}
