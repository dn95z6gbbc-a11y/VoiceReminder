package com.example.voicereminder.data

data class Reminder(
    val id: Long,
    val title: String,
    val scheduledAt: Long,
    val status: String,
    val createdAt: Long,
    val completedAt: Long?
) {
    val isDone: Boolean get() = status == STATUS_DONE

    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_DONE = "DONE"
    }
}
