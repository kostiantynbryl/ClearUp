package com.norvexa.clearup.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object ClearUpNotifications {
    const val CHANNEL_SCAN = "clearup_scan_results"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SCAN,
                    "Результаты сканирования",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Уведомления о найденных файлах без автоматического удаления"
                },
            )
        }
    }
}
