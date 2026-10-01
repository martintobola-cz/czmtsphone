package cz.mts.phone.recorder.scrcpy

import android.os.ParcelFileDescriptor
import cz.mts.phone.recorder.RecorderLog
import java.io.*

private const val TAG = "ScrcpyClient"

/**
 * Čte binární audio stream, který scrcpy-server posílá přes pipe.
 * Adaptováno beze změny logiky ze ShizuCallRecorder.
 *
 * Hlavička spojení (jednou):
 *  - 4 bajty: Codec FourCC (např. "opus" 0x6F707573)
 *
 * Scrcpy v4.0 Frame Header (12 bajtů na paket):
 *     [. . . . . . . .|. . . .]. . . . . . . . . . . . . . . ...
 *      <-------------> <-----> <-----------------------------...
 *            PTS        packet        raw packet
 *                        size
 *
 * Nejvýznamnější bity 8-bajtového PTS nesou flags:
 *      byte 0   ...
 *     0CK..... ........
 *     ^^^
 *     |||
 *     || `- (K)ey frame         (bit 61)
 *     | `-- (C)onfig packet     (bit 62) - první paket je vždy CONFIG.
 *      `--- (M)edia packet flag (bit 63) - 0 pro media, 1 pro session. Audio je vždy 0.
 *
 * Pokud budeš aktualizovat scrcpy-server, ověř v jeho zdrojácích, že se tyto
 * bitmasky a pořadí v writeFrameMeta() nezměnily:
 *   https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/device/Streamer.java
 */
class ScrcpyClient(
    private val inputPfd: ParcelFileDescriptor,
    private val expectedCodec: ScrcpyAudioCodec,
    private val listener: AudioPacketListener
) : Closeable {

    companion object {
        private const val MEDIA_PACKET_FLAG     = 1L shl 63
        private const val PACKET_FLAG_CONFIG    = 1L shl 62
        private const val PACKET_FLAG_KEY_FRAME = 1L shl 61
        private const val MAX_PACKET_SIZE = 1 * 1024 * 1024 // 1 MiB pojistka proti misalignmentu
    }

    interface AudioPacketListener {
        fun onMetadataReceived(codec: ScrcpyAudioCodec)
        fun onAudioPacket(packet: AudioPacket)
        fun onStreamEnd(error: String?)
    }

    data class AudioPacket(
        val pts: Long,
        val isConfigPacket: Boolean,
        val data: ByteArray
    )

    private data class AudioEnvelope(
        val rawPtsAndFlags: Long,
        val pts: Long,
        val isMedia: Boolean,
        val isConfig: Boolean,
        val isKeyFrame: Boolean,
        val payloadSize: Int
    )

    @Volatile
    private var running = false

    /**
     * Blokující čtení streamu, dokud se nezavolá [stop] nebo nenastane EOF/chyba.
     * Musí se volat na background threadu/coroutine.
     */
    fun start() {
        running = true
        val inputStream = DataInputStream(
            BufferedInputStream(FileInputStream(inputPfd.fileDescriptor))
        )
        try {
            val receivedFourCC = inputStream.readInt()
            val resolvedCodec = ScrcpyAudioCodec.fromFourCC(receivedFourCC)
            RecorderLog.d(TAG, "Codec FourCC: received=0x${receivedFourCC.toString(16)} resolved=${resolvedCodec.cliKey} expected=${expectedCodec.cliKey}")

            if (resolvedCodec != expectedCodec) {
                RecorderLog.w(TAG, "Codec mismatch: requested ${expectedCodec.cliKey} but server sent ${resolvedCodec.cliKey}")
            }

            listener.onMetadataReceived(resolvedCodec)

            var hasReceivedConfig = false
            while (running) {
                val header = readPacketHeader(inputStream)

                if (!hasReceivedConfig) {
                    if (!header.isConfig) {
                        throw java.io.IOException(
                            "Protocol error: the first packet must be a CONFIG packet, but a regular packet arrived! " +
                            "(isMedia=${header.isMedia}, isConfig=${header.isConfig}, isKeyFrame=${header.isKeyFrame})"
                        )
                    }
                    hasReceivedConfig = true
                }

                if (!header.isMedia) {
                    RecorderLog.w(TAG, "Unexpected session packet on the audio stream! flags=0x${header.rawPtsAndFlags.toString(16)}")
                }

                if (header.payloadSize <= 0 || header.payloadSize > MAX_PACKET_SIZE) {
                    throw java.io.IOException(
                        "Implausible packet size ${header.payloadSize} after PTS/flags 0x${header.rawPtsAndFlags.toString(16)} - " +
                        "probable stream misalignment."
                    )
                }

                val payloadBytes = ByteArray(header.payloadSize)
                inputStream.readFully(payloadBytes)

                listener.onAudioPacket(
                    AudioPacket(
                        pts            = header.pts,
                        isConfigPacket = header.isConfig,
                        data           = payloadBytes
                    )
                )
            }
        } catch (e: EOFException) {
            RecorderLog.d(TAG, "Stream fully read (EOF), nothing left to parse.")
            listener.onStreamEnd(null)
        } catch (e: Exception) {
            RecorderLog.e(TAG, "Stream ended with error: ${e.message}", e)
            listener.onStreamEnd(e.message)
        } finally {
            runCatching { inputStream.close() }
        }
    }

    fun stop() {
        running = false
    }

    override fun close() {
        stop()
        runCatching { inputPfd.close() }
    }

    private fun readPacketHeader(stream: DataInputStream): AudioEnvelope {
        val ptsAndFlags = stream.readLong()
        val payloadSize = stream.readInt()
        return AudioEnvelope(
            rawPtsAndFlags = ptsAndFlags,
            pts            = ptsAndFlags and (MEDIA_PACKET_FLAG or PACKET_FLAG_CONFIG or PACKET_FLAG_KEY_FRAME).inv(),
            isMedia        = (ptsAndFlags and MEDIA_PACKET_FLAG) == 0L,
            isConfig       = (ptsAndFlags and PACKET_FLAG_CONFIG)    != 0L,
            isKeyFrame     = (ptsAndFlags and PACKET_FLAG_KEY_FRAME) != 0L,
            payloadSize    = payloadSize
        )
    }
}
