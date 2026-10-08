package cz.mts.phone.recorder

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException

/**
 * Mini přehrávač jednoho souboru (ogg i cokoli jiného, co umí MediaPlayer).
 * Volat z hlavního vlásku. Vždy hraje maximálně jeden soubor – nový play() ten předchozí zastaví.
 */
class SimpleAudioPlayer(
    /**
     * Zavolá se při každé změně stavu (nový soubor, připraveno, pause/resume, konec).
     * Parametr je cesta aktuálního souboru, nebo null když nic není načtené.
     * Podrobnosti (hraje / pauza / délka) si zjisti přes [isPlaying], [isPrepared], [durationMs].
     */
    private val onStateChanged: (currentPath: String?) -> Unit = {},
    private val onError: (Exception) -> Unit = {}
) {

    private var player: MediaPlayer? = null

    var currentPath: String? = null
        private set

    /** true až po dokončení prepareAsync() – teprve pak jde zjistit délku, pozastavit a seekovat. */
    var isPrepared: Boolean = false
        private set

    val isPlaying: Boolean
        get() = try {
            isPrepared && player?.isPlaying == true
        } catch (e: IllegalStateException) {
            false
        }

    /** Délka v ms; 0 dokud není připraveno (nebo když ji formát neumí říct). */
    val durationMs: Int
        get() = try {
            if (isPrepared) (player?.duration ?: 0).coerceAtLeast(0) else 0
        } catch (e: IllegalStateException) {
            0
        }

    /** Aktuální pozice v ms. */
    val positionMs: Int
        get() = try {
            if (isPrepared) (player?.currentPosition ?: 0).coerceAtLeast(0) else 0
        } catch (e: IllegalStateException) {
            0
        }

    /** Stejný soubor znovu = stop, jiný soubor = přepnout na něj. */
    fun toggle(file: File) {
        if (currentPath == file.absolutePath) stop() else play(file)
    }

    fun play(file: File) {
        stop()

        if (!file.exists()) {
            onError(FileNotFoundException(file.absolutePath))
            return
        }

        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            FileInputStream(file).use { mp.setDataSource(it.fd) }

            mp.setOnPreparedListener {
                if (player === mp) {
                    isPrepared = true
                    it.start()
                    onStateChanged(currentPath)
                }
            }
            mp.setOnCompletionListener {
                if (player === mp) stop()
            }
            mp.setOnErrorListener { _, what, extra ->
                if (player === mp) {
                    stop()
                    onError(IllegalStateException("MediaPlayer error what=$what extra=$extra"))
                }
                true
            }

            player = mp
            currentPath = file.absolutePath
            isPrepared = false
            onStateChanged(currentPath)
            mp.prepareAsync()
        } catch (e: Exception) {
            try {
                mp.release()
            } catch (ignored: Exception) {
            }
            player = null
            currentPath = null
            isPrepared = false
            onStateChanged(null)
            onError(e)
        }
    }

    fun pause() {
        val mp = player ?: return
        if (!isPrepared) return
        try {
            if (mp.isPlaying) mp.pause()
        } catch (ignored: IllegalStateException) {
        }
        onStateChanged(currentPath)
    }

    fun resume() {
        val mp = player ?: return
        if (!isPrepared) return
        try {
            if (!mp.isPlaying) mp.start()
        } catch (ignored: IllegalStateException) {
        }
        onStateChanged(currentPath)
    }

    fun togglePause() {
        if (isPlaying) pause() else resume()
    }

    /** Přeskočí na danou pozici (ms). Funguje za hraní i v pauze. */
    fun seekTo(positionMs: Int) {
        val mp = player ?: return
        if (!isPrepared) return
        val target = positionMs.coerceIn(0, durationMs)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // SEEK_CLOSEST = přesně na zvolené místo, ne na nejbližší předchozí keyframe
                mp.seekTo(target.toLong(), MediaPlayer.SEEK_CLOSEST)
            } else {
                mp.seekTo(target)
            }
        } catch (ignored: IllegalStateException) {
        }
    }

    fun stop() {
        val mp = player ?: return
        player = null
        currentPath = null
        isPrepared = false

        try {
            mp.setOnPreparedListener(null)
            mp.setOnCompletionListener(null)
            mp.setOnErrorListener(null)
            mp.stop()
        } catch (ignored: IllegalStateException) {
            // stop() během prepare – nevadí, release() to uklidí
        }
        mp.release()
        onStateChanged(null)
    }
}
