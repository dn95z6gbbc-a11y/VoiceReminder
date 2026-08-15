package com.example.voicereminder.voice

import com.example.voicereminder.data.Reminder
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class ParsedReminder(
    val title: String,
    val scheduledAt: Long,
    val repeatRule: String = Reminder.REPEAT_NONE
)

data class PendingReminder(
    val title: String,
    val targetDate: LocalDate,
    val repeatRule: String = Reminder.REPEAT_NONE,
    val weekdayBased: Boolean = false
)

object RussianReminderParser {

    private val numberWords = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "два" to 2, "две" to 2,
        "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7,
        "восемь" to 8, "девять" to 9, "десять" to 10, "одиннадцать" to 11,
        "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14,
        "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17,
        "восемнадцать" to 18, "девятнадцать" to 19, "двадцать" to 20,
        "двадцать один" to 21, "двадцать два" to 22, "двадцать три" to 23,
        "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50,
        "шестьдесят" to 60, "девяносто" to 90
    )

    private val weekdayForms = mapOf(
        "понедельник" to DayOfWeek.MONDAY,
        "понедельника" to DayOfWeek.MONDAY,
        "вторник" to DayOfWeek.TUESDAY,
        "вторника" to DayOfWeek.TUESDAY,
        "среду" to DayOfWeek.WEDNESDAY,
        "среда" to DayOfWeek.WEDNESDAY,
        "среды" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY,
        "четверга" to DayOfWeek.THURSDAY,
        "пятницу" to DayOfWeek.FRIDAY,
        "пятница" to DayOfWeek.FRIDAY,
        "пятницы" to DayOfWeek.FRIDAY,
        "субботу" to DayOfWeek.SATURDAY,
        "суббота" to DayOfWeek.SATURDAY,
        "субботы" to DayOfWeek.SATURDAY,
        "воскресенье" to DayOfWeek.SUNDAY,
        "воскресенья" to DayOfWeek.SUNDAY
    )

    private val monthForms = mapOf(
        "января" to Month.JANUARY,
        "февраля" to Month.FEBRUARY,
        "марта" to Month.MARCH,
        "апреля" to Month.APRIL,
        "мая" to Month.MAY,
        "июня" to Month.JUNE,
        "июля" to Month.JULY,
        "августа" to Month.AUGUST,
        "сентября" to Month.SEPTEMBER,
        "октября" to Month.OCTOBER,
        "ноября" to Month.NOVEMBER,
        "декабря" to Month.DECEMBER
    )

    private data class DateInfo(
        val date: LocalDate,
        val matched: String,
        val weekdayBased: Boolean = false
    )

    fun parse(raw: String, now: ZonedDateTime = ZonedDateTime.now()): ParsedReminder? {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return null

        val repeat = parseRepeat(normalized)
        val repeatRule = repeat?.first ?: Reminder.REPEAT_NONE
        val text = repeat?.second?.let { normalized.replace(it, " ") } ?: normalized

        parseDuration(text, now)?.let { (whenAt, matched) ->
            val title = cleanTitle(text.replace(matched, " "))
            return ParsedReminder(title, whenAt.toInstant().toEpochMilli(), repeatRule)
        }

        val timeInfo = parseTime(text)
        if (timeInfo == null) {
            if (repeatRule == Reminder.REPEAT_HOURLY && parseDate(text, now) == null) {
                val first = now.plusHours(1).withSecond(0).withNano(0)
                return ParsedReminder(
                    title = cleanTitle(text),
                    scheduledAt = first.toInstant().toEpochMilli(),
                    repeatRule = repeatRule
                )
            }
            return null
        }

        val (hour, minute, timeMatched) = timeInfo
        val dateInfo = parseDate(text, now)
        var targetDate = dateInfo?.date ?: now.toLocalDate()
        var target = targetDate.atTime(hour, minute).atZone(now.zone)

        if (dateInfo?.weekdayBased == true && !target.isAfter(now)) {
            targetDate = targetDate.plusWeeks(1)
            target = targetDate.atTime(hour, minute).atZone(now.zone)
        } else if (dateInfo == null && !target.isAfter(now)) {
            target = target.plusDays(1)
        }

        if (!target.isAfter(now)) return null

        var titleSource = text.replace(timeMatched, " ")
        dateInfo?.matched?.let { titleSource = titleSource.replace(it, " ") }

        return ParsedReminder(
            title = cleanTitle(titleSource),
            scheduledAt = target.toInstant().toEpochMilli(),
            repeatRule = repeatRule
        )
    }

    fun parseNeedsTime(raw: String, now: ZonedDateTime = ZonedDateTime.now()): PendingReminder? {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return null

        val repeat = parseRepeat(normalized)
        val repeatRule = repeat?.first ?: Reminder.REPEAT_NONE
        val text = repeat?.second?.let { normalized.replace(it, " ") } ?: normalized

        if (parseDuration(text, now) != null || parseTime(text) != null) return null

        val dateInfo = parseDate(text, now) ?: return null
        val title = cleanTitle(text.replace(dateInfo.matched, " "))

        return PendingReminder(
            title = title,
            targetDate = dateInfo.date,
            repeatRule = repeatRule,
            weekdayBased = dateInfo.weekdayBased
        )
    }

    fun completeWithTime(
        pending: PendingReminder,
        rawTime: String,
        now: ZonedDateTime = ZonedDateTime.now()
    ): ParsedReminder? {
        val text = normalize(rawTime)
        val timeInfo = parseTime(text) ?: return null
        val (hour, minute, _) = timeInfo

        var date = pending.targetDate
        var target = date.atTime(hour, minute).atZone(now.zone)
        if (pending.weekdayBased && !target.isAfter(now)) {
            date = date.plusWeeks(1)
            target = date.atTime(hour, minute).atZone(now.zone)
        }
        if (!target.isAfter(now)) return null

        return ParsedReminder(
            title = pending.title,
            scheduledAt = target.toInstant().toEpochMilli(),
            repeatRule = pending.repeatRule
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
        Regex("""(?<![а-я0-9])через\s+полчаса(?![а-я0-9])""").find(text)?.let {
            return now.plusMinutes(30) to it.value
        }
        Regex("""(?<![а-я0-9])через\s+четверть\s+часа(?![а-я0-9])""").find(text)?.let {
            return now.plusMinutes(15) to it.value
        }
        Regex("""(?<![а-я0-9])через\s+полтора\s+часа(?![а-я0-9])""").find(text)?.let {
            return now.plusMinutes(90) to it.value
        }
        Regex("""(?<![а-я0-9])через\s+(минуту|час|день|неделю)(?![а-я0-9])""").find(text)?.let { match ->
            val target = when (match.groupValues[1]) {
                "минуту" -> now.plusMinutes(1)
                "час" -> now.plusHours(1)
                "день" -> now.plusDays(1)
                "неделю" -> now.plusWeeks(1)
                else -> return@let
            }
            return target to match.value
        }

        val direct = Regex(
            """(?<![а-я0-9])через\s+([а-я0-9 ]+?)\s+(минуту|минуты|минут|час|часа|часов|день|дня|дней|неделю|недели|недель)(?![а-я0-9])"""
        ).find(text)
        if (direct != null) {
            val amount = parseNumber(direct.groupValues[1].trim())
            if (amount != null && amount > 0) {
                val target = addDuration(now, amount, direct.groupValues[2])
                if (target != null) return target to direct.value
            }
        }

        val reversed = Regex(
            """(?<![а-я0-9])(минуту|минуты|минут|час|часа|часов|день|дня|дней|неделю|недели|недель)\s+через\s+([а-я0-9 ]+?)(?=$|[,.;]|\s+(?:напомни|позвонить|сделать|купить|проверить))"""
        ).find(text)
        if (reversed != null) {
            val amount = parseNumber(reversed.groupValues[2].trim())
            if (amount != null && amount > 0) {
                val target = addDuration(now, amount, reversed.groupValues[1])
                if (target != null) return target to reversed.value
            }
        }

        val later = Regex(
            """(?<![а-я0-9])на\s+([а-я0-9 ]+?)\s+(минуту|минуты|минут|час|часа|часов|день|дня|дней)\s+позже(?:\s+текущего\s+времени)?(?![а-я0-9])"""
        ).find(text)
        if (later != null) {
            val amount = parseNumber(later.groupValues[1].trim())
            if (amount != null && amount > 0) {
                val target = addDuration(now, amount, later.groupValues[2])
                if (target != null) return target to later.value
            }
        }

        return null
    }

    private fun addDuration(now: ZonedDateTime, amount: Int, unit: String): ZonedDateTime? = when (unit) {
        "минуту", "минуты", "минут" -> now.plusMinutes(amount.toLong())
        "час", "часа", "часов" -> now.plusHours(amount.toLong())
        "день", "дня", "дней" -> now.plusDays(amount.toLong())
        "неделю", "недели", "недель" -> now.plusWeeks(amount.toLong())
        else -> null
    }

    private fun parseDate(text: String, now: ZonedDateTime): DateInfo? {
        Regex("""(?<![а-я0-9])послезавтра(?![а-я0-9])""").find(text)?.let {
            return DateInfo(now.toLocalDate().plusDays(2), it.value)
        }
        Regex("""(?<![а-я0-9])завтра(?![а-я0-9])""").find(text)?.let {
            return DateInfo(now.toLocalDate().plusDays(1), it.value)
        }
        Regex("""(?<![а-я0-9])сегодня(?![а-я0-9])""").find(text)?.let {
            return DateInfo(now.toLocalDate(), it.value)
        }

        val weekdayWords = weekdayForms.keys.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        val weekdayRegex = Regex(
            """(?<![а-я0-9])(?:(?:в|во|на)\s+)?(?:(?:этот|эту|следующий|следующую)\s+)?($weekdayWords)(?![а-я0-9])"""
        )
        weekdayRegex.find(text)?.let { match ->
            val day = weekdayForms[match.groupValues[1]] ?: return@let
            var candidate = now.with(TemporalAdjusters.nextOrSame(day)).toLocalDate()
            val strictNext = match.value.contains("следующ")
            if (strictNext && candidate == now.toLocalDate()) candidate = candidate.plusWeeks(1)
            return DateInfo(candidate, match.value, weekdayBased = true)
        }

        val numericDate = Regex(
            """(?<!\d)(?:на\s+)?(\d{1,2})[./](\d{1,2})(?:[./](\d{2,4}))?(?!\d)"""
        ).find(text)
        if (numericDate != null) {
            val day = numericDate.groupValues[1].toInt()
            val month = numericDate.groupValues[2].toInt()
            val yearText = numericDate.groupValues[3]
            var year = if (yearText.isBlank()) now.year else yearText.toInt().let { if (it < 100) 2000 + it else it }
            try {
                var date = LocalDate.of(year, month, day)
                if (yearText.isBlank() && date.isBefore(now.toLocalDate())) {
                    year += 1
                    date = LocalDate.of(year, month, day)
                }
                return DateInfo(date, numericDate.value)
            } catch (_: DateTimeException) {
                return null
            }
        }

        val monthWords = monthForms.keys.joinToString("|") { Regex.escape(it) }
        val wordDate = Regex(
            """(?<![а-я0-9])(?:на\s+)?(\d{1,2})\s+($monthWords)(?:\s+(\d{4}))?(?![а-я0-9])"""
        ).find(text)
        if (wordDate != null) {
            val day = wordDate.groupValues[1].toInt()
            val month = monthForms[wordDate.groupValues[2]] ?: return null
            val explicitYear = wordDate.groupValues[3]
            var year = explicitYear.toIntOrNull() ?: now.year
            try {
                var date = LocalDate.of(year, month, day)
                if (explicitYear.isBlank() && date.isBefore(now.toLocalDate())) {
                    year += 1
                    date = LocalDate.of(year, month, day)
                }
                return DateInfo(date, wordDate.value)
            } catch (_: DateTimeException) {
                return null
            }
        }

        return null
    }

    private fun parseTime(text: String): Triple<Int, Int, String>? {
        Regex("""(?<![а-я0-9])(?:в|на|к)?\s*полдень(?![а-я0-9])""").find(text)?.let {
            return Triple(12, 0, it.value)
        }
        Regex("""(?<![а-я0-9])(?:в|на|к)?\s*полночь(?![а-я0-9])""").find(text)?.let {
            return Triple(0, 0, it.value)
        }

        val numericWithPrefix = Regex(
            """(?<![а-я0-9])(?:в|на|к)\s+([01]?\d|2[0-3])(?:(?::|\.)([0-5]\d))?\s*(утра|дня|вечера|ночи)?(?![а-я0-9])"""
        ).find(text)
        if (numericWithPrefix != null) {
            var hour = numericWithPrefix.groupValues[1].toInt()
            val minute = numericWithPrefix.groupValues[2].ifBlank { "0" }.toInt()
            hour = adjustPartOfDay(hour, numericWithPrefix.groupValues[3])
            return Triple(hour, minute, numericWithPrefix.value)
        }

        val standaloneClock = Regex(
            """(?<![\d:])([01]?\d|2[0-3])(?::|\.)([0-5]\d)(?!\d)"""
        ).find(text)
        if (standaloneClock != null) {
            return Triple(
                standaloneClock.groupValues[1].toInt(),
                standaloneClock.groupValues[2].toInt(),
                standaloneClock.value
            )
        }

        val words = numberWords.keys
            .filter { (numberWords[it] ?: 99) <= 23 }
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        val wordRegex = Regex(
            """(?<![а-я0-9])(?:в|на|к)\s+($words)\s*(утра|дня|вечера|ночи)?(?![а-я0-9])"""
        )
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
            "ночи", "утра" -> if (hour == 12) hour = 0
        }
        return hour.coerceIn(0, 23)
    }

    private fun parseNumber(value: String): Int? {
        val normalized = value.trim()
        normalized.toIntOrNull()?.let { return it }
        numberWords[normalized]?.let { return it }

        val parts = normalized.split(" ").filter { it.isNotBlank() }
        if (parts.size == 2) {
            val first = numberWords[parts[0]]
            val second = numberWords[parts[1]]
            if (first != null && second != null && first >= 20 && second in 1..9) {
                return first + second
            }
        }
        return null
    }

    private fun cleanTitle(value: String): String {
        var title = value
            .replace(Regex("""(?<![а-я0-9])(напомни|напомнить|поставь|поставить|создай|создать)(?![а-я0-9])"""), " ")
            .replace(Regex("""(?<![а-я0-9])(мне|пожалуйста)(?![а-я0-9])"""), " ")
            .replace(Regex("""(?<![а-я0-9])(задачу|напоминание)(?![а-я0-9])"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '-', '—', ':')

        while (title.startsWith("в ") || title.startsWith("во ") || title.startsWith("на ") || title.startsWith("к ")) {
            title = title.substringAfter(' ').trim()
        }

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
