package cz.mts.phone.recorder

import android.os.ParcelFileDescriptor
import cz.mts.phone.recorder.scrcpy.ScrcpyAudioCodec
import cz.mts.phone.recorder.scrcpy.ScrcpyAudioSource
import cz.mts.phone.recorder.scrcpy.ScrcpyConfig
import cz.mts.phone.recorder.scrcpy.ServerExtractor
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "ShellAudioPipeline"

/**
 * ShellAudioPipeline řídí audio-capture pipeline v shell procesu.
 * Adaptováno beze změny logiky ze ShizuCallRecorder.
 *
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │  Shell Process (UID 2000)                                    │
 *   │  ShellService --> ShellAudioPipeline (tato třída)             │
 *   │    ├── spustí  scrcpy-server (app_process)                    │
 *   │    │     └── připojí se na  LocalServerSocket                 │
 *   │    ├── AudioRelayCoroutine (socket → pipe)                     │
 *   │    │     Pipe[1] write-end (zůstává v Shell)                  │
 *   │    │     Pipe[0] read-end  ───────────────► App Process       │
 *   │    ├── LogConsumerCoroutine   (drain stdout)                  │
 *   │    └── ProcessMonitorCoroutine (čeká na exit)                 │
 *   └─────────────────────────────────────────────────────────────┘
 */
class ShellAudioPipeline {
    private companion object {
        const val RELAY_BUFFER_SIZE = 32 * 1024
        const val PROCESS_STOP_GRACE_PERIOD_SEC = 2L
    }

    private val isRecordingActive = AtomicBoolean(false)

    private var scrcpyProcess: Process? = null
    private var serverSocket: android.net.LocalServerSocket? = null
    private var clientConnection: android.net.LocalSocket? = null
    private var audioWriteEnd: ParcelFileDescriptor? = null

    private var shellScope: CoroutineScope? = null
    private var audioPipeRelayJob: Job? = null

    /**
     * Spustí audio-capture pipeline.
     *
     * @param audioSource   scrcpy audio_source (v naší appce vždy "voice-call")
     * @param audioCodec    scrcpy audio_codec (v naší appce vždy "opus")
     * @param audioBitRate  bitrate v bps (v naší appce vždy 16000)
     * @param serverPath    absolutní cesta k scrcpy-server.jar
     * @return read-end pipe, nebo null při selhání
     */
    fun startCapture(
        audioSource: String,
        audioCodec: String,
        audioBitRate: Int,
        serverPath: String
    ): ParcelFileDescriptor? {
        if (isRecordingActive.get()) {
            RecorderLog.w(TAG, "startCapture() rejected: a session is already running")
            return null
        }

        try {
            RecorderLog.i(TAG, "Initializing recording pipeline...")

            val serverJarFile = File(serverPath)
            if (!serverJarFile.exists() || !ServerExtractor.verifyServerHash(serverJarFile)) {
                RecorderLog.w(TAG, "Server JAR missing or SHA-256 mismatch at $serverPath - aborting")
                return null
            }

            val pipe = ParcelFileDescriptor.createPipe()
            val pipeReadEnd  = pipe[0]
            val pipeWriteEnd = pipe[1]
            audioWriteEnd = pipeWriteEnd

            val socketName = ScrcpyConfig.getRandomSocketName()
            val serverFullSocketName = ScrcpyConfig.SERVER_SOCKET_NAME_PREFIX + socketName
            serverSocket = android.net.LocalServerSocket(serverFullSocketName)
            RecorderLog.d(TAG, "Listening on abstract socket '$serverFullSocketName'")

            shellScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            spawnAudioRelayCoroutine()

            val audioSourceEnum = ScrcpyAudioSource.fromKey(audioSource)
            val audioCodecEnum  = ScrcpyAudioCodec.fromKey(audioCodec)
            val serverArgs    = ScrcpyConfig.buildServerArgs(socketName, audioSourceEnum, audioCodecEnum, audioBitRate)
            val launchCommand = mutableListOf("app_process", "/", ScrcpyConfig.SERVER_MAIN_CLASS)
            launchCommand.addAll(serverArgs)

            val scrcpyBuilder = ProcessBuilder(launchCommand).apply {
                environment()["CLASSPATH"] = serverPath
                redirectErrorStream(true)
            }
            scrcpyProcess = scrcpyBuilder.start()
            isRecordingActive.set(true)
            RecorderLog.i(TAG, "scrcpy-server started successfully")

            spawnLogConsumerCoroutine(scrcpyProcess!!)
            spawnProcessMonitorCoroutine(scrcpyProcess!!)

            RecorderLog.i(TAG, "Recording pipeline created. Returning pipe read end.")
            return pipeReadEnd
        } catch (e: Exception) {
            RecorderLog.e(TAG, "Critical error while starting pipeline: ${e.message}", e)
            stopCapture()
            return null
        }
    }

    /**
     * Zastaví pipeline a uvolní zdroje. Pořadí je záměrné:
     * 1. Vypnout flag, aby relay smyčka skončila na dalším cyklu.
     * 2. Zabít scrcpy-server, dát mu chvíli na doflushnutí posledních bajtů.
     * 3. Zrušit coroutine scope (přeruší blokující I/O v relay/log/monitor).
     * 4. Zavřít client connection, aby se relay read() odblokoval.
     * 5. Zavřít server socket.
     * 6. Zavřít pipe write-end AŽ NAKONEC, ať scrcpy-server stihne dopsat.
     */
    fun stopCapture() {
        if (!isRecordingActive.compareAndSet(true, false)) {
            RecorderLog.w(TAG, "stopCapture() called but no session is running - skipping")
            return
        }

        RecorderLog.i(TAG, "Stopping scrcpy-server process...")
        runCatching { scrcpyProcess?.destroy() }

        try {
            scrcpyProcess?.waitFor(PROCESS_STOP_GRACE_PERIOD_SEC, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            RecorderLog.w(TAG, "Interrupted while waiting for scrcpy-server to exit: ${e.message}")
        }

        RecorderLog.d(TAG, "Waiting for relay coroutine to copy late bytes...")
        runCatching {
            runBlocking {
                withTimeoutOrNull(2000L) {
                    audioPipeRelayJob?.join()
                }
            }
        }

        RecorderLog.d(TAG, "Cancelling all jobs in the shell coroutine scope...")
        runCatching { shellScope?.cancel() }

        RecorderLog.d(TAG, "Closing client socket connection...")
        runCatching { clientConnection?.close() }

        RecorderLog.d(TAG, "Closing server socket...")
        runCatching { serverSocket?.close() }

        RecorderLog.d(TAG, "Closing audio pipe write end...")
        runCatching { audioWriteEnd?.close() }

        scrcpyProcess = null
        clientConnection  = null
        serverSocket      = null
        audioWriteEnd     = null
        shellScope        = null
        audioPipeRelayJob = null

        RecorderLog.i(TAG, "Recording pipeline stopped and all resources released")
    }

    private fun spawnAudioRelayCoroutine() {
        audioPipeRelayJob = shellScope?.launch(Dispatchers.IO) {
            try {
                RecorderLog.d(TAG, "AudioRelayCoroutine: waiting for scrcpy-server connection...")
                val connection = serverSocket?.accept() ?: run {
                    RecorderLog.w(TAG, "AudioRelayCoroutine: server socket was null or closed")
                    return@launch
                }
                clientConnection = connection
                RecorderLog.i(TAG, "AudioRelayCoroutine: scrcpy-server connected")

                val sourceStream = connection.inputStream
                val destinationStream = ParcelFileDescriptor.AutoCloseOutputStream(audioWriteEnd)

                val buffer = ByteArray(RELAY_BUFFER_SIZE)

                while (isActive) {
                    val bytesRead = sourceStream.read(buffer)
                    if (bytesRead == -1) {
                        RecorderLog.d(TAG, "AudioRelayCoroutine: socket EOF - scrcpy-server disconnected")
                        break
                    }
                    destinationStream.write(buffer, 0, bytesRead)
                }
            } catch (e: IOException) {
                if (isRecordingActive.get()) {
                    RecorderLog.e(TAG, "AudioRelayCoroutine: unexpected I/O error: ${e.message}", e)
                } else {
                    RecorderLog.d(TAG, "AudioRelayCoroutine: I/O error during shutdown (expected): ${e.message}")
                }
            } finally {
                RecorderLog.d(TAG, "AudioRelayCoroutine finished")
                stopCapture()
            }
        }
    }

    private fun spawnLogConsumerCoroutine(process: Process) {
        shellScope?.launch(Dispatchers.IO) {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    var line = reader.readLine()
                    while (isActive && line != null) {
                        RecorderLog.i(TAG, "[scrcpy-server] $line")
                        line = reader.readLine()
                    }
                }
            } catch (_: InterruptedIOException) {
                RecorderLog.d(TAG, "LogConsumerCoroutine: interrupted (expected during shutdown)")
            } catch (e: IOException) {
                RecorderLog.e(TAG, "LogConsumerCoroutine: I/O error: ${e.message}", e)
            } finally {
                RecorderLog.d(TAG, "LogConsumerCoroutine finished")
            }
        }
    }

    private fun spawnProcessMonitorCoroutine(process: Process) {
        shellScope?.launch(Dispatchers.IO) {
            try {
                val exitCode = process.waitFor()
                if (exitCode != 0 && isRecordingActive.get()) {
                    RecorderLog.e(TAG, "ProcessMonitorCoroutine: scrcpy-server crashed (exit code $exitCode)")
                    stopCapture()
                } else {
                    RecorderLog.i(TAG, "ProcessMonitorCoroutine: scrcpy-server exited normally (code $exitCode)")
                }
            } catch (_: InterruptedException) {
                RecorderLog.d(TAG, "ProcessMonitorCoroutine: interrupted (expected during shutdown)")
            } finally {
                RecorderLog.d(TAG, "ProcessMonitorCoroutine finished")
            }
        }
    }
}
