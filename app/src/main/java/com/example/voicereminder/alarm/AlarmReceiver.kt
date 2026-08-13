package com.example.voicereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.voicereminder.data.Reminder
import com.example.voicereminder.data.ReminderStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (id <= 0L) return

        val reminder = ReminderStore(context).get(id) ?: return
        if (reminder.status != Reminder.STATUS_ACTIVE) return

        ReminderNotifications.show(context, reminder)
    }
}
