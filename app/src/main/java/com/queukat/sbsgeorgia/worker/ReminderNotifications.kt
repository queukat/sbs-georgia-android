package com.queukat.sbsgeorgia.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.queukat.sbsgeorgia.MainActivity
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
                context.getString(R.string.notification_channel_declaration_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    context.getString(
                        R.string.notification_channel_declaration_reminders_description
                    )
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                PAYMENT_CHANNEL_ID,
                context.getString(R.string.notification_channel_payment_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    context.getString(R.string.notification_channel_payment_reminders_description)
            }
        )
    }

    fun show(context: Context, reminderNotification: ReminderNotification) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannels(context)
        val channelId =
            when (reminderNotification.type) {
                ReminderType.DECLARATION -> DECLARATION_CHANNEL_ID
                ReminderType.PAYMENT -> PAYMENT_CHANNEL_ID
            }
        val notificationId =
            when (reminderNotification.type) {
                ReminderType.DECLARATION -> 1001
                ReminderType.PAYMENT -> 1002
            }
        val openAppPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(reminderNotification.title)
                .setContentText(reminderNotification.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reminderNotification.body))
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        NotificationManagerCompat.from(context).notify(
            reminderNotification.notificationId ?: notificationId,
            notification
        )
    }
}
