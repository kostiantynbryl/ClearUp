package com.norvexa.clearup.data.privilege

import android.content.Context
import android.content.pm.PackageManager
import com.norvexa.clearup.domain.model.OrphanDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootOrphanRepository(
    private val context: Context,
    private val rootShell: RootShell,
) {
    suspend fun scan(
        protectedPackages: Set<String>,
    ): List<OrphanDirectory> = withContext(Dispatchers.IO) {
        val installedPackages = installedPackages()
        val result = rootShell.listPackageDirectories()
        check(result.success) {
            result.error.ifBlank { "Не удалось прочитать каталоги приложений" }
        }

        result.output.lineSequence()
            .mapNotNull(::parseLine)
            .filterNot { candidate ->
                candidate.packageName in installedPackages ||
                    candidate.packageName in protectedPackages ||
                    candidate.packageName == context.packageName
            }
            .distinctBy { candidate -> candidate.canonicalPath }
            .sortedWith(
                compareByDescending<OrphanDirectory> { it.bytes }
                    .thenBy { it.packageName },
            )
            .toList()
    }

    suspend fun delete(candidate: OrphanDirectory): ShellResult =
        rootShell.deleteOrphanDirectory(
            packageName = candidate.packageName,
            path = candidate.path,
        )

    private fun installedPackages(): Set<String> {
        @Suppress("DEPRECATION")
        return context.packageManager.getInstalledPackages(
            PackageManager.MATCH_DISABLED_COMPONENTS,
        ).mapTo(hashSetOf()) { packageInfo -> packageInfo.packageName }
    }

    private fun parseLine(line: String): OrphanDirectory? {
        val parts = line.split('\t', limit = 5)
        if (parts.size != 5) return null
        val root = parts[0]
        val packageName = parts[1]
        val canonicalPath = parts[2]
        if (!RootOrphanPolicy.isValidPackageName(packageName)) return null
        val location = RootOrphanPolicy.locationForRoot(root) ?: return null
        val path = "$root/$packageName"
        if (!RootOrphanPolicy.isAllowedPath(packageName, path)) return null
        val sizeKb = parts[3].toLongOrNull()?.coerceAtLeast(0) ?: 0
        val modifiedSeconds = parts[4].toLongOrNull()?.coerceAtLeast(0) ?: 0
        return OrphanDirectory(
            packageName = packageName,
            path = path,
            canonicalPath = canonicalPath,
            bytes = sizeKb.coerceAtMost(Long.MAX_VALUE / 1024L) * 1024L,
            modifiedAtMillis = modifiedSeconds.coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L,
            location = location,
        )
    }
}
