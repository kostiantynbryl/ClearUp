package com.norvexa.clearup.data.scanner

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.norvexa.clearup.data.scanner.rules.EmptyFileRule
import com.norvexa.clearup.data.scanner.rules.LargeFileRule
import com.norvexa.clearup.data.scanner.rules.OldApkRule
import com.norvexa.clearup.data.scanner.rules.ScreenshotRule
import com.norvexa.clearup.data.scanner.rules.TemporaryFileRule
import com.norvexa.clearup.domain.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScannerEngine(private val context: Context) {
    suspend fun scan(
        largeFileThresholdMb: Int,
        excludedPrefixes: Set<String> = emptySet(),
    ): ScanResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val entries = queryMediaEntries().filterNot { entry ->
            excludedPrefixes.any { prefix ->
                entry.relativePath.startsWith(prefix, ignoreCase = true)
            }
        }
        val rules = listOf(
            TemporaryFileRule(),
            EmptyFileRule(),
            OldApkRule(),
            ScreenshotRule(),
            LargeFileRule(largeFileThresholdMb.toLong() * 1024L * 1024L),
        )
        val items = entries
            .mapNotNull { entry -> rules.firstNotNullOfOrNull { it.evaluate(entry) } }
            .distinctBy { it.uri }
            .sortedWith(compareBy({ it.riskLevel }, { -it.bytes }))
        ScanResult(
            items = items,
            scannedCount = entries.size,
            startedAtMillis = started,
            completedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun queryMediaEntries(): List<MediaEntry> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Files.FileColumns.MIME_TYPE)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            } else {
                add(MediaStore.Files.FileColumns.DATA)
            }
        }.toTypedArray()

        val entries = mutableListOf<MediaEntry>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Files.FileColumns.RELATIVE_PATH
                } else {
                    MediaStore.Files.FileColumns.DATA
                }
                val pathIndex = cursor.getColumnIndexOrThrow(pathColumn)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val uri = ContentUris.withAppendedId(collection, id)
                    entries += MediaEntry(
                        id = id,
                        displayName = cursor.getString(nameIndex).orEmpty(),
                        relativePath = cursor.getString(pathIndex).orEmpty(),
                        uri = uri.toString(),
                        bytes = cursor.getLong(sizeIndex).coerceAtLeast(0),
                        mimeType = cursor.getString(mimeIndex).orEmpty(),
                        modifiedAtMillis = cursor.getLong(modifiedIndex).coerceAtLeast(0) * 1000L,
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return entries
    }
}
