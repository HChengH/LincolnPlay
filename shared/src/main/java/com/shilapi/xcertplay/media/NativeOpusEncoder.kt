package com.shilapi.xcertplay.media

import android.util.Log

/**
 * Binding over the bundled libopus (`diplay_opus_jni`), the software fallback for the
 * CarPlay microphone uplink on boards whose MediaCodec exposes no Opus encoder
 * (Android 8.x). All entry points are no-ops or zeroes when the library is missing.
 */
internal object NativeOpusEncoder {
    const val APPLICATION_VOIP = 2048
    const val APPLICATION_AUDIO = 2049

    @Volatile private var loadAttempted = false
    @Volatile private var libraryLoaded = false

    val available: Boolean
        get() {
            if (loadAttempted) return libraryLoaded
            synchronized(this) {
                if (loadAttempted) return libraryLoaded
                libraryLoaded = runCatching { System.loadLibrary("diplay_opus_jni") }
                    .onFailure { Log.w(TAG, "native Opus library unavailable", it) }
                    .isSuccess
                loadAttempted = true
                return libraryLoaded
            }
        }

    fun createEncoder(sampleRate: Int, channels: Int, application: Int, bitrate: Int): Long =
        if (available) nativeCreate(sampleRate, channels, application, bitrate) else 0L

    /** Returns the encoded packet length, or a negative Opus error code. */
    fun encode(handle: Long, pcm: ByteArray, sampleCount: Int, output: ByteArray): Int =
        if (handle == 0L) -1 else nativeEncode(handle, pcm, sampleCount, output)

    fun destroy(handle: Long) {
        if (handle != 0L) nativeDestroy(handle)
    }

    private external fun nativeCreate(sampleRate: Int, channels: Int, application: Int, bitrate: Int): Long
    private external fun nativeEncode(handle: Long, pcm: ByteArray, sampleCount: Int, output: ByteArray): Int
    private external fun nativeDestroy(handle: Long)

    private const val TAG = "xcertplay-usb"
}
