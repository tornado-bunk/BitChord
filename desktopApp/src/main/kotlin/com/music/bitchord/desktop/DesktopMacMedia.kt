package com.music.bitchord.desktop

import java.util.concurrent.atomic.AtomicBoolean

/**
 * macOS System Media Controls (MPNowPlayingInfoCenter & MPRemoteCommandCenter)
 * for the desktop player.
 */
internal object DesktopMacMedia {

    private const val LIBRARY = "bitchord_nowplaying"

    private const val BUTTON_PLAY = 1
    private const val BUTTON_PAUSE = 2
    private const val BUTTON_NEXT = 3
    private const val BUTTON_PREVIOUS = 4
    private const val BUTTON_STOP = 5

    private val started = AtomicBoolean(false)
    private val shutdownHookAdded = AtomicBoolean(false)

    @Volatile
    private var controller: Controller? = null

    internal class Controller(
        val onPlay: () -> Unit,
        val onPause: () -> Unit,
        val onNext: () -> Unit,
        val onPrevious: () -> Unit,
        val onStop: () -> Unit,
    )

    private val available: Boolean by lazy {
        if (!DesktopPlatform.isMac) return@lazy false
        runCatching { DesktopAnalysisRuntime.loadNative(LIBRARY) }
            .onFailure { DesktopTrackLog.log("macOS media controls unavailable: ${it.message}") }
            .isSuccess
    }

    fun start(controller: Controller) {
        this.controller = controller
        if (!available || !started.compareAndSet(false, true)) return
        if (!nativeStart()) {
            started.set(false)
            DesktopTrackLog.log("macOS media controls could not be started")
            return
        }
        if (shutdownHookAdded.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(Thread(::stop, "bitchord-macmedia-stop"))
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        controller = null
        runCatching { nativeStop() }
    }

    fun publish(state: DesktopPlaybackState) {
        if (!started.get()) return
        val song = state.song
        runCatching {
            nativeUpdate(
                song?.title.orEmpty(),
                song?.artist.orEmpty(),
                song?.albumName.orEmpty(),
                song?.thumbnailUrl.orEmpty(),
                state.isPlaying,
                state.positionMs,
                state.durationMs,
            )
        }
    }

    @JvmStatic
    fun onButton(button: Int) {
        val target = controller ?: return
        when (button) {
            BUTTON_PLAY -> target.onPlay()
            BUTTON_PAUSE -> target.onPause()
            BUTTON_NEXT -> target.onNext()
            BUTTON_PREVIOUS -> target.onPrevious()
            BUTTON_STOP -> target.onStop()
        }
    }

    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeUpdate(
        title: String,
        artist: String,
        album: String,
        artUrl: String,
        playing: Boolean,
        positionMs: Long,
        durationMs: Long,
    )
}
