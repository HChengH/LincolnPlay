package com.shilapi.xcertplay.media

import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import com.shilapi.xcertplay.airplay.MicrophoneCounters
import com.shilapi.xcertplay.airplay.MicrophonePacketizer
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures one PCM microphone stream and sends it back to the phone as sealed CarPlay RTP.
 *
 * The recorder runs only while the matching audio stream is active, so callers start this after
 * the first downlink audio packet and close it on stream teardown.
 */
internal class MicrophoneUplink(
    private val context: android.content.Context,
    private val config: MicrophoneConfig,
    private val onDiagnostic: (String) -> Unit = {},
) : Closeable {
    private val running = AtomicBoolean(false)
    private val stats = MicrophoneCaptureStats(config, report = { message ->
        Log.i(TAG, message)
        onDiagnostic(message)
    })
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var opusEncoder: OpusEncoder? = null
    @Volatile private var effects: List<AudioEffect> = emptyList()
    private var thread: Thread? = null

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true

        val channelMask = if (config.channels >= 2) {
            AndroidAudioFormat.CHANNEL_IN_STEREO
        } else {
            AndroidAudioFormat.CHANNEL_IN_MONO
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRate,
            channelMask,
            AndroidAudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.w(TAG, "microphone unavailable rate=${config.sampleRate} channels=${config.channels}")
            stats.failure(MicrophoneFailureStage.MIN_BUFFER, code = minBuffer)
            running.set(false)
            return false
        }

        // Always VOICE_COMMUNICATION: on this board the physical mic lives on the voice DSP
        // path and only routes to Android capture while that path is held — the factory
        // voice assistant opening it made our capture hear real audio (user-verified), and
        // MIC/VOICE_RECOGNITION sources capture dead silence otherwise. VOICE_COMMUNICATION
        // is the source the policy ties to that path, with AEC/NS as a side benefit.
        val source = MediaRecorder.AudioSource.VOICE_COMMUNICATION
        val nextEncoder = if (config.codec == AudioCodecKind.OPUS) {
            OpusEncoder(config.bitrate ?: 48_000).takeIf { it.available }
        } else {
            null
        }
        if (config.codec == AudioCodecKind.OPUS && nextEncoder == null) {
            Log.w(TAG, "microphone Opus encoder is unavailable")
            stats.failure(MicrophoneFailureStage.ENCODER)
            running.set(false)
            return false
        }
        val bufferSize = maxOf(minBuffer * 2, config.frameBytes * 4)
        val nextRecorder = try {
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AndroidAudioFormat.Builder()
                        .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(config.sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
        } catch (error: Exception) {
            Log.e(TAG, "microphone recorder creation failed", error)
            stats.failure(MicrophoneFailureStage.RECORDER_CREATION, error)
            nextEncoder?.close()
            running.set(false)
            return false
        }
        if (nextRecorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "microphone recorder failed to initialize")
            stats.failure(MicrophoneFailureStage.RECORDER_INITIALIZATION, code = nextRecorder.state)
            nextRecorder.release()
            nextEncoder?.close()
            running.set(false)
            return false
        }

        val nextSocket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("::"), 0))
            }
        } catch (error: Exception) {
            Log.e(TAG, "microphone socket creation failed", error)
            stats.failure(MicrophoneFailureStage.SOCKET_CREATION, error)
            nextRecorder.release()
            nextEncoder?.close()
            running.set(false)
            return false
        }

        recorder = nextRecorder
        socket = nextSocket
        opusEncoder = nextEncoder
        return try {
            // Vendor voice DSP gate: on this board the builtin mic delivers digital
            // silence to plain captures; the AEC/NS chain (and the vendor voice app)
            // opens the DSP path. Attach for every uplink as the probe experiment.
            effects = voiceEffects(nextRecorder.audioSessionId)
            nextRecorder.startRecording()
            stats.started(routeType(nextRecorder))
            stats.inputDevices(inputDeviceList())
            gateKeeper = openGateKeeper()
            thread = Thread({ capture(nextRecorder, nextSocket) }, "carplay-mic").apply {
                isDaemon = true
                start()
            }
            true
        } catch (error: Exception) {
            Log.e(TAG, "microphone recording failed", error)
            stats.failure(MicrophoneFailureStage.RECORDING, error)
            release()
            false
        }
    }

    private fun voiceEffects(sessionId: Int): List<AudioEffect> = listOfNotNull(
        enabledEffect("AEC") {
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId) else null
        },
        enabledEffect("NS") {
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId) else null
        },
    )

    // Advertised effects may still fail to initialize on a vendor ROM. Keep recording without them.
    private fun enabledEffect(name: String, create: () -> AudioEffect?): AudioEffect? {
        var effect: AudioEffect? = null
        try {
            effect = create()
            if (effect != null) {
                val status = effect.setEnabled(true)
                if (status == AudioEffect.SUCCESS && effect.enabled) {
                    Log.i(TAG, "microphone effect=$name enabled=true")
                    return effect
                }
                Log.w(TAG, "microphone effect=$name could not be enabled status=$status")
            } else {
                Log.i(TAG, "microphone effect=$name unavailable")
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "microphone effect=$name unavailable; continuing without it", error)
        }
        effect?.let(::releaseEffect)
        return null
    }

    private fun releaseEffect(effect: AudioEffect) {
        try {
            effect.release()
        } catch (error: RuntimeException) {
            Log.w(TAG, "microphone effect release failed", error)
        }
    }

    private fun capture(recorder: AudioRecord, socket: DatagramSocket) {
        val frame = ByteArray(config.frameBytes)
        val readBuffer = ByteArray(maxOf(frame.size, MIN_READ_BYTES))
        val counters = MicrophoneCounters()
        val routeInfo = { routeType(recorder) }
        var filled = 0
        try {
            while (running.get()) {
                stats.reading()
                val count = recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                stats.read(count)
                if (count < 0) {
                    if (running.get()) {
                        Log.e(TAG, "microphone read failed code=$count")
                        stats.failure(MicrophoneFailureStage.READ, code = count)
                    }
                    return
                }
                if (count == 0) {
                    stats.flush(routeType = routeInfo)
                    continue
                }
                var offset = 0
                while (offset < count && running.get()) {
                    val copied = minOf(frame.size - filled, count - offset)
                    readBuffer.copyInto(frame, filled, offset, offset + copied)
                    filled += copied
                    offset += copied
                    if (filled == frame.size) {
                        sendFrame(socket, counters, frame)
                        filled = 0
                    }
                }
                feedGateKeeper(count)
                stats.flush(routeType = routeInfo)
            }
        } catch (error: Exception) {
            if (running.get()) {
                Log.e(TAG, "microphone capture failed", error)
                stats.failure(MicrophoneFailureStage.CAPTURE, error)
            }
        } finally {
            stats.flush(ended = true, routeType = routeInfo)
            running.set(false)
            release()
        }
    }

    private fun sendFrame(socket: DatagramSocket, counters: MicrophoneCounters, frame: ByteArray) {
        stats.level(framePeak(frame))
        val bodies = if (config.codec == AudioCodecKind.OPUS) {
            opusEncoder?.encode(frame).orEmpty()
        } else {
            listOf(MicrophonePacketizer.toWirePcm(frame))
        }
        stats.encoded(bodies.size, if (bodies.isEmpty()) 1 else bodies.count { it.isEmpty() })
        bodies.forEach { body ->
            sendPacket(
                socket = socket,
                counters = counters,
                body = body,
                samples = config.rtpSamplesPerPacket,
            )
        }
    }

    /** Max |sample| across the frame (16-bit LE, strided); distinguishes routed audio from silence. */
    private fun framePeak(frame: ByteArray): Int {
        var peak = 0
        var i = 0
        while (i + 1 < frame.size) {
            val sample = (frame[i + 1].toInt() shl 8) or (frame[i].toInt() and 0xff)
            val abs = if (sample < 0) -sample else sample
            if (abs > peak) peak = abs
            i += 32
        }
        return peak
    }

    private fun sendPacket(
        socket: DatagramSocket,
        counters: MicrophoneCounters,
        body: ByteArray,
        samples: Int,
    ) {
        val packet = MicrophonePacketizer.sealPacket(
            key = config.key,
            payloadType = config.payloadType,
            counters = counters,
            body = body,
            samples = samples,
        )
        try {
            socket.send(DatagramPacket(packet, packet.size, config.host, config.port))
            stats.sent()
        } catch (error: Exception) {
            stats.sendFailed()
            if (running.get()) throw error
        }
    }

    private fun routeType(recorder: AudioRecord): Int? = runCatching { recorder.routedDevice?.type }.getOrNull()

    /** Names every input device the audio manager knows about, for the routing probe. */
    private fun inputDeviceList(): List<String> = runCatching {
        val am = context.getSystemService(android.media.AudioManager::class.java) ?: return emptyList()
        val devices = am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
        if (devices.isEmpty()) return emptyList()
        devices.map { device ->
            "type=${device.type}${device.address?.takeIf { it.isNotBlank() }?.let { " addr=$it" } ?: ""}"
        }
    }.getOrDefault(emptyList())

    /**
     * The board's mic DSP gate follows the OUTPUT side's audio scene: real mic audio only
     * reaches captures while a MEDIA-usage output stream is (or was recently) playing, and
     * a speech stream tears that state down (user-verified: record after music works,
     * after voice playback it dies, music again revives it). A silent MEDIA track held
     * for the whole capture pins the scene open; it also keeps the output path alive so
     * Siri's reply stays audible. Zeros only - inaudible and outside the renderer map,
     * so ducking and media accounting are untouched.
     */
    @Volatile private var gateKeeper: android.media.AudioTrack? = null
    private val gateKeeperSilence = ByteArray(4_096)

    private fun openGateKeeper(): android.media.AudioTrack? = runCatching {
        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = android.media.AudioFormat.Builder()
            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(GATE_KEEPER_RATE)
            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBuffer = android.media.AudioTrack.getMinBufferSize(
            GATE_KEEPER_RATE, android.media.AudioFormat.CHANNEL_OUT_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4_096)
        android.media.AudioTrack(attributes, format, minBuffer * 2,
            android.media.AudioTrack.MODE_STREAM, 0).also { track ->
            track.play()
            Log.i(TAG, "mic gate-keeper MEDIA track opened rate=$GATE_KEEPER_RATE")
        }
    }.onFailure {
        Log.w(TAG, "mic gate-keeper track unavailable", it)
    }.getOrNull()

    /** Paces silence onto the keeper at real time, scaled from the capture rate. */
    private fun feedGateKeeper(capturedBytes: Int) {
        val track = gateKeeper ?: return
        runCatching {
            val bytes = capturedBytes * GATE_KEEPER_RATE / config.sampleRate.coerceAtLeast(1)
            var written = 0
            while (written < bytes) {
                val chunk = minOf(gateKeeperSilence.size, bytes - written)
                val result = track.write(gateKeeperSilence, 0, chunk, android.media.AudioTrack.WRITE_BLOCKING)
                if (result <= 0) break
                written += result
            }
        }
    }

    private fun closeGateKeeper() {
        val track = gateKeeper ?: return
        gateKeeper = null
        runCatching {
            track.stop()
        }
        runCatching {
            track.release()
        }
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) {
            release()
            return
        }
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // Best effort; release below is authoritative.
        }
        try {
            socket?.close()
        } catch (_: Exception) {
            // Best effort.
        }
        thread?.let { worker ->
            try {
                worker.join(CLOSE_JOIN_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (worker.isAlive) worker.interrupt()
        }
        release()
    }

    @Synchronized
    private fun release() {
        running.set(false)
        closeGateKeeper()
        val currentEffects = effects
        effects = emptyList()
        currentEffects.forEach(::releaseEffect)
        val currentRecorder = recorder
        recorder = null
        try {
            currentRecorder?.release()
        } catch (_: Exception) {
            // Best effort.
        }
        val currentSocket = socket
        socket = null
        try {
            currentSocket?.close()
        } catch (_: Exception) {
            // Best effort.
        }
        val currentEncoder = opusEncoder
        opusEncoder = null
        currentEncoder?.close()
    }

    private companion object {
        const val TAG = "xcertplay-usb"
        const val MIN_READ_BYTES = 2_048
        const val CLOSE_JOIN_MILLIS = 500L
        const val GATE_KEEPER_RATE = 48_000
    }
}
