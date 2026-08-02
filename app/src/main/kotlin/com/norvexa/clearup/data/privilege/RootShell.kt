package com.norvexa.clearup.data.privilege

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class ShellResult(
    val exitCode: Int,
    val output: String,
    val error: String,
) {
    val success: Boolean get() = exitCode == 0
}

class RootShell(
    private val auditStore: RootAuditStore? = null,
) {
    suspend fun isAvailable(): Boolean = execute(
        action = "CHECK_ROOT",
        target = "su",
        command = "id",
        timeoutSeconds = 3,
        audit = false,
    ).let { result ->
        result.success && result.output.contains("uid=0")
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
        return execute("CLEAR_CACHE", packageName, command)
    }

    suspend fun forceStop(packageName: String): ShellResult {
        requireValidPackage(packageName)
        return execute(
            action = "FORCE_STOP",
            target = packageName,
            command = "am force-stop --user current '$packageName'",
        )
    }

    suspend fun setFrozen(packageName: String, frozen: Boolean): ShellResult {
        requireValidPackage(packageName)
        val command = if (frozen) {
            "pm disable-user --user 0 '$packageName'"
        } else {
            "pm enable --user 0 '$packageName'"
        }
        return execute(
            action = if (frozen) "FREEZE_PACKAGE" else "UNFREEZE_PACKAGE",
            target = packageName,
            command = command,
        )
    }

    suspend fun listPackageDirectories(): ShellResult {
        val command = """
            for base in ${RootOrphanPolicy.allowedRoots.joinToString(" ")}; do
                [ -d "${'$'}base" ] || continue
                for directory in "${'$'}base"/*; do
                    [ -d "${'$'}directory" ] || continue
                    package_name="${'$'}{directory##*/}"
                    canonical="${'$'}(readlink -f "${'$'}directory" 2>/dev/null || printf '%s' "${'$'}directory")"
                    size_kb="${'$'}(du -sk "${'$'}directory" 2>/dev/null | awk 'NR==1 {print ${'$'}1}')"
                    modified="${'$'}(stat -c %Y "${'$'}directory" 2>/dev/null || printf '0')"
                    printf '%s\t%s\t%s\t%s\t%s\n' \
                        "${'$'}base" \
                        "${'$'}package_name" \
                        "${'$'}canonical" \
                        "${'$'}{size_kb:-0}" \
                        "${'$'}{modified:-0}"
                done
            done
        """.trimIndent()
        return execute(
            action = "SCAN_ORPHAN_DIRECTORIES",
            target = RootOrphanPolicy.allowedRoots.joinToString(","),
            command = command,
            timeoutSeconds = 120,
        )
    }

    suspend fun deleteOrphanDirectory(
        packageName: String,
        path: String,
    ): ShellResult {
        requireValidPackage(packageName)
        require(RootOrphanPolicy.isAllowedPath(packageName, path)) {
            "Directory is outside the orphan allowlist"
        }
        val command = """
            if pm path '$packageName' >/dev/null 2>&1; then
                printf 'Package is installed' >&2
                exit 3
            fi
            if [ -d '$path' ]; then
                rm -rf -- '$path'
            fi
        """.trimIndent()
        return execute(
            action = "DELETE_ORPHAN_DIRECTORY",
            target = path,
            command = command,
            timeoutSeconds = 60,
        )
    }

    private fun requireValidPackage(packageName: String) {
        require(RootOrphanPolicy.isValidPackageName(packageName)) { "Invalid package name" }
    }

    private suspend fun execute(
        action: String,
        target: String,
        command: String,
        timeoutSeconds: Long = 20,
        audit: Boolean = true,
    ): ShellResult = withContext(Dispatchers.IO) {
        val result = runCatching {
            val process = ProcessBuilder("su", "-c", command).start()
            coroutineScope {
                val stdout = async {
                    process.inputStream.bufferedReader().use { it.readText() }
                }
                val stderr = async {
                    process.errorStream.bufferedReader().use { it.readText() }
                }
                val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    process.waitFor(2, TimeUnit.SECONDS)
                }
                ShellResult(
                    exitCode = if (finished) process.exitValue() else -1,
                    output = stdout.await(),
                    error = if (finished) stderr.await() else {
                        stderr.await().ifBlank { "Root command timed out" }
                    },
                )
            }
        }.getOrElse { error ->
            ShellResult(-1, "", error.message.orEmpty())
        }
        if (audit) {
            auditStore?.record(action = action, target = target, result = result)
        }
        result
    }
}
