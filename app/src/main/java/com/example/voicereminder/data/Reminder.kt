package com.example.voicereminder.data

data class Reminder(
    val id: Long,
    val title: String,
    val scheduledAt: Long,
    val status: String,
    val createdAt: Long,
    val completedAt: Long?,
    val repeatRule: String = REPEAT_NONE
) {
    val isDone: Boolean get() = status == STATUS_DONE
    val isRepeating: Boolean get() = repeatRule != REPEAT_NONE

    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_DONE = "DONE"
        const val REPEAT_NONE = "NONE"
        const val REPEAT_HOURLY = "HOURLY"
        const val REPEAT_DAILY = "DAILY"
        const val REPEAT_WEEKLY = "WEEKLY"
        const val REPEAT_MONTHLY = "MONTHLY"
    }
}
