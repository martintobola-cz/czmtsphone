package cz.mts.phone.recorder.scrcpy

/**
 * Zjednodušená verze ScrcpyAudioSource ze ShizuCallRecorder - žádné UI popisky,
 * jen cliKey, protože uživatel si zdroj nevybírá (natvrdo VOICE_CALL).
 * Ostatní hodnoty necháváme v kódu pro budoucí použití, i když teď nejsou
 * odnikud volané.
 *
 * Zdroj: https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/audio/AudioSource.java
 */
enum class ScrcpyAudioSource(val cliKey: String) {
    /** Mikrofon laděný pro VoIP (echo cancellation, AGC). */
    VOICE_COMMUNICATION("mic-voice-communication"),

    /** Obě strany hovoru (uplink + downlink). Vyžaduje CAPTURE_AUDIO_OUTPUT (shell UID ho má). */
    VOICE_CALL("voice-call"),

    /** Jen lokální strana hovoru (mikrofon volajícího/tebe). */
    VOICE_CALL_UPLINK("voice-call-uplink"),

    /** Jen vzdálená strana hovoru (druhá strana, sluchátko). */
    VOICE_CALL_DOWNLINK("voice-call-downlink"),

    /** Mix audia jdoucí do reproduktoru (REMOTE_SUBMIX). */
    OUTPUT("output"),

    /** Audio výstup ostatních appek (AudioPlaybackCapture, API 29+). */
    PLAYBACK("playback"),

    /** Obyčejný mikrofon. */
    MIC("mic");

    companion object {
        fun fromKey(key: String): ScrcpyAudioSource =
            entries.firstOrNull { it.cliKey == key }
                ?: throw IllegalArgumentException("Unknown ScrcpyAudioSource key: $key")
    }
}
