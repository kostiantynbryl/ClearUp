package com.norvexa.clearup.data.storage

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.norvexa.clearup.domain.model.StorageCategory
import com.norvexa.clearup.domain.model.StorageCategoryUsage
import com.norvexa.clearup.domain.model.StorageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidStorageRepository(private val context: Context) {
    suspend fun loadSummary(): StorageSummary = withContext(Dispatchers.IO) {
        val path = Environment.getExternalStorageDirectory().absolutePath
        val stat = StatFs(path)
        val total = stat.totalBytes.coerceAtLeast(0)
        val free = stat.availableBytes.coerceAtLeast(0)
        StorageSummary(totalBytes = total, usedBytes = (total - free).coerceAtLeast(0), freeBytes = free)
    }

    suspend fun loadCategoryUsage(): List<StorageCategoryUsage> = withContext(Dispatchers.IO) {
        val totals = mutableMapOf<StorageCategory, Pair<Long, Int>>()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
        )

        try {
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
                    val size = cursor.getLong(sizeIndex).coerceAtLeast(0)
                    val mime = cursor.getString(mimeIndex).orEmpty()
                    val name = cursor.getString(nameIndex).orEmpty()
                    val category = classify(mime, name)
                    val previous = totals[category] ?: (0L to 0)
                    totals[category] = (previous.first + size) to (previous.second + 1)
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        }

        StorageCategory.entries.mapNotNull { category ->
            totals[category]?.let { StorageCategoryUsage(category, it.first, it.second) }
        }.sortedByDescending { it.bytes }
    }

    private fun classify(mime: String, name: String): StorageCategory {
        val lower = name.lowercase()
        return when {
            mime.startsWith("image/") -> StorageCategory.IMAGES
            mime.startsWith("video/") -> StorageCategory.VIDEO
            mime.startsWith("audio/") -> StorageCategory.AUDIO
            mime == "application/vnd.android.package-archive" || lower.endsWith(".apk") -> StorageCategory.APK
            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tar") || lower.endsWith(".gz") -> StorageCategory.ARCHIVES
            mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") || mime.contains("sheet") || mime.contains("presentation") -> StorageCategory.DOCUMENTS
            else -> StorageCategory.OTHER
        }
    }
}
