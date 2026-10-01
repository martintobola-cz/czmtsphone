package cz.mts.phone.recorder.scrcpy

import android.media.MediaFormat
import android.media.MediaMuxer

/**
 * Zjednodušená verze ScrcpyAudioCodec ze ShizuCallRecorder - bez titleResId
 * (žádné UI pro výběr), zbytek stejný. Uživatel dostane vždy OPUS/16kbps,
 * ale AAC necháváme v kódu pro budoucí použití.
 *
 * Zdroj: https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/audio/AudioCodec.java
 */
enum class ScrcpyAudioCodec(
    val cliKey: String,
    val codecFourCC: Int,
    val defaultBitRate: Int,
    val outputFormat: Int,
    val mimeType: String,
    val containerExtension: String
) {
    /** Preferovaný - srozumitelná řeč i při velmi nízkém bitrate (16 kbps). */
    OPUS(
        cliKey             = "opus",
        codecFourCC        = 0x6F707573, // ASCII "opus"
        defaultBitRate     = 16000,
        outputFormat       = MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG,
        mimeType           = MediaFormat.MIMETYPE_AUDIO_OPUS,
        containerExtension = ".ogg"
    ),

    /** Širší kompatibilita, ale potřebuje vyšší bitrate pro srovnatelnou kvalitu. */
    AAC(
        cliKey             = "aac",
        codecFourCC        = 0x00616163, // ASCII "\0aac"
        defaultBitRate     = 32000,
        outputFormat       = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        mimeType           = MediaFormat.MIMETYPE_AUDIO_AAC,
        containerExtension = ".m4a"
    );

    companion object {
        fun fromKey(key: String): ScrcpyAudioCodec =
            entries.firstOrNull { it.cliKey == key }
                ?: throw IllegalArgumentException("Unknown ScrcpyAudioCodec key: $key")

        fun fromFourCC(fourCC: Int): ScrcpyAudioCodec =
            entries.firstOrNull { it.codecFourCC == fourCC }
                ?: throw IllegalArgumentException("Unknown ScrcpyAudioCodec fourCC: $fourCC")
    }
}
