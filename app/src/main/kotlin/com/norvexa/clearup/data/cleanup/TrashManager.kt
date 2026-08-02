package com.norvexa.clearup.data.cleanup

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.norvexa.clearup.domain.model.ScanItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface TrashAction {
    data class RequiresConfirmation(val pendingIntent: PendingIntent) : TrashAction
    data class Completed(val movedCount: Int, val failedCount: Int) : TrashAction
    data object NothingSelected : TrashAction
}

class TrashManager(private val context: Context) {
    suspend fun prepare(items: List<ScanItem>): TrashAction = withContext(Dispatchers.IO) {
        val uris = items.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
        if (uris.isEmpty()) return@withContext TrashAction.NothingSelected

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext TrashAction.RequiresConfirmation(
                MediaStore.createTrashRequest(context.contentResolver, uris, true),
            )
        }

        var moved = 0
        var failed = 0
        uris.forEach { uri ->
            try {
                if (context.contentResolver.delete(uri, null, null) > 0) moved++ else failed++
            } catch (_: SecurityException) {
                failed++
            }
        }
        TrashAction.Completed(movedCount = moved, failedCount = failed)
    }
}
