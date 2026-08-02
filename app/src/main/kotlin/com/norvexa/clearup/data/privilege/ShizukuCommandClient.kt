package com.norvexa.clearup.data.privilege

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.UserHandle
import com.norvexa.clearup.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

class ShizukuCommandClient(
    context: Context,
    private val auditStore: ShizukuAuditStore,
) {
    private val appContext = context.applicationContext
    private val connectionLock = Any()

    @Volatile
    private var service: IShizukuCommandService? = null
    private var pendingConnection: CompletableDeferred<IShizukuCommandService>? = null

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            appContext.packageName,
            ShizukuCommandService::class.java.name,
        ),
    )
        .daemon(false)
        .processNameSuffix("privileged")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val connected = IShizukuCommandService.Stub.asInterface(binder)
            synchronized(connectionLock) {
                service = connected
                pendingConnection?.complete(connected)
                pendingConnection = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            failPendingConnection("Shizuku UserService disconnected")
        }

        override fun onBindingDied(name: ComponentName) {
            failPendingConnection("Shizuku UserService binding died")
        }

        override fun onNullBinding(name: ComponentName) {
            failPendingConnection("Shizuku UserService returned a null binding")
        }
    }

    fun isReady(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    suspend fun clearCache(packageName: String): ShellResult = execute(
        operation = ShizukuCommandPolicy.CLEAR_CACHE,
        packageName = packageName,
    )

    suspend fun forceStop(packageName: String): ShellResult = execute(
        operation = ShizukuCommandPolicy.FORCE_STOP,
        packageName = packageName,
    )

    suspend fun setFrozen(packageName: String, frozen: Boolean): ShellResult = execute(
        operation = if (frozen) {
            ShizukuCommandPolicy.FREEZE
        } else {
            ShizukuCommandPolicy.UNFREEZE
        },
        packageName = packageName,
    )

    private suspend fun execute(
        operation: String,
        packageName: String,
    ): ShellResult = withContext(Dispatchers.IO) {
        require(ShizukuCommandPolicy.isValidPackageName(packageName)) {
            "Invalid package name"
        }
        if (!isReady()) {
            return@withContext ShellResult(
                exitCode = -1,
                output = "",
                error = "Shizuku недоступен или разрешение не выдано",
            )
        }

        val result = runCatching {
            val remote = awaitService()
            parseResponse(
                remote.execute(
                    operation,
                    packageName,
                    UserHandle.myUserId(),
                ),
            )
        }.getOrElse { error ->
            synchronized(connectionLock) {
                service = null
            }
            ShellResult(
                exitCode = -1,
                output = "",
                error = error.message ?: "Ошибка Shizuku UserService",
            )
        }
        auditStore.record(
            action = operation,
            target = packageName,
            result = result,
        )
        result
    }

    private suspend fun awaitService(): IShizukuCommandService {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }

        val (deferred, shouldBind) = synchronized(connectionLock) {
            service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
            pendingConnection?.let { existing ->
                existing to false
            } ?: CompletableDeferred<IShizukuCommandService>().let { created ->
                pendingConnection = created
                created to true
            }
        }

        if (shouldBind) {
            runCatching {
                Shizuku.bindUserService(serviceArgs, connection)
            }.onFailure { error ->
                synchronized(connectionLock) {
                    if (pendingConnection === deferred) {
                        pendingConnection = null
                        deferred.completeExceptionally(error)
                    }
                }
            }
        }

        return try {
            withTimeout(CONNECTION_TIMEOUT_MS) {
                deferred.await()
            }
        } catch (error: Throwable) {
            synchronized(connectionLock) {
                if (pendingConnection === deferred) {
                    pendingConnection = null
                }
            }
            throw error
        }
    }

    private fun failPendingConnection(message: String) {
        synchronized(connectionLock) {
            service = null
            pendingConnection?.completeExceptionally(IllegalStateException(message))
            pendingConnection = null
        }
    }

    private fun parseResponse(response: Array<String>?): ShellResult {
        if (response == null || response.size < 3) {
            return ShellResult(-1, "", "Некорректный ответ Shizuku UserService")
        }
        return ShellResult(
            exitCode = response[0].toIntOrNull() ?: -1,
            output = response[1],
            error = response[2],
        )
    }

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 10_000L
    }
}
