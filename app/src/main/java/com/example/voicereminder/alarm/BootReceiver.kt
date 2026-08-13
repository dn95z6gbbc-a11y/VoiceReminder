package com.example.voicereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.voicereminder.data.ReminderStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                val store = ReminderStore(context)
                val scheduler = AlarmScheduler(context)
                store.active().forEach { reminder ->
                    scheduler.schedule(reminder)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
