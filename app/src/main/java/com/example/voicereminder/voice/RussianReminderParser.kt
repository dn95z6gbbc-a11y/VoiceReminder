package com.example.voicereminder.voice

import com.example.voicereminder.data.Reminder
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class ParsedReminder(
    val title: String,
    val scheduledAt: Long,
    val repeatRule: String = Reminder.REPEAT_NONE
)

object RussianReminderParser {

    private val numberWords = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "два" to 2, "две" to 2,
        "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7,
        "восемь" to 8, "девять" to 9, "десять" to 10, "одиннадцать" to 11,
        "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14,
        "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17,
        "восемнадцать" to 18, "девятнадцать" to 19, "двадцать" to 20,
        "двадцать один" to 21, "двадцать два" to 22, "двадцать три" to 23
    )

    private val weekdays = mapOf(
        "понедельник" to DayOfWeek.MONDAY,
        "понедельника" to DayOfWeek.MONDAY,
        "вторник" to DayOfWeek.TUESDAY,
        "вторника" to DayOfWeek.TUESDAY,
        "среду" to DayOfWeek.WEDNESDAY,
        "среда" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY,
        "четверга" to DayOfWeek.THURSDAY,
        "пятницу" to DayOfWeek.FRIDAY,
        "пятница" to DayOfWeek.FRIDAY,
        "субботу" to DayOfWeek.SATURDAY,
        "суббота" to DayOfWeek.SATURDAY,
        "воскресенье" to DayOfWeek.SUNDAY
    )

    fun parse(raw: String, now: ZonedDateTime = ZonedDateTime.now()): ParsedReminder? {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return null

        val repeat = parseRepeat(normalized)
        val repeatRule = repeat?.first ?: Reminder.REPEAT_NONE
        val text = repeat?.second?.let { normalized.replace(it, " ") } ?: normalized

        parseDuration(text, now)?.let { (whenAt, matched) ->
            val title = cleanTitle(text.replace(matched, ""))
            return ParsedReminder(title, whenAt.toInstant().toEpochMilli(), repeatRule)
        }

        val timeInfo = parseTime(text)
        if (timeInfo == null) {
            if (repeatRule == Reminder.REPEAT_NONE) return null
            val first = when (repeatRule) {
                Reminder.REPEAT_HOURLY -> now.plusHours(1)
                Reminder.REPEAT_DAILY -> now.plusDays(1)
                Reminder.REPEAT_WEEKLY -> now.plusWeeks(1)
                Reminder.REPEAT_MONTHLY -> now.plusMonths(1)
                else -> return null
            }.withSecond(0).withNano(0)
            return ParsedReminder(
                title = cleanTitle(text),
                scheduledAt = first.toInstant().toEpochMilli(),
                repeatRule = repeatRule
            )
        }

        val (hour, minute, timeMatched) = timeInfo
        var targetDate = now.toLocalDate()
        var dateMatched = ""

        when {
            Regex("""(?<![а-я0-9])послезавтра(?![а-я0-9])""").containsMatchIn(text) -> {
                targetDate = now.toLocalDate().plusDays(2)
                dateMatched = "послезавтра"
            }
            Regex("""(?<![а-я0-9])завтра(?![а-я0-9])""").containsMatchIn(text) -> {
                targetDate = now.toLocalDate().plusDays(1)
                dateMatched = "завтра"
            }
            Regex("""(?<![а-я0-9])сегодня(?![а-я0-9])""").containsMatchIn(text) -> {
                targetDate = now.toLocalDate()
                dateMatched = "сегодня"
            }
            else -> {
                val weekdayEntry = weekdays.entries.firstOrNull { (word, _) ->
                    Regex("""(?<![а-я0-9])${Regex.escape(word)}(?![а-я0-9])""").containsMatchIn(text)
                }
                if (weekdayEntry != null) {
                    var candidate = now.with(
                        TemporalAdjusters.nextOrSame(weekdayEntry.value)
                    ).toLocalDate()
                    val sameDayTime = candidate.atTime(hour, minute).atZone(now.zone)
                    if (!sameDayTime.isAfter(now)) candidate = candidate.plusWeeks(1)
                    targetDate = candidate
                    dateMatched = weekdayEntry.key
                }
            }
        }

        var target = targetDate.atTime(hour, minute).atZone(now.zone)
        val hasExplicitDate = dateMatched.isNotBlank()
        if (!hasExplicitDate && !target.isAfter(now)) target = target.plusDays(1)
        if (!target.isAfter(now)) return null

        val titleSource = text
            .replace(timeMatched, " ")
            .replace(Regex("""(?<![а-я0-9])(сегодня|завтра|послезавтра)(?![а-я0-9])"""), " ")
            .replace(
                Regex("""(?<![а-я0-9])(понедельник|понедельника|вторник|вторника|среду|среда|четверг|четверга|пятницу|пятница|субботу|суббота|воскресенье)(?![а-я0-9])"""),
                " "
            )

        return ParsedReminder(
            title = cleanTitle(titleSource),
            scheduledAt = target.toInstant().toEpochMilli(),
            repeatRule = repeatRule
        )
    }

    private fun parseRepeat(text: String): Pair<String, String>? {
        val patterns = listOf(
            Reminder.REPEAT_HOURLY to Regex("""(?<![а-я0-9])(каждый\s+час|ежечасно|раз\s+в\s+час)(?![а-я0-9])"""),
            Reminder.REPEAT_DAILY to Regex("""(?<![а-я0-9])(каждый\s+день|ежедневно|раз\s+в\s+день)(?![а-я0-9])"""),
            Reminder.REPEAT_WEEKLY to Regex("""(?<![а-я0-9])(каждую\s+неделю|еженедельно|раз\s+в\s+неделю)(?![а-я0-9])"""),
            Reminder.REPEAT_MONTHLY to Regex("""(?<![а-я0-9])(каждый\s+месяц|ежемесячно|раз\s+в\s+месяц)(?![а-я0-9])""")
        )
        for ((rule, regex) in patterns) {
            val match = regex.find(text)
            if (match != null) return rule to match.value
        }
        return null
    }

    private fun parseDuration(text: String, now: ZonedDateTime): Pair<ZonedDateTime, String>? {
        val regex = Regex(
            """(?<![а-я0-9])через\s+([а-я0-9 ]+?)\s+(минуту|минуты|минут|час|часа|часов|день|дня|дней)(?![а-я0-9])"""
        )
        val match = regex.find(text) ?: return null
        val amount = parseNumber(match.groupValues[1].trim()) ?: return null
        if (amount <= 0) return null

        val target = when (match.groupValues[2]) {
            "минуту", "минуты", "минут" -> now.plusMinutes(amount.toLong())
            "час", "часа", "часов" -> now.plusHours(amount.toLong())
            "день", "дня", "дней" -> now.plusDays(amount.toLong())
            else -> return null
        }
        return target to match.value
    }

    private fun parseTime(text: String): Triple<Int, Int, String>? {
        Regex("""(?<![а-я0-9])в\s+полдень(?![а-я0-9])""").find(text)?.let {
            return Triple(12, 0, it.value)
        }
        Regex("""(?<![а-я0-9])в\s+полночь(?![а-я0-9])""").find(text)?.let {
            return Triple(0, 0, it.value)
        }

        val numeric = Regex(
            """(?<![а-я0-9])в\s+([01]?\d|2[0-3])(?:(?::|\.)([0-5]\d))?\s*(утра|дня|вечера|ночи)?(?![а-я0-9])"""
        ).find(text)
        if (numeric != null) {
            var hour = numeric.groupValues[1].toInt()
            val minute = numeric.groupValues[2].ifBlank { "0" }.toInt()
            hour = adjustPartOfDay(hour, numeric.groupValues[3])
            return Triple(hour, minute, numeric.value)
        }

        val words = numberWords.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        val wordRegex = Regex("""(?<![а-я0-9])в\s+($words)\s*(утра|дня|вечера|ночи)?(?![а-я0-9])""")
        val wordMatch = wordRegex.find(text)
        if (wordMatch != null) {
            var hour = numberWords[wordMatch.groupValues[1]] ?: return null
            hour = adjustPartOfDay(hour, wordMatch.groupValues[2])
            return Triple(hour, 0, wordMatch.value)
        }

        return null
    }

    private fun adjustPartOfDay(hourValue: Int, part: String): Int {
        var hour = hourValue
        when (part) {
            "вечера" -> if (hour in 1..11) hour += 12
            "дня" -> if (hour in 1..7) hour += 12
            "ночи" -> if (hour == 12) hour = 0
            "утра" -> if (hour == 12) hour = 0
        }
        return hour.coerceIn(0, 23)
    }

    private fun parseNumber(value: String): Int? {
        value.trim().toIntOrNull()?.let { return it }
        return numberWords[value.trim()]
    }

    private fun cleanTitle(value: String): String {
        var title = value
            .replace(Regex("""(?<![а-я0-9])напомни(?![а-я0-9])"""), " ")
            .replace(Regex("""(?<![а-я0-9])мне(?![а-я0-9])"""), " ")
            .replace(Regex("""(?<![а-я0-9])пожалуйста(?![а-я0-9])"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '-', '—', ':')

        if (title.startsWith("в ")) title = title.removePrefix("в ").trim()
        return title.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString()
        }.ifBlank { "Напоминание" }
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale("ru"))
            .replace('ё', 'е')
            .replace(Regex("""[!?]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
