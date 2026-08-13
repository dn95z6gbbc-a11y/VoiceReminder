package com.example.voicereminder

import android.app.Application
import com.example.voicereminder.alarm.ReminderNotifications

class ReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.ensureChannel(this)
    }
}
