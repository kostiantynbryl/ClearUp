package com.norvexa.clearup.data.directories

import java.io.File

object EmptyDirectoryPolicy {
    private const val ANDROID_DIRECTORY = "Android"

    fun canonicalOrNull(file: File): String? = runCatching {
        file.canonicalFile.absolutePath
    }.getOrNull()

    fun isAllowedCandidate(
        candidateCanonicalPath: String,
        rootCanonicalPaths: Set<String>,
        externalStorageCanonicalPath: String,
    ): Boolean {
        val candidate = File(candidateCanonicalPath)
        val normalizedCandidate = canonicalOrNull(candidate) ?: return false
        if (normalizedCandidate != candidateCanonicalPath) return false

        val externalRoot = canonicalOrNull(File(externalStorageCanonicalPath)) ?: return false
        val androidRoot = File(externalRoot, ANDROID_DIRECTORY).absolutePath
        if (
            normalizedCandidate == externalRoot ||
            normalizedCandidate == androidRoot ||
            normalizedCandidate.startsWith("$androidRoot${File.separator}")
        ) {
            return false
        }

        return rootCanonicalPaths.any { rootPath ->
            val normalizedRoot = canonicalOrNull(File(rootPath)) ?: return@any false
            normalizedCandidate != normalizedRoot &&
                normalizedCandidate.startsWith("$normalizedRoot${File.separator}")
        }
    }

    fun depthFromRoot(
        candidateCanonicalPath: String,
        rootCanonicalPaths: Set<String>,
    ): Int? {
        val root = rootCanonicalPaths
            .filter { candidateCanonicalPath.startsWith("$it${File.separator}") }
            .maxByOrNull(String::length)
            ?: return null
        val relative = candidateCanonicalPath.removePrefix(root).trimStart(File.separatorChar)
        if (relative.isBlank()) return null
        return relative.split(File.separatorChar).size
    }
}
