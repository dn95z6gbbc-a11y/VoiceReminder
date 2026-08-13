package com.example.voicereminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.voicereminder.data.Reminder

class AlarmScheduler(private val context: Context) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(reminder: Reminder) {
        val triggerAt = reminder.scheduledAt.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        val pendingIntent = alarmPendingIntent(reminder.id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
            return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }

    fun cancel(reminderId: Long) {
        alarmManager.cancel(alarmPendingIntent(reminderId))
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun alarmPendingIntent(id: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_REMINDER_ID, id)

        return PendingIntent.getBroadcast(
            context,
            requestCode(id, 1),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"

        fun requestCode(id: Long, salt: Int): Int {
            val mixed = (id xor (id ushr 32)).toInt()
            return mixed * 31 + salt
        }
    }
}
