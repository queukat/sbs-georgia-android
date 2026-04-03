package com.queukat.sbsgeorgia.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.service.ReminderNotification
import com.queukat.sbsgeorgia.domain.service.ReminderType

object ReminderNotifications {
    const val DECLARATION_CHANNEL_ID = "declaration-reminders"
    const val PAYMENT_CHANNEL_ID = "payment-reminders"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                DECLARATION_CHANNEL_ID,
                "Declaration reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminders to prepare and submit monthly small business declarations."
            },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                PAYMENT_CHANNEL_ID,
                "Payment reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminders to send and verify small business tax payments."
            },
        )
    }

    fun show(context: Context, reminderNotification: ReminderNotification) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannels(context)
        val channelId = when (reminderNotification.type) {
            ReminderType.DECLARATION -> DECLARATION_CHANNEL_ID
            ReminderType.PAYMENT -> PAYMENT_CHANNEL_ID
        }
        val notificationId = when (reminderNotification.type) {
            ReminderType.DECLARATION -> 1001
            ReminderType.PAYMENT -> 1002
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(reminderNotification.title)
            .setContentText(reminderNotification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminderNotification.body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
