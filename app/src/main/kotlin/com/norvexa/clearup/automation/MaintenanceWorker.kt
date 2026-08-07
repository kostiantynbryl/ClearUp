package com.norvexa.clearup.automation

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.norvexa.clearup.MainActivity
import com.norvexa.clearup.R
import com.norvexa.clearup.core.util.ByteFormatter
import com.norvexa.clearup.data.exclusions.ExclusionRepository
import com.norvexa.clearup.data.history.HistoryStore
import com.norvexa.clearup.data.scanner.ScannerEngine
import com.norvexa.clearup.data.storage.StorageAccessRepository
import com.norvexa.clearup.domain.model.HistoryType
import com.norvexa.clearup.domain.model.RiskLevel
import kotlinx.coroutines.CancellationException

class MaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val storageAccess = StorageAccessRepository(applicationContext)
        if (!storageAccess.readState().completeAccess) {
            return Result.success()
        }
        val exclusions = ExclusionRepository(applicationContext).current()
        val threshold = inputData.getInt(KEY_THRESHOLD_MB, 100)
        val result = ScannerEngine(applicationContext, storageAccess).scan(
            largeFileThresholdMb = threshold,
            excludedPrefixes = exclusions.pathPrefixes,
        )
        val safeCount = result.items.count { it.riskLevel == RiskLevel.SAFE }

        HistoryStore(applicationContext).use { history ->
            history.record(
                type = HistoryType.AUTOMATION,
                itemCount = safeCount,
                bytes = result.reclaimableBytes,
                note = "Фоновое безопасное сканирование · только SAFE",
            )
        }

        if (safeCount > 0 && result.reclaimableBytes > 0) {
            notify(safeCount, result.reclaimableBytes)
        }
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }

    private fun notify(count: Int, bytes: Long) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(
            applicationContext,
            ClearUpNotifications.CHANNEL_SCAN,
        )
            .setSmallIcon(R.drawable.ic_notification_clearup)
            .setContentTitle("ClearUp нашёл безопасные файлы для очистки")
            .setContentText("$count объектов · ${ByteFormatter.format(bytes)}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(4101, notification)
    }

    companion object {
        const val KEY_THRESHOLD_MB = "threshold_mb"
    }
}
