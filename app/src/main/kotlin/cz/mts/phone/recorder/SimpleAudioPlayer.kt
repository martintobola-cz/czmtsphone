package cz.mts.phone.recorder

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException

/**
 * Mini přehrávač jednoho souboru (ogg i cokoli jiného, co umí MediaPlayer).
 * Volat z hlavního vlásku. Vždy hraje maximálně jeden soubor – nový play() ten předchozí zastaví.
 */
class SimpleAudioPlayer(
    /** Zavolá se při každé změně; parametr je cesta hrajícího souboru, nebo null když nic nehraje. */
    private val onStateChanged: (playingPath: String?) -> Unit = {},
    private val onError: (Exception) -> Unit = {}
) {

    private var player: MediaPlayer? = null

    var currentPath: String? = null
        private set

    val isPlaying: Boolean
        get() = try {
            player?.isPlaying == true
        } catch (e: IllegalStateException) {
            false
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
                if (player === mp) it.start()
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
            onStateChanged(currentPath)
            mp.prepareAsync()
        } catch (e: Exception) {
            try {
                mp.release()
            } catch (ignored: Exception) {
            }
            player = null
            currentPath = null
            onStateChanged(null)
            onError(e)
        }
    }

    fun stop() {
        val mp = player ?: return
        player = null
        currentPath = null

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
