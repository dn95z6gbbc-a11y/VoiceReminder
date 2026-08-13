package com.example.voicereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.voicereminder.data.Reminder
import com.example.voicereminder.data.ReminderStore
import java.time.Instant
import java.time.ZoneId

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (id <= 0L) return

        val store = ReminderStore(context)
        val reminder = store.get(id) ?: return
        if (reminder.status != Reminder.STATUS_ACTIVE) return

        ReminderNotifications.show(context, reminder)

        if (reminder.isRepeating) {
            val nextAt = nextOccurrence(reminder, System.currentTimeMillis())
            store.reschedule(id, nextAt)
            store.get(id)?.let { AlarmScheduler(context).schedule(it) }
        }
    }

    private fun nextOccurrence(reminder: Reminder, afterMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        var next = Instant.ofEpochMilli(reminder.scheduledAt).atZone(zone)
        val after = Instant.ofEpochMilli(afterMillis).atZone(zone)

        do {
            next = when (reminder.repeatRule) {
                Reminder.REPEAT_HOURLY -> next.plusHours(1)
                Reminder.REPEAT_DAILY -> next.plusDays(1)
                Reminder.REPEAT_WEEKLY -> next.plusWeeks(1)
                Reminder.REPEAT_MONTHLY -> next.plusMonths(1)
                else -> return reminder.scheduledAt
            }
        } while (!next.isAfter(after))

        return next.toInstant().toEpochMilli()
    }
}
