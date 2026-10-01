// Place at: app/src/main/aidl/cz/mts/phone/recorder/IShellService.aidl
package cz.mts.phone.recorder;

import android.os.ParcelFileDescriptor;

interface IShellService {
    /**
     * Spustí audio-capture pipeline (scrcpy-server v shell procesu).
     *
     * @param audioSource   scrcpy audio_source parametr (my vždy posíláme "voice-call")
     * @param audioCodec    scrcpy audio_codec parametr (my vždy posíláme "opus")
     * @param audioBitRate  bitrate v bps (my vždy posíláme 16000)
     * @param serverPath    absolutní cesta k scrcpy-server.jar ve sdíleném úložišti
     * @return read-end ParcelFileDescriptor audio pipe, nebo null při selhání
     */
    ParcelFileDescriptor startRecording(
        String audioSource,
        String audioCodec,
        int audioBitRate,
        String serverPath
    ) = 1;

    /** Zastaví audio capture pipeline a uvolní všechny prostředky. */
    void stopRecording() = 2;

    /**
     * Vrátí a vyprázdní záznamy z RecorderLog ve shell procesu (přenosový formát,
     * viz RecorderLog.drainForTransfer). Appka je sloučí do svého logu.
     */
    List<String> drainLogs() = 3;

    /**
     * Volá Shizuku, když chce tuhle user service ukončit.
     * MUSÍ zavolat kotlin.system.exitProcess, aby se celý shell proces ukončil.
     */
    void destroy() = 16777114;
}
