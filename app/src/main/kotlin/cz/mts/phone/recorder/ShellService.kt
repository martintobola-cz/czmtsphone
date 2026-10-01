package cz.mts.phone.recorder

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import kotlin.system.exitProcess

private const val TAG = "ShellService"

/**
 * ShellService běží uvnitř privilegovaného shell procesu (UID 2000), který
 * spravuje Shizuku. Adaptováno ze ShizuCallRecorder, zjednodušeno o
 * appop/role granting a remote logging (nepotřebujeme pro jednoduché
 * nahrávání na tlačítko).
 *
 * Požadavky Shizuku:
 *  - Musí mít bezparametrický konstruktor A konstruktor s jedním Context (Shizuku v13+).
 *  - Musí být @Keep, jinak ho ProGuard/R8 v release buildu odstraní/přejmenuje.
 *  - [destroy] MUSÍ zavolat exitProcess, aby se shell proces skutečně ukončil.
 */
@Keep
class ShellService : IShellService.Stub {

    private val audioPipeline by lazy { ShellAudioPipeline() }

    @Keep
    constructor() : this(null)

    @Keep
    constructor(context: Context?) {
        // Tenhle proces má vlastní instanci RecorderLog - označ ji, ať je ve výpisu poznat.
        RecorderLog.setProcessLabel("shell")
        RecorderLog.i(TAG, "ShellService process started, UID=${android.os.Process.myUid()}")
    }

    override fun startRecording(
        audioSource: String,
        audioCodec: String,
        audioBitRate: Int,
        serverPath: String
    ): ParcelFileDescriptor? {
        return audioPipeline.startCapture(audioSource, audioCodec, audioBitRate, serverPath)
    }

    override fun stopRecording() {
        audioPipeline.stopCapture()
    }

    /** Vrátí a vyprázdní záznamy logu z tohoto (shell) procesu. */
    override fun drainLogs(): MutableList<String> {
        return RecorderLog.drainForTransfer().toMutableList()
    }

    /** Volá Shizuku, když chce tuhle user service ukončit. */
    override fun destroy() {
        RecorderLog.i(TAG, "ShellService.destroy() - terminating shell process")
        stopRecording()
        exitProcess(0)
    }
}
