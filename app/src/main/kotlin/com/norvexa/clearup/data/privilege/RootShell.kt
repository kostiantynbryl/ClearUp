package com.norvexa.clearup.data.privilege

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShellResult(
    val exitCode: Int,
    val output: String,
    val error: String,
) {
    val success: Boolean get() = exitCode == 0
}

class RootShell {
    private val packagePattern = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")

    suspend fun isAvailable(): Boolean = run("id", timeoutSeconds = 3).let {
        it.success && it.output.contains("uid=0")
    }

    suspend fun clearCache(packageName: String): ShellResult {
        requireValidPackage(packageName)
        val roots = listOf("/data/user/0", "/data/data")
        val targets = roots.flatMap { base ->
            listOf("$base/$packageName/cache", "$base/$packageName/code_cache")
        }
        val command = targets.joinToString("; ") { path ->
            "if [ -d '$path' ]; then find '$path' -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +; fi"
        }
        return run(command)
    }

    suspend fun forceStop(packageName: String): ShellResult {
        requireValidPackage(packageName)
        return run("am force-stop --user current '$packageName'")
    }

    suspend fun setFrozen(packageName: String, frozen: Boolean): ShellResult {
        requireValidPackage(packageName)
        val command = if (frozen) {
            "pm disable-user --user 0 '$packageName'"
        } else {
            "pm enable --user 0 '$packageName'"
        }
        return run(command)
    }

    private fun requireValidPackage(packageName: String) {
        require(packagePattern.matches(packageName)) { "Invalid package name" }
    }

    private suspend fun run(
        command: String,
        timeoutSeconds: Long = 20,
    ): ShellResult = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("su", "-c", command).start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching ShellResult(-1, "", "Root command timed out")
            }
            ShellResult(
                exitCode = process.exitValue(),
                output = process.inputStream.bufferedReader().use { it.readText() },
                error = process.errorStream.bufferedReader().use { it.readText() },
            )
        }.getOrElse { error ->
            ShellResult(-1, "", error.message.orEmpty())
        }
    }
}
