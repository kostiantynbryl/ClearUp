package com.norvexa.clearup.data.directories

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.norvexa.clearup.domain.model.EmptyDirectory
import com.norvexa.clearup.domain.model.EmptyDirectoryDeleteResult
import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class EmptyDirectoryRepository(context: Context) {
    private val appContext = context.applicationContext

    fun hasRequiredAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scan(minAgeDays: Int = DEFAULT_MIN_AGE_DAYS): List<EmptyDirectory> =
        withContext(Dispatchers.IO) {
            check(hasRequiredAccess()) {
                "Нет доступа для проверки публичных каталогов"
            }

            val externalRoot = Environment.getExternalStorageDirectory()
            val externalCanonical = EmptyDirectoryPolicy.canonicalOrNull(externalRoot)
                ?: error("Не удалось определить корень общего накопителя")
            val roots = publicRoots()
                .mapNotNull { root ->
                    if (!root.exists() || !root.isDirectory || isSymbolicLink(root)) {
                        null
                    } else {
                        EmptyDirectoryPolicy.canonicalOrNull(root)
                    }
                }
                .toSet()

            if (roots.isEmpty()) return@withContext emptyList()

            val cutoff = System.currentTimeMillis() -
                TimeUnit.DAYS.toMillis(minAgeDays.coerceIn(1, 365).toLong())
            val result = mutableListOf<EmptyDirectory>()
            val stack = ArrayDeque<File>()
            publicRoots().forEach { root ->
                if (
                    root.exists() &&
                    root.isDirectory &&
                    !isSymbolicLink(root) &&
                    EmptyDirectoryPolicy.canonicalOrNull(root) in roots
                ) {
                    stack.addLast(root)
                }
            }

            var visited = 0
            while (stack.isNotEmpty() && visited < MAX_VISITED_DIRECTORIES) {
                currentCoroutineContext().ensureActive()
                val directory = stack.removeLast()
                visited += 1
                val children = directory.listFiles() ?: continue

                children.forEach { child ->
                    currentCoroutineContext().ensureActive()
                    if (!child.isDirectory || isSymbolicLink(child)) return@forEach
                    val canonical = EmptyDirectoryPolicy.canonicalOrNull(child) ?: return@forEach
                    if (
                        EmptyDirectoryPolicy.isAllowedCandidate(
                            candidateCanonicalPath = canonical,
                            rootCanonicalPaths = roots,
                            externalStorageCanonicalPath = externalCanonical,
                        )
                    ) {
                        stack.addLast(child)
                    }
                }

                val canonical = EmptyDirectoryPolicy.canonicalOrNull(directory) ?: continue
                val depth = EmptyDirectoryPolicy.depthFromRoot(canonical, roots) ?: continue
                if (
                    children.isEmpty() &&
                    directory.lastModified() in 1..cutoff &&
                    EmptyDirectoryPolicy.isAllowedCandidate(
                        candidateCanonicalPath = canonical,
                        rootCanonicalPaths = roots,
                        externalStorageCanonicalPath = externalCanonical,
                    )
                ) {
                    result += EmptyDirectory(
                        path = directory.absolutePath,
                        canonicalPath = canonical,
                        modifiedAtMillis = directory.lastModified(),
                        depth = depth,
                    )
                }
            }

            result
                .distinctBy(EmptyDirectory::canonicalPath)
                .sortedWith(
                    compareByDescending<EmptyDirectory> { it.depth }
                        .thenBy { it.canonicalPath.lowercase() },
                )
        }

    suspend fun deleteSelected(
        selected: Collection<EmptyDirectory>,
    ): EmptyDirectoryDeleteResult = withContext(Dispatchers.IO) {
        check(hasRequiredAccess()) {
            "Доступ к общему накопителю был отозван"
        }

        val externalRoot = Environment.getExternalStorageDirectory()
        val externalCanonical = EmptyDirectoryPolicy.canonicalOrNull(externalRoot)
            ?: error("Не удалось определить корень общего накопителя")
        val roots = publicRoots()
            .mapNotNull(EmptyDirectoryPolicy::canonicalOrNull)
            .toSet()

        var deleted = 0
        var skipped = 0
        val failed = mutableListOf<String>()

        selected
            .distinctBy(EmptyDirectory::canonicalPath)
            .sortedByDescending(EmptyDirectory::depth)
            .forEach { candidate ->
                currentCoroutineContext().ensureActive()
                val directory = File(candidate.path)
                val canonical = EmptyDirectoryPolicy.canonicalOrNull(directory)
                val children = if (
                    directory.exists() &&
                    directory.isDirectory &&
                    !isSymbolicLink(directory)
                ) {
                    directory.listFiles()
                } else {
                    null
                }

                when {
                    canonical == null || canonical != candidate.canonicalPath -> skipped += 1
                    !EmptyDirectoryPolicy.isAllowedCandidate(
                        candidateCanonicalPath = canonical,
                        rootCanonicalPaths = roots,
                        externalStorageCanonicalPath = externalCanonical,
                    ) -> skipped += 1
                    children == null -> failed += candidate.path
                    children.isNotEmpty() -> skipped += 1
                    directory.delete() -> deleted += 1
                    else -> failed += candidate.path
                }
            }

        EmptyDirectoryDeleteResult(
            deleted = deleted,
            skipped = skipped,
            failedPaths = failed.take(MAX_REPORTED_FAILURES),
        )
    }

    @Suppress("DEPRECATION")
    private fun publicRoots(): List<File> = PUBLIC_DIRECTORY_NAMES.map { directoryName ->
        Environment.getExternalStoragePublicDirectory(directoryName)
    }

    private fun isSymbolicLink(file: File): Boolean = runCatching {
        Files.isSymbolicLink(file.toPath())
    }.getOrDefault(true)

    companion object {
        const val DEFAULT_MIN_AGE_DAYS = 14
        private const val MAX_VISITED_DIRECTORIES = 50_000
        private const val MAX_REPORTED_FAILURES = 20

        private val PUBLIC_DIRECTORY_NAMES = listOf(
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_PODCASTS,
            Environment.DIRECTORY_RINGTONES,
            Environment.DIRECTORY_NOTIFICATIONS,
            Environment.DIRECTORY_ALARMS,
        )
    }
}
