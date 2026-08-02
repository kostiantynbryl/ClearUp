package com.norvexa.clearup.data.directories

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyDirectoryPolicyTest {
    @Test
    fun acceptsOnlyDescendantsOfPublicRoots() {
        val external = Files.createTempDirectory("clearup-external").toFile()
        val download = File(external, "Download").apply { mkdirs() }
        val empty = File(download, "empty").apply { mkdirs() }

        assertTrue(
            EmptyDirectoryPolicy.isAllowedCandidate(
                candidateCanonicalPath = empty.canonicalPath,
                rootCanonicalPaths = setOf(download.canonicalPath),
                externalStorageCanonicalPath = external.canonicalPath,
            ),
        )
        assertFalse(
            EmptyDirectoryPolicy.isAllowedCandidate(
                candidateCanonicalPath = download.canonicalPath,
                rootCanonicalPaths = setOf(download.canonicalPath),
                externalStorageCanonicalPath = external.canonicalPath,
            ),
        )
    }

    @Test
    fun rejectsAndroidTreeAndOutsidePaths() {
        val external = Files.createTempDirectory("clearup-external").toFile()
        val download = File(external, "Download").apply { mkdirs() }
        val androidData = File(external, "Android/data/com.example").apply { mkdirs() }
        val outside = Files.createTempDirectory("clearup-outside").toFile()

        assertFalse(
            EmptyDirectoryPolicy.isAllowedCandidate(
                candidateCanonicalPath = androidData.canonicalPath,
                rootCanonicalPaths = setOf(download.canonicalPath),
                externalStorageCanonicalPath = external.canonicalPath,
            ),
        )
        assertFalse(
            EmptyDirectoryPolicy.isAllowedCandidate(
                candidateCanonicalPath = outside.canonicalPath,
                rootCanonicalPaths = setOf(download.canonicalPath),
                externalStorageCanonicalPath = external.canonicalPath,
            ),
        )
    }

    @Test
    fun calculatesRelativeDepth() {
        val external = Files.createTempDirectory("clearup-external").toFile()
        val pictures = File(external, "Pictures").apply { mkdirs() }
        val nested = File(pictures, "one/two").apply { mkdirs() }

        assertEquals(
            2,
            EmptyDirectoryPolicy.depthFromRoot(
                nested.canonicalPath,
                setOf(pictures.canonicalPath),
            ),
        )
        assertNull(
            EmptyDirectoryPolicy.depthFromRoot(
                pictures.canonicalPath,
                setOf(pictures.canonicalPath),
            ),
        )
    }
}
