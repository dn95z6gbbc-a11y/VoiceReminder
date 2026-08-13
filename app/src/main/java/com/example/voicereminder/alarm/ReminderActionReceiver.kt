package com.example.voicereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.voicereminder.data.ReminderStore
import java.time.Instant
import java.time.ZoneId

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (id <= 0L) return

        val store = ReminderStore(context)
        val scheduler = AlarmScheduler(context)
        val reminder = store.get(id) ?: return

        when (intent.action) {
            ACTION_DONE -> {
                store.markDone(id)
                scheduler.cancel(id)
            }

            ACTION_SNOOZE_1H -> {
                val newTime = System.currentTimeMillis() + 60L * 60L * 1000L
                store.reschedule(id, newTime)
                scheduler.schedule(store.get(id)!!)
            }

            ACTION_SNOOZE_3H -> {
                val newTime = System.currentTimeMillis() + 3L * 60L * 60L * 1000L
                store.reschedule(id, newTime)
                scheduler.schedule(store.get(id)!!)
            }

            ACTION_SNOOZE_TOMORROW -> {
                val zone = ZoneId.systemDefault()
                val original = Instant.ofEpochMilli(reminder.scheduledAt).atZone(zone)
                var tomorrow = original.plusDays(1)
                val now = Instant.now().atZone(zone)
                while (!tomorrow.isAfter(now)) tomorrow = tomorrow.plusDays(1)

                store.reschedule(id, tomorrow.toInstant().toEpochMilli())
                scheduler.schedule(store.get(id)!!)
            }
        }

        ReminderNotifications.cancel(context, id)
    }

    companion object {
        const val ACTION_DONE = "com.example.voicereminder.DONE"
        const val ACTION_SNOOZE_1H = "com.example.voicereminder.SNOOZE_1H"
        const val ACTION_SNOOZE_3H = "com.example.voicereminder.SNOOZE_3H"
        const val ACTION_SNOOZE_TOMORROW = "com.example.voicereminder.SNOOZE_TOMORROW"
    }
}
