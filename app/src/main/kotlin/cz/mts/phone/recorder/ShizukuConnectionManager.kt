package cz.mts.phone.recorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "ShizukuConnectionManager"

/**
 * Řídí interakce se Shizuku manager appkou a jejím server procesem.
 * Adaptováno ze ShizuCallRecorder (integrations.shizuku.ShizukuConnectionManager),
 * zjednodušeno o remote logging (setLogCallback).
 *
 * "Service"/"ShellService" = náš kód běžící se zvýšenými právy ([ShellService]).
 * "Server" = Shizuku ADB server proces (UID 2000/0), který ShellService hostuje.
 *
 * @param context Application context (ne Activity, kvůli leakům).
 * @param onBinderDied Volitelný callback při NEČEKANÉ ztrátě spojení se serverem.
 */
class ShizukuConnectionManager(
    private val context: Context,
    private val onBinderDied: () -> Unit = {}
) {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 204846

        fun isAvailable(): Boolean {
            return try {
                Shizuku.pingBinder()
            } catch (e: Exception) {
                RecorderLog.w(TAG, "Shizuku unavailable: ${e.message}", e)
                false
            }
        }

        fun hasPermission(): Boolean {
            return try {
                if (isAvailable()) {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } else {
                    false
                }
            } catch (e: Exception) {
                RecorderLog.e(TAG, "Error while checking Shizuku permission", e)
                false
            }
        }

        fun requestPermission() {
            if (!hasPermission()) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        }

        /** Resolve balíček Shizuku manager appky přes jeho deklarované permission. */
        fun getPackageName(context: Context): String? {
            return runCatching {
                context.packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0)
            }.getOrNull()?.packageName
        }

        /** Nastartuje Shizuku ADB server přes broadcast. Bezpečné volat i když už běží. */
        fun startServer(context: Context, authKey: String) {
            try {
                if (isAvailable()) {
                    RecorderLog.i(TAG, "Shizuku server already running, start broadcast not needed")
                    return
                }
                val packageName = getPackageName(context)
                    ?: throw IllegalStateException("Shizuku manager package not found, cannot start the server")

                val intent = Intent("moe.shizuku.privileged.api.START").apply {
                    setPackage(packageName)
                    putExtra("auth", authKey)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                context.sendBroadcast(intent)
                RecorderLog.i(TAG, "Sent Shizuku server start broadcast to $packageName")
            } catch (e: Exception) {
                RecorderLog.e(TAG, "Failed to send the start broadcast", e)
            }
        }

        /** Zastaví Shizuku ADB server přes broadcast. */
        fun stopServer(context: Context, authKey: String) {
            try {
                if (!isAvailable()) {
                    RecorderLog.i(TAG, "Shizuku server already stopped, stop broadcast not needed")
                    return
                }
                val packageName = getPackageName(context)
                    ?: throw IllegalStateException("Shizuku manager package not found, cannot stop the server")

                val intent = Intent("moe.shizuku.privileged.api.STOP").apply {
                    setPackage(packageName)
                    putExtra("auth", authKey)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                context.sendBroadcast(intent)
                RecorderLog.i(TAG, "Sent Shizuku server stop broadcast to $packageName")
            } catch (e: Exception) {
                RecorderLog.e(TAG, "Failed to send the stop broadcast", e)
            }
        }

        /** Čeká, až se Shizuku server zpřístupní (po startServer). */
        suspend fun waitForServer(timeoutMillis: Long = 10_000, pollIntervalMillis: Long = 200): Boolean {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                if (isAvailable()) return true
                delay(pollIntervalMillis)
            }
            RecorderLog.w(TAG, "Timed out waiting for the Shizuku server after ${timeoutMillis}ms")
            return false
        }

        /**
         * Stáhne záznamy logu ze shell procesu a sloučí je do [RecorderLog] v appce.
         * Blokující binder volání - NEVOLAT z main threadu.
         */
        fun pullShellLogs(service: IShellService?) {
            if (service == null) return
            try {
                val lines = service.drainLogs()
                if (!lines.isNullOrEmpty()) {
                    RecorderLog.importFromTransfer(lines)
                }
            } catch (e: Exception) {
                RecorderLog.w(TAG, "Could not pull logs from the shell process: ${e.message}")
            }
        }

        /**
         * Otestuje, že s daným auth klíčem lze reálně spustit Shizuku server A
         * úspěšně se na něj přes ShellService připojit. Po testu server VŽDY
         * zase vypne (i při chybě), aby netekl mezi hovory.
         *
         * Použij při ukládání klíče v nastavení - vrátí Result.success, jen
         * pokud celý start→bind→stop cyklus proběhl bez chyby.
         */
        suspend fun testConnection(context: Context, authKey: String): Result<Unit> {
            if (authKey.isBlank()) {
                RecorderLog.w(TAG, "testConnection: auth key is empty")
                return Result.failure(IllegalArgumentException("Auth key is empty"))
            }

            RecorderLog.i(TAG, "testConnection: started")
            var connMgr: ShizukuConnectionManager? = null
            return try {
                if (!isAvailable()) {
                    RecorderLog.i(TAG, "testConnection: starting Shizuku server")
                    startServer(context, authKey)
                    if (!waitForServer(timeoutMillis = 8_000)) {
                        RecorderLog.w(TAG, "testConnection: Shizuku server did not start within 8s")
                        return Result.failure(IllegalStateException("Shizuku server did not start within 8s - check the auth key"))
                    }
                }

                RecorderLog.i(TAG, "testConnection: Shizuku server is up, binding ShellService")
                connMgr = ShizukuConnectionManager(context)
                val service = withTimeoutOrNull(8_000) { connMgr.getShellService() }
                if (service == null) {
                    RecorderLog.w(TAG, "testConnection: ShellService connection timed out (8s)")
                    Result.failure(IllegalStateException("Could not connect to ShellService (8s timeout)"))
                } else {
                    RecorderLog.i(TAG, "testConnection: ShellService connected")
                    withContext(Dispatchers.IO) { pullShellLogs(service) }
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                RecorderLog.e(TAG, "testConnection: failed", e)
                Result.failure(e)
            } finally {
                runCatching { connMgr?.unbind() }
                // Test skončil, server nemá zůstat běžet mezi hovory.
                stopServer(context, authKey)
                RecorderLog.i(TAG, "testConnection: finished")
            }
        }
    }

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
            // daemon=false: proces skončí, když skončí appka - nechceme ho nechávat viset.
            .daemon(false)
            .processNameSuffix("recorder")
            .debuggable(false)
            .version(version)
    }

    private var serviceConnection: ServiceConnection? = null

    /**
     * Vrátí [IShellService] proxy, s bindem (a případně permission requestem), pokud
     * ještě nejsme připojení.
     *
     * @throws IllegalStateException pokud Shizuku neběží nebo bind/spojení selže.
     * @throws SecurityException pokud je permission zamítnuté.
     */
    suspend fun getShellService(): IShellService = suspendCancellableCoroutine { continuation ->
        if (!isAvailable()) {
            continuation.resumeWithException(IllegalStateException("Shizuku is not running"))
            return@suspendCancellableCoroutine
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
                if (binder != null) {
                    val proxy = IShellService.Stub.asInterface(binder)
                    RecorderLog.i(TAG, "ShellService connected successfully")
                    if (continuation.isActive) continuation.resume(proxy)
                } else {
                    val e = IllegalStateException("Shizuku returned a null binder")
                    RecorderLog.e(TAG, "Service connected with a null binder", e)
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                RecorderLog.d(TAG, "ShellService disconnected unexpectedly")
                unbind()
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("Shizuku service disconnected unexpectedly"))
                } else {
                    onBinderDied()
                }
            }
        }
        this.serviceConnection = connection

        fun bindServiceInternal() {
            try {
                RecorderLog.i(TAG, "Binding ShellService...")
                Shizuku.bindUserService(userServiceArgs, connection)
            } catch (e: Exception) {
                RecorderLog.e(TAG, "Bind failed", e)
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }

        val permissionListener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        bindServiceInternal()
                    } else {
                        RecorderLog.w(TAG, "Shizuku permission denied by the user")
                        if (continuation.isActive) {
                            continuation.resumeWithException(SecurityException("Shizuku permission denied by the user"))
                        }
                    }
                }
            }
        }

        if (hasPermission()) {
            bindServiceInternal()
        } else {
            RecorderLog.i(TAG, "Requesting Shizuku permission")
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }

        continuation.invokeOnCancellation {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        }
    }

    /** Odpojí ShellService a vyresetuje interní stav. */
    fun unbind() {
        val conn = serviceConnection ?: return
        try {
            if (isAvailable()) {
                Shizuku.unbindUserService(userServiceArgs, conn, false)
                RecorderLog.i(TAG, "ShellService unbound")
            }
        } catch (e: Exception) {
            RecorderLog.e(TAG, "Error while unbinding ShellService", e)
        }
        serviceConnection = null
    }
}
