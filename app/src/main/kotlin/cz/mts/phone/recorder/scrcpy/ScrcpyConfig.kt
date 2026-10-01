package cz.mts.phone.recorder.scrcpy

import android.content.Context
import cz.mts.phone.BuildConfig
import java.security.SecureRandom

/**
 * Centralizuje konstanty a helpery pro práci se scrcpy-server.
 * Adaptováno ze ShizuCallRecorder (com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyConfig).
 *
 * scrcpy-server běží přes `app_process` pod shell UID (2000) a poskytuje přístup
 * k audio zdrojům, které normální appka nemá (VOICE_CALL apod.).
 *
 * Reference:
 *  • https://github.com/Genymobile/scrcpy/blob/master/doc/develop.md
 *  • https://github.com/Genymobile/scrcpy/blob/master/doc/audio.md
 */
object ScrcpyConfig {

    /** Verze scrcpy-server bundlovaná v APK (viz BuildConfigField v build.gradle.kts). */
    const val SCRCPY_VERSION: String = BuildConfig.SCRCPY_VERSION

    /** Očekávaný SHA-256 hash scrcpy-server.jar, pro ověření integrity před spuštěním. */
    const val EXPECTED_SERVER_SHA256: String = BuildConfig.SCRCPY_SERVER_SHA256

    /**
     * Vrací absolutní cestu, kam se má scrcpy-server.jar uložit/hledat.
     *
     * Shell proces (UID 2000) nevidí privátní data appky (/data/data/...), ale vidí
     * sdílené úložiště (/storage/emulated/0/Android/data/<pkg>/...), proto external
     * files dir - jediné místo zapisovatelné appkou A čitelné shellem bez rootu.
     */
    fun getServerPath(context: Context): String {
        val folder = context.getExternalFilesDir(null)
            ?: context.externalCacheDir
            ?: throw IllegalStateException("Sdílené úložiště není dostupné.")
        return folder.absolutePath + "/scrcpy-${SCRCPY_VERSION}-server.jar"
    }

    /** Plně kvalifikovaný název hlavní třídy uvnitř scrcpy-server.jar. */
    const val SERVER_MAIN_CLASS = "com.genymobile.scrcpy.Server"

    /** Viz DesktopConnection.java v scrcpy-server. */
    const val SERVER_SOCKET_NAME_PREFIX = "scrcpy_"

    /** Sample rate používaný scrcpy-server pro Opus i AAC (48 kHz). */
    const val AUDIO_SAMPLE_RATE = 48000

    /** scrcpy-server vždy vrací stereo. */
    const val AUDIO_CHANNELS = 2

    /**
     * Sestaví argumenty pro spuštění scrcpy-server (za verzí, viz buildServerArgs).
     *
     * @param socketName   8 hex znaků, scrcpy je parsuje jako Integer.parseInt(…, 16).
     * @param audioSource  natvrdo VOICE_CALL v naší appce, ale parametr necháváme obecný.
     * @param audioCodec   natvrdo OPUS v naší appce.
     * @param audioBitRate natvrdo 16000 v naší appce.
     */
    fun buildServerArgs(
        socketName: String,
        audioSource: ScrcpyAudioSource,
        audioCodec: ScrcpyAudioCodec,
        audioBitRate: Int
    ): List<String> {
        val args = mutableListOf(
            SCRCPY_VERSION,
            "log_level=info",
            "video=false",
            "audio=true",
            "control=false",
            // tunnel_forward=false: scrcpy-server dial NÁŠ LocalServerSocket (ne obráceně).
            // V tomto módu server nepíše dummy byte před codec daty.
            "tunnel_forward=false",
            "send_dummy_byte=false",
            "scid=$socketName",
            "audio_source=${audioSource.cliKey}",
            "audio_codec=${audioCodec.cliKey}",
            "send_device_meta=false",
            "send_frame_meta=true",
            "send_stream_meta=true"
        )
        if (audioBitRate > 0) {
            args.add("audio_bit_rate=$audioBitRate")
        }
        return args
    }

    /** Náhodný 8-hex-znakový název socketu (BEZ prefixu SERVER_SOCKET_NAME_PREFIX). */
    fun getRandomSocketName(): String {
        return SecureRandom().nextInt(Int.MAX_VALUE).toString(16).padStart(8, '0')
    }
}
