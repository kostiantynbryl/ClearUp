package com.norvexa.clearup.data.duplicates

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.norvexa.clearup.domain.model.DuplicateGroup
import com.norvexa.clearup.domain.model.DuplicateItem
import com.norvexa.clearup.domain.model.DuplicateScanResult
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class DuplicateRepository(private val context: Context) {
    suspend fun scanExact(excludedPrefixes: Set<String>): DuplicateScanResult = withContext(Dispatchers.IO) {
        val candidates = queryCandidates(excludedPrefixes)
        val sameSize = candidates
            .groupBy { it.bytes }
            .filterKeys { it > 0 }
            .values
            .filter { it.size > 1 }
        val hashed = mutableMapOf<String, MutableList<DuplicateItem>>()
        sameSize.flatten().forEach { item ->
            coroutineContext.ensureActive()
            val hash = hash(item.uri) ?: return@forEach
            hashed.getOrPut("${item.bytes}:$hash") { mutableListOf() } += item
        }
        val groups = hashed.entries
            .filter { (_, matches) -> matches.size > 1 }
            .map { (fingerprint, matches) ->
                val ordered = matches.sortedWith(
                    compareByDescending<DuplicateItem> { it.modifiedAtMillis }
                        .thenBy { it.displayName },
                )
                DuplicateGroup(
                    fingerprint = fingerprint,
                    items = ordered.mapIndexed { index, item ->
                        item.copy(selected = index > 0)
                    },
                )
            }
            .sortedByDescending { it.reclaimableBytes }
        DuplicateScanResult(
            groups = groups,
            candidateCount = sameSize.sumOf { it.size },
        )
    }

    private fun queryCandidates(excludedPrefixes: Set<String>): List<DuplicateItem> {
        val collection = MediaStore.Files.getContentUri("external")
        val columns = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            } else {
                add(MediaStore.Files.FileColumns.DATA)
            }
        }.toTypedArray()
        val result = mutableListOf<DuplicateItem>()
        try {
            context.contentResolver.query(
                collection,
                columns,
                "${MediaStore.Files.FileColumns.SIZE}>0",
                null,
                null,
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val size = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val modified = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Files.FileColumns.RELATIVE_PATH
                } else {
                    MediaStore.Files.FileColumns.DATA
                }
                val path = cursor.getColumnIndexOrThrow(pathColumn)
                while (cursor.moveToNext()) {
                    val relativePath = cursor.getString(path).orEmpty()
                    if (excludedPrefixes.any { relativePath.startsWith(it, ignoreCase = true) }) continue
                    val itemId = cursor.getLong(id)
                    result += DuplicateItem(
                        id = itemId,
                        uri = ContentUris.withAppendedId(collection, itemId).toString(),
                        displayName = cursor.getString(name).orEmpty(),
                        path = relativePath,
                        bytes = cursor.getLong(size).coerceAtLeast(0),
                        modifiedAtMillis = cursor.getLong(modified).coerceAtLeast(0) * 1000L,
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return result
    }

    private fun hash(uriValue: String): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(android.net.Uri.parse(uriValue))?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return null
        digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }.getOrNull()
}
