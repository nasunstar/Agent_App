package com.example.agent_app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Parses simple natural language date/time expressions into absolute epoch millisecond values.
 */
object TimeResolver {
    private val zone: ZoneId = ZoneId.of("Asia/Seoul")

    data class Resolution(
        val timestampMillis: Long,
        val confidence: Double,
    )

    private val fullDatePattern =
        Regex("""\b(\d{4})[./-](\d{1,2})[./-](\d{1,2})(?:\s+(\d{1,2})(?::(\d{2}))?)?\b""")
    private val monthDayPattern =
        Regex("""\b(\d{1,2})[/-](\d{1,2})(?:\s+(\d{1,2})(?::(\d{2}))?)?\b""")
    private val koreanDatePattern =
        Regex("""(\d{1,2})월\s*(\d{1,2})일(?:\s*(오전|오후|AM|PM|am|pm)?\s*(\d{1,2})시(?:\s*(\d{1,2})분)?)?""")
    private val timeOnlyPattern =
        Regex("""(오전|오후|AM|PM|am|pm)?\s*(\d{1,2})(?:[:시]\s*(\d{1,2}))?""")
    private val nextWeekPattern =
        Regex("""다음\s*주\s*(월|화|수|목|금|토|일)?요일?""")
    private val thisWeekPattern =
        Regex("""이번\s*주\s*(월|화|수|목|금|토|일)?요일?""")

    private val weekdayMap = mapOf(
        "월" to DayOfWeek.MONDAY,
        "화" to DayOfWeek.TUESDAY,
        "수" to DayOfWeek.WEDNESDAY,
        "목" to DayOfWeek.THURSDAY,
        "금" to DayOfWeek.FRIDAY,
        "토" to DayOfWeek.SATURDAY,
        "일" to DayOfWeek.SUNDAY,
    )

    fun resolve(text: String, now: ZonedDateTime = ZonedDateTime.now(zone)): Resolution? {
        fullDatePattern.find(text)?.let { match ->
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            val (hour, minute) = extractTime(match.groupValues.getOrNull(4), match.groupValues.getOrNull(5), text)
            val dateTime = LocalDateTime.of(year, month, day, hour, minute)
            return Resolution(dateTime.atZone(zone).toInstant().toEpochMilli(), confidenceForExplicit(hourProvided = match.groupValues[4].isNotBlank()))
        }

        koreanDatePattern.find(text)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val ampm = match.groupValues.getOrNull(3)
            val hourGroup = match.groupValues.getOrNull(4)
            val minuteGroup = match.groupValues.getOrNull(5)
            val (year, resolvedMonth) = resolveYearAndMonth(now.toLocalDate(), month)
            val (hour, minute) = extractTime(hourGroup, minuteGroup, text, ampmHint = ampm)
            val dateTime = LocalDateTime.of(year, resolvedMonth, day, hour, minute)
            return Resolution(dateTime.atZone(zone).toInstant().toEpochMilli(), confidenceForExplicit(hourGroup?.isNotBlank() == true))
        }

        monthDayPattern.find(text)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val (year, resolvedMonth) = resolveYearAndMonth(now.toLocalDate(), month)
            val (hour, minute) = extractTime(match.groupValues.getOrNull(3), match.groupValues.getOrNull(4), text)
            val dateTime = LocalDateTime.of(year, resolvedMonth, day, hour, minute)
            return Resolution(dateTime.atZone(zone).toInstant().toEpochMilli(), confidenceForExplicit(match.groupValues[3].isNotBlank()))
        }

        parseRelative(text, now)?.let { return it }

        return null
    }

    private fun confidenceForExplicit(hourProvided: Boolean): Double = if (hourProvided) 0.9 else 0.8

    private fun resolveYearAndMonth(baseDate: LocalDate, month: Int): Pair<Int, Int> {
        var year = baseDate.year
        var resolvedMonth = month
        if (month < 1 || month > 12) {
            return year to baseDate.monthValue
        }
        val candidate = LocalDate.of(year, month, 1)
        if (candidate.isBefore(baseDate.withDayOfMonth(1))) {
            year += 1
        }
        resolvedMonth = month
        return year to resolvedMonth
    }

    private fun parseRelative(text: String, now: ZonedDateTime): Resolution? {
        val lower = text.lowercase(Locale.getDefault())
        val timePair = extractTimeFromText(text)
        val targetTime = { base: ZonedDateTime ->
            val hour = timePair?.first ?: 9
            val minute = timePair?.second ?: 0
            base.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        }
        when {
            lower.contains("내일") || lower.contains("tomorrow") -> {
                val base = now.plusDays(1)
                return Resolution(targetTime(base).toInstant().toEpochMilli(), 0.65)
            }
            lower.contains("모레") -> {
                val base = now.plusDays(2)
                return Resolution(targetTime(base).toInstant().toEpochMilli(), 0.6)
            }
            lower.contains("오늘") || lower.contains("today") -> {
                return Resolution(targetTime(now).toInstant().toEpochMilli(), 0.55)
            }
        }
        nextWeekPattern.find(text)?.let { match ->
            val target = resolveWeekday(now, offsetWeeks = 1, weekdayToken = match.groupValues.getOrNull(1))
            val resolved = targetTime(target)
            return Resolution(resolved.toInstant().toEpochMilli(), 0.6)
        }
        thisWeekPattern.find(text)?.let { match ->
            val target = resolveWeekday(now, offsetWeeks = 0, weekdayToken = match.groupValues.getOrNull(1))
            val resolved = targetTime(target)
            return Resolution(resolved.toInstant().toEpochMilli(), 0.55)
        }
        if (lower.contains("next week")) {
            val target = resolveWeekday(now, offsetWeeks = 1, weekdayToken = null)
            val resolved = targetTime(target)
            return Resolution(resolved.toInstant().toEpochMilli(), 0.6)
        }
        return null
    }

    private fun resolveWeekday(now: ZonedDateTime, offsetWeeks: Int, weekdayToken: String?): ZonedDateTime {
        val base = now.plusWeeks(offsetWeeks.toLong())
        val weekday = weekdayToken?.takeIf { it.isNotBlank() }
            ?.let { token ->
                val trimmed = token.take(1)
                weekdayMap[trimmed]
            }
        val targetDay = weekday ?: DayOfWeek.MONDAY
        var result = base
        while (result.dayOfWeek != targetDay) {
            result = result.plusDays(1)
        }
        return result
    }

    private fun extractTime(hourGroup: String?, minuteGroup: String?, fullText: String, ampmHint: String? = null): Pair<Int, Int> {
        if (!hourGroup.isNullOrBlank()) {
            val hourValue = hourGroup.toInt()
            val minuteValue = minuteGroup?.takeIf { it.isNotBlank() }?.toInt() ?: 0
            return adjustForMeridiem(hourValue, minuteValue, ampmHint, fullText)
        }
        return extractTimeFromText(fullText) ?: (9 to 0)
    }

    private fun extractTimeFromText(text: String): Pair<Int, Int>? {
        val match = timeOnlyPattern.find(text) ?: return null
        val raw = match.value
        val ampm = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        val hourValue = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val minuteValue = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
        val containsMarker = raw.contains(":") || raw.contains("시")
        if (ampm == null && !containsMarker) {
            return null
        }
        return adjustForMeridiem(hourValue, minuteValue, ampm, text)
    }

    private fun adjustForMeridiem(hour: Int, minute: Int, ampm: String?, text: String): Pair<Int, Int> {
        var resolvedHour = hour
        val normalized = ampm?.lowercase(Locale.getDefault()) ?: text.lowercase(Locale.getDefault())
        val isPm = normalized.contains("오후") || normalized.contains("pm")
        val isAm = normalized.contains("오전") || normalized.contains("am")
        if (isPm && resolvedHour < 12) {
            resolvedHour += 12
        } else if (isAm && resolvedHour == 12) {
            resolvedHour = 0
        }
        return resolvedHour.coerceIn(0, 23) to minute.coerceIn(0, 59)
    }
}
