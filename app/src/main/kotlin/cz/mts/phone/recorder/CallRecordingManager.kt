package cz.mts.phone.recorder

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import cz.mts.phone.recorder.scrcpy.*
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "CallRecordingManager"

/**
 * Singleton řídící celý životní cyklus nahrávání hovoru přes scrcpy-server/Shizuku.
 *
 * Natvrdo: zdroj VOICE_CALL, kodek OPUS, 16000 bps (viz konstanty níže) - nic
 * jiného appka nenabízí, ale ScrcpyAudioSource/ScrcpyAudioCodec enumy mají
 * i ostatní hodnoty pro budoucí použití.
 *
 * Shizuku lifecycle podle zadání: server se startuje AŽ při stisku nahrávacího
 * tlačítka (přes uložený auth klíč) a KOMPLETNĚ se vypne po skončení nahrávání
 * (unbind + stop broadcast) - neběží trvale na pozadí.
 *
 * Veškeré hlášení jde do [RecorderLog] (ne do systémového logcatu).
 */
object CallRecordingManager {

    private val HARDCODED_SOURCE = ScrcpyAudioSource.VOICE_CALL
    private val HARDCODED_CODEC = ScrcpyAudioCodec.OPUS
    private const val HARDCODED_BITRATE = 16000

    private var appContext: Context? = null
    private var connectionManager: ShizukuConnectionManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Poslední chyba, pro debug výpis v CallActivity (iSaveDebugMode). */
    var lastError: String? = null
        private set

    @Volatile private var recordingActive = false
    private var shellService: IShellService? = null
    private var scrcpyClient: ScrcpyClient? = null
    private var scrcpyMuxer: ScrcpyAudioMuxer? = null
    private var outputPfd: ParcelFileDescriptor? = null
    private var recordingJob: Job? = null
    private var currentFile: File? = null
    private var authKeyInUse: String = ""

    /** Zavolat jednou, ideálně z CallService.onCreate(). Bezpečné volat opakovaně. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
    }

    fun isRecording(): Boolean = recordingActive

    /**
     * Spustí celý flow: Shizuku server (pokud neběží) -> bind ShellService ->
     * scrcpy-server -> muxer do souboru. onResult(true) přijde, jakmile
     * skutečně začneme dostávat audio pakety (ne jen po odeslání requestu).
     *
     * Vrací soubor, kam se bude nahrávat (existuje hned, i než start doběhne).
     */
    fun startRecording(callLabel: String, onResult: (started: Boolean) -> Unit): File? {
        val context = appContext ?: run {
            lastError = "appContext is null - init() was not called"
            RecorderLog.e(TAG, lastError!!)
            onResult(false)
            return null
        }
        if (recordingActive) {
            RecorderLog.d(TAG, "startRecording(): already recording, ignoring")
            onResult(true)
            return currentFile
        }
        lastError = null

        val authKey = ShizukuAuthKeyStore.getAuthKey(context)
        if (authKey.isBlank()) {
            lastError = "Shizuku auth key is not set in Settings"
            RecorderLog.e(TAG, lastError!!)
            onResult(false)
            return null
        }
        authKeyInUse = authKey

        val safeLabel = callLabel.ifBlank { "call" }.filter { it.isLetterOrDigit() }
        val dir = File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(Date())
        val file = File(dir, "${safeLabel}_${stamp}${HARDCODED_CODEC.containerExtension}")

        RecorderLog.i(TAG, "startRecording(): target file ${file.name}")

        val connMgr = connectionManager ?: ShizukuConnectionManager(context) { onBinderDied() }.also { connectionManager = it }

        recordingJob = scope.launch {
            try {
                if (!ShizukuConnectionManager.isAvailable()) {
                    RecorderLog.i(TAG, "Shizuku server is not running, starting it")
                    ShizukuConnectionManager.startServer(context, authKey)
                    if (!ShizukuConnectionManager.waitForServer()) {
                        fail(context, connMgr, "Shizuku server did not start within 10s - check the auth key in Settings", onResult)
                        return@launch
                    }
                }

                val service = try {
                    connMgr.getShellService()
                } catch (e: Exception) {
                    fail(context, connMgr, "Failed to connect to ShellService: ${e.javaClass.simpleName}: ${e.message}", onResult)
                    return@launch
                }
                shellService = service

                val serverPath = ScrcpyConfig.getServerPath(context)
                val serverReady = withContext(Dispatchers.IO) { ServerExtractor.ensureServerFile(context, serverPath) }
                if (!serverReady) {
                    fail(context, connMgr, "scrcpy-server.jar is missing or its SHA-256 does not match", onResult)
                    return@launch
                }

                val pipeReadEnd = try {
                    service.startRecording(
                        HARDCODED_SOURCE.cliKey,
                        HARDCODED_CODEC.cliKey,
                        HARDCODED_BITRATE,
                        serverPath
                    )
                } catch (e: Exception) {
                    fail(context, connMgr, "startRecording on ShellService failed: ${e.javaClass.simpleName}: ${e.message}", onResult)
                    return@launch
                }

                if (pipeReadEnd == null) {
                    fail(context, connMgr, "ShellService returned a null pipe (see the shell process entries in the log, tag ShellAudioPipeline)", onResult)
                    return@launch
                }

                val pfd = ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE
                )
                outputPfd = pfd

                val muxer = ScrcpyAudioMuxer(pfd.fileDescriptor, file.name)
                muxer.initialize(HARDCODED_CODEC)
                scrcpyMuxer = muxer

                val client = ScrcpyClient(
                    inputPfd = pipeReadEnd,
                    expectedCodec = HARDCODED_CODEC,
                    listener = object : ScrcpyClient.AudioPacketListener {
                        override fun onMetadataReceived(codec: ScrcpyAudioCodec) {
                            muxer.initialize(codec) // no-op pokud už inicializováno
                        }
                        override fun onAudioPacket(packet: ScrcpyClient.AudioPacket) {
                            muxer.writePacket(packet, HARDCODED_CODEC)
                        }
                        override fun onStreamEnd(error: String?) {
                            if (error != null) RecorderLog.w(TAG, "Audio stream ended with error: $error")
                        }
                    }
                )
                scrcpyClient = client
                recordingActive = true
                RecorderLog.i(TAG, "Recording started")
                mainHandler.post { onResult(true) }

                // Blokující čtecí smyčka - běží po celou dobu nahrávání, dokud
                // nepřijde EOF (viz stopRecording) nebo chyba.
                client.start()

            } catch (e: Throwable) {
                fail(context, connMgr, "Unexpected error during start: ${e.javaClass.simpleName}: ${e.message}", onResult)
            }
        }

        return file
    }

    /**
     * Zastaví nahrávání: pošle stop shell procesu, počká na doflushnutí (timeout
     * 3s), stáhne logy ze shell procesu, zavře muxer/soubor, odpojí ShellService
     * a KOMPLETNĚ vypne Shizuku server (broadcast stop) - podle zadání se nemá
     * nechávat běžet mezi hovory.
     */
    fun stopRecording() {
        if (!recordingActive) return
        recordingActive = false
        RecorderLog.i(TAG, "stopRecording(): stop requested")

        val client = scrcpyClient
        val service = shellService
        val job = recordingJob
        val muxer = scrcpyMuxer
        val pfd = outputPfd
        val connMgr = connectionManager
        val context = appContext
        val authKey = authKeyInUse

        scope.launch {
            runCatching { service?.stopRecording() } // spustí teardown na shell straně
            withTimeoutOrNull(3_000L) { job?.join() } // počkej na EOF z pipe
            runCatching { client?.close() } // fallback, kdyby EOF nepřišel včas

            // Shell proces ještě žije - stáhni jeho log dřív, než ho vypneme.
            ShizukuConnectionManager.pullShellLogs(service)

            runCatching { muxer?.close() }
            runCatching { pfd?.close() }
            runCatching { connMgr?.unbind() }
            if (context != null) {
                ShizukuConnectionManager.stopServer(context, authKey)
            }

            shellService = null
            scrcpyClient = null
            scrcpyMuxer = null
            outputPfd = null
            recordingJob = null
            RecorderLog.i(TAG, "stopRecording(): cleanup finished")
        }
    }

    /** Best-effort cleanup + hlášení chyby, voláno kdykoliv start flow selže (vždy na IO threadu). */
    private fun fail(context: Context, connMgr: ShizukuConnectionManager, message: String, onResult: (Boolean) -> Unit) {
        RecorderLog.e(TAG, message)
        lastError = message
        recordingActive = false
        // Shell strana často říká, PROČ to selhalo - stáhni její log, dokud proces žije.
        ShizukuConnectionManager.pullShellLogs(shellService)
        runCatching { connMgr.unbind() }
        ShizukuConnectionManager.stopServer(context, authKeyInUse)
        shellService = null
        scrcpyClient = null
        scrcpyMuxer = null
        runCatching { outputPfd?.close() }
        outputPfd = null
        mainHandler.post { onResult(false) }
    }

    /** Voláno, když se ShellService/Shizuku spojení nečekaně ztratí uprostřed nahrávání. */
    private fun onBinderDied() {
        RecorderLog.w(TAG, "Shizuku binder died in the middle of recording (shell process log entries are lost)")
        lastError = "Shizuku connection died unexpectedly (binder died)"
        recordingActive = false
        runCatching { scrcpyMuxer?.close() }
        runCatching { outputPfd?.close() }
        shellService = null
        scrcpyClient = null
        scrcpyMuxer = null
        outputPfd = null
        recordingJob = null
    }
}
