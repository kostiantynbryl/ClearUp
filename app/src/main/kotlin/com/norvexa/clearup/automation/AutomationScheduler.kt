package com.norvexa.clearup.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class AutomationScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(intervalDays: Int, thresholdMb: Int, chargingOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(chargingOnly)
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(
            intervalDays.coerceAtLeast(1).toLong(),
            TimeUnit.DAYS,
        )
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putInt(MaintenanceWorker.KEY_THRESHOLD_MB, thresholdMb)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() = workManager.cancelUniqueWork(WORK_NAME)

    companion object {
        private const val WORK_NAME = "clearup_periodic_scan"
    }
}
