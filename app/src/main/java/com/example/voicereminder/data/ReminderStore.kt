package com.example.voicereminder.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ReminderStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                scheduled_at INTEGER NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                completed_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_reminders_status_time ON reminders(status, scheduled_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun insert(title: String, scheduledAt: Long): Reminder {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", title)
            put("scheduled_at", scheduledAt)
            put("status", Reminder.STATUS_ACTIVE)
            put("created_at", now)
            putNull("completed_at")
        }
        val id = writableDatabase.insertOrThrow("reminders", null, values)
        return Reminder(id, title, scheduledAt, Reminder.STATUS_ACTIVE, now, null)
    }

    @Synchronized
    fun get(id: Long): Reminder? {
        readableDatabase.query(
            "reminders",
            COLUMNS,
            "id=?",
            arrayOf(id.toString()),
            null, null, null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toReminder() else null
        }
    }

    @Synchronized
    fun active(): List<Reminder> = queryByStatus(Reminder.STATUS_ACTIVE, "scheduled_at ASC")

    @Synchronized
    fun completed(): List<Reminder> = queryByStatus(Reminder.STATUS_DONE, "completed_at DESC")

    @Synchronized
    fun markDone(id: Long) {
        val values = ContentValues().apply {
            put("status", Reminder.STATUS_DONE)
            put("completed_at", System.currentTimeMillis())
        }
        writableDatabase.update("reminders", values, "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun reschedule(id: Long, scheduledAt: Long) {
        val values = ContentValues().apply {
            put("scheduled_at", scheduledAt)
            put("status", Reminder.STATUS_ACTIVE)
            putNull("completed_at")
        }
        writableDatabase.update("reminders", values, "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun update(id: Long, title: String, scheduledAt: Long) {
        val values = ContentValues().apply {
            put("title", title)
            put("scheduled_at", scheduledAt)
            put("status", Reminder.STATUS_ACTIVE)
            putNull("completed_at")
        }
        writableDatabase.update("reminders", values, "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun delete(id: Long) {
        writableDatabase.delete("reminders", "id=?", arrayOf(id.toString()))
    }

    private fun queryByStatus(status: String, orderBy: String): List<Reminder> {
        val result = mutableListOf<Reminder>()
        readableDatabase.query(
            "reminders",
            COLUMNS,
            "status=?",
            arrayOf(status),
            null, null,
            orderBy
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toReminder()
        }
        return result
    }

    private fun android.database.Cursor.toReminder(): Reminder {
        return Reminder(
            id = getLong(getColumnIndexOrThrow("id")),
            title = getString(getColumnIndexOrThrow("title")),
            scheduledAt = getLong(getColumnIndexOrThrow("scheduled_at")),
            status = getString(getColumnIndexOrThrow("status")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            completedAt = getColumnIndexOrThrow("completed_at").let { idx ->
                if (isNull(idx)) null else getLong(idx)
            }
        )
    }

    companion object {
        private const val DB_NAME = "voice_reminders.db"
        private const val DB_VERSION = 1
        private val COLUMNS = arrayOf(
            "id", "title", "scheduled_at", "status", "created_at", "completed_at"
        )
    }
}
