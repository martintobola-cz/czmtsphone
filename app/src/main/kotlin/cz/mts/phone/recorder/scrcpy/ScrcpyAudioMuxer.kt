package cz.mts.phone.recorder.scrcpy

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import cz.mts.phone.recorder.RecorderLog
import java.io.Closeable
import java.io.FileDescriptor
import java.nio.ByteBuffer

private const val TAG = "ScrcpyAudioMuxer"

/**
 * Zapisuje scrcpy audio pakety do OGG (Opus) nebo MPEG-4/M4A (AAC) kontejneru
 * přes MediaMuxer. Adaptováno beze změny logiky ze ShizuCallRecorder (tam šlo
 * do souboru přes SAF, my píšeme rovnou do plain File deskriptoru).
 *
 * DŮLEŽITÉ: [close] MUSÍ být zavoláno - MediaMuxer.stop() zapisuje index
 * kontejneru (moov atom / OGG finální stránka). Bez toho může být soubor
 * nepřehratelný.
 *
 * Timestamp strategie: PTS je odvozené od System.nanoTime místo scrcpy stream
 * PTS, aby reálné ticho v přerušovaných zdrojích (VOICE_CALL, MIC) dalo
 * správné mezery místo zborceného/poškozeného souboru.
 * Viz https://github.com/Genymobile/scrcpy/pull/5870
 */
class ScrcpyAudioMuxer(
    private val outputFileDescriptor: FileDescriptor,
    private val outputDisplayPath: String
) : Closeable {

    private var muxer: MediaMuxer? = null
    private var audioTrackIndex = -1
    private var isMuxerStarted = false
    private var firstPacketTimeNanos: Long = -1L
    private var lastWrittenPtsUs: Long = -1L
    private var lastPacketWallClockNanos: Long = -1L
    private var totalIgnoredGapNanos: Long = 0L

    /** Práh pro detekci pauzy/velkého výpadku paketů (400 ms). Normální interval je ~20ms. */
    private val GAP_THRESHOLD_NANOS = 400_000_000L

    /** Přirozená mezera (25 ms), kterou necháme po "squashnutí" velké pauzy. */
    private val GAP_SLACK_NANOS = 25_000_000L

    fun initialize(codec: ScrcpyAudioCodec) {
        if (muxer != null) return
        RecorderLog.d(TAG, "Initializing muxer: codec=${codec.cliKey} format=${codec.outputFormat} path='$outputDisplayPath'")
        muxer = MediaMuxer(outputFileDescriptor, codec.outputFormat)
    }

    fun writePacket(packet: ScrcpyClient.AudioPacket, codec: ScrcpyAudioCodec) {
        if (packet.isConfigPacket) {
            if (audioTrackIndex < 0) {
                addAudioTrack(configData = packet.data, codec = codec)
            }
            return
        }

        if (!isMuxerStarted || audioTrackIndex < 0) {
            RecorderLog.w(TAG, "writePacket(): muxer not ready - dropping frame")
            return
        }

        val nowNanos = System.nanoTime()

        if (firstPacketTimeNanos == -1L) {
            firstPacketTimeNanos = nowNanos
            lastPacketWallClockNanos = nowNanos
            RecorderLog.d(TAG, "First audio frame: wall-clock origin set, pts=0")
        } else {
            val gapNanos = nowNanos - lastPacketWallClockNanos
            if (gapNanos > GAP_THRESHOLD_NANOS) {
                val ignoredNanos = gapNanos - GAP_SLACK_NANOS
                totalIgnoredGapNanos += ignoredNanos
                RecorderLog.d(TAG, "Large gap detected: ${gapNanos / 1_000_000} ms. Squashed ${ignoredNanos / 1_000_000} ms.")
            }
            lastPacketWallClockNanos = nowNanos
        }

        val wallClockPtsUs = (nowNanos - firstPacketTimeNanos - totalIgnoredGapNanos) / 1000L
        val normalizedPtsUs = if (wallClockPtsUs > lastWrittenPtsUs) wallClockPtsUs else lastWrittenPtsUs + 1L

        val bufferInfo = MediaCodec.BufferInfo().apply {
            offset             = 0
            size               = packet.data.size
            presentationTimeUs = normalizedPtsUs
        }
        lastWrittenPtsUs = normalizedPtsUs

        muxer?.writeSampleData(audioTrackIndex, ByteBuffer.wrap(packet.data), bufferInfo)
    }

    override fun close() {
        if (isMuxerStarted) {
            RecorderLog.d(TAG, "Finalizing muxer for '$outputDisplayPath'")
            runCatching { muxer?.stop() }.onFailure { e ->
                RecorderLog.e(TAG, "Muxer stop failed (file may be incomplete): ${e.message}")
            }
        }
        runCatching { muxer?.release() }
        muxer                    = null
        isMuxerStarted           = false
        audioTrackIndex          = -1
        firstPacketTimeNanos     = -1L
        lastWrittenPtsUs         = -1L
        lastPacketWallClockNanos = -1L
        totalIgnoredGapNanos     = 0L
        RecorderLog.d(TAG, "Muxer closed")
    }

    private fun addAudioTrack(configData: ByteArray, codec: ScrcpyAudioCodec) {
        val csdBytes = configData.takeIf { it.isNotEmpty() }
        if (csdBytes == null) {
            RecorderLog.e(TAG, "Empty config data. Cannot initialize audio track.")
            return
        }

        val mediaFormat = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, codec.mimeType)
            setInteger(MediaFormat.KEY_SAMPLE_RATE, ScrcpyConfig.AUDIO_SAMPLE_RATE)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, ScrcpyConfig.AUDIO_CHANNELS)
            setByteBuffer("csd-0", ByteBuffer.wrap(csdBytes))
        }

        audioTrackIndex = muxer?.addTrack(mediaFormat) ?: -1
        if (audioTrackIndex < 0) {
            RecorderLog.e(TAG, "Failed to add audio track (addTrack returned $audioTrackIndex)")
            return
        }

        muxer?.start()
        isMuxerStarted = audioTrackIndex >= 0
        RecorderLog.d(TAG, "Audio track added (index=$audioTrackIndex mime=${codec.mimeType}) - muxer started")
    }
}
