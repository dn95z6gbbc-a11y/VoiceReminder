package com.example.voicereminder.alarm

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
import com.example.voicereminder.MainActivity
import com.example.voicereminder.R
import com.example.voicereminder.data.Reminder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReminderNotifications {
    const val CHANNEL_ID = "reminders_high"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Напоминания",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Точные голосовые напоминания"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun show(context: Context, reminder: Reminder) {
        ensureChannel(context)

        val openIntent = PendingIntent.getActivity(
            context,
            AlarmScheduler.requestCode(reminder.id, 10),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText("Напоминание • ${formatTime(reminder.scheduledAt)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.title))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(openIntent)
            .addAction(0, "Готово", actionPendingIntent(context, reminder.id, ReminderActionReceiver.ACTION_DONE, 20))
            .addAction(0, "+1 час", actionPendingIntent(context, reminder.id, ReminderActionReceiver.ACTION_SNOOZE_1H, 21))
            .addAction(0, "+3 часа", actionPendingIntent(context, reminder.id, ReminderActionReceiver.ACTION_SNOOZE_3H, 22))
            .addAction(0, "Завтра", actionPendingIntent(context, reminder.id, ReminderActionReceiver.ACTION_SNOOZE_TOMORROW, 23))

        if (
            Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(notificationId(reminder.id), builder.build())
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    private fun actionPendingIntent(
        context: Context,
        reminderId: Long,
        action: String,
        salt: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)

        return PendingIntent.getBroadcast(
            context,
            AlarmScheduler.requestCode(reminderId, salt),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notificationId(id: Long): Int =
        (id xor (id ushr 32)).toInt() and 0x7fffffff

    private fun formatTime(epochMillis: Long): String {
        val formatter = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru"))
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
    }
}
