package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import com.shilapi.xcertplay.media.CarPlayNowPlaying

/**
 * Shows the iPhone's now-playing track on the instrument cluster's song widget, like
 * Bluetooth audio did. Mirrors the radio-widget dialect the lyrics-board app proved on
 * this board: `com.baidu.car.radio.notify.PLAY_INFO` broadcasts whose `title` extra
 * carries the line to show. A sender that ever tunnels lyric lines through the
 * now-playing title would scroll here unchanged.
 */
internal class ClusterSongPublisher(
    context: Context,
    private val enabled: () -> Boolean,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var nowPlaying = CarPlayNowPlaying()
    private var playing = false
    private var lastTitle: String? = null
    private var lastArtist: String? = null
    private var lastAlbum: String? = null
    private var lastPlaying = false
    private var lastBroadcastAt = 0L

    /** Feed from the media keys' now-playing stream; runs on the main thread. */
    fun onNowPlaying(update: CarPlayNowPlaying, updatePlaying: Boolean) {
        if (update.title?.trim().orEmpty().isEmpty()) return
        nowPlaying = update
        playing = updatePlaying
        publish()
    }

    /** Stops publishing; one final paused broadcast so the widget does not stick mid-state. */
    fun release() {
        mainHandler.post {
            if (lastTitle != null) broadcast(lastTitle!!, playing = false)
            lastTitle = null
            lastArtist = null
            lastAlbum = null
        }
    }

    private fun publish() {
        if (!enabled()) {
            lastTitle = null
            return
        }
        val title = nowPlaying.title?.trim().orEmpty()
        if (title.isEmpty()) return
        val artist = nowPlaying.artist?.trim()
        val now = SystemClock.elapsedRealtime()
        val changed = title != lastTitle || artist != lastArtist ||
            nowPlaying.album != lastAlbum || playing != lastPlaying
        if (!changed && now - lastBroadcastAt < KEEP_ALIVE_MILLIS) return
        if (title != lastTitle) DiagnosticSnifferHook.post("Cluster song: $title — ${artist.orEmpty()}")
        broadcast(title, playing)
        lastTitle = title
        lastArtist = artist
        lastAlbum = nowPlaying.album
        lastPlaying = playing
        lastBroadcastAt = now
    }

    private fun broadcast(text: String, playing: Boolean) {
        val intent = Intent(ACTION_PLAY_INFO).addFlags(BROADCAST_FLAGS)
        intent.putExtra("title", text)
        intent.putExtra("artist", nowPlaying.artist?.trim().orEmpty())
        nowPlaying.album?.trim()?.takeIf { it.isNotEmpty() }?.let { intent.putExtra("album_name", it) }
        val durationSeconds = nowPlaying.durationMillis?.let { (it / 1000).toInt() } ?: 0
        if (durationSeconds > 0) intent.putExtra("duration", durationSeconds)
        intent.putExtra("is_playing", playing)
        intent.putExtra("source_type", SOURCE_TYPE_BLUETOOTH_AUDIO)
        intent.putExtra("source_name", nowPlaying.sourceApp?.takeIf { it.isNotBlank() } ?: "CarPlay")
        send(intent)
    }

    /** The lyrics app reaches the cluster receiver through the all-users broadcast path. */
    private fun send(intent: Intent) {
        val handle = allUsersHandle
        val asUser = sendBroadcastAsUser
        if (handle != null && asUser != null) {
            try {
                asUser.invoke(appContext, intent, handle)
                return
            } catch (error: Throwable) {
                // Fall through to the plain broadcast, like the lyrics app's SecurityException path.
            }
        }
        runCatching { appContext.sendBroadcast(intent) }
    }

    private val allUsersHandle: Any? by lazy {
        runCatching {
            UserHandle::class.java
                .getMethod("getUserHandleForUid", Int::class.javaPrimitiveType)
                .invoke(null, -1)
        }.getOrNull()
    }

    private val sendBroadcastAsUser by lazy {
        runCatching {
            Context::class.java.getMethod("sendBroadcastAsUser", Intent::class.java, UserHandle::class.java)
        }.getOrNull()
    }

    private companion object {
        const val ACTION_PLAY_INFO = "com.baidu.car.radio.notify.PLAY_INFO"
        const val BROADCAST_FLAGS = 0x01000000
        const val SOURCE_TYPE_BLUETOOTH_AUDIO = 5
        const val KEEP_ALIVE_MILLIS = 5_000L
    }
}
