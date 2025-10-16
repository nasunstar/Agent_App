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
        val endTimeMillis: Long? = null, // 범위 검색을 위한 종료 시간 (주/월 단위)
    )

    private val fullDatePattern =
        Regex("""\b(\d{4})[./-](\d{1,2})[./-](\d{1,2})(?:\s+(\d{1,2})(?::(\d{2}))?)?\b""")
    private val monthDayPattern =
        Regex("""\b(\d{1,2})[/-](\d{1,2})(?:\s+(\d{1,2})(?::(\d{2}))?)?\b""")
    private val koreanDatePattern =
        Regex("""(\d{1,2})월\s*(\d{1,2})일(?:\s*(오전|오후|AM|PM|am|pm)?\s*(\d{1,2})시(?:\s*(\d{1,2})분)?)?""")
    private val timeOnlyPattern =
        Regex("""(오전|오후|AM|PM|am|pm)?\s*(\d{1,2})(?:[:시]\s*(\d{1,2}))?""")
    // 일반화된 주간 패턴 매칭 - 더 유연한 패턴 지원
    private val weekPattern =
        Regex("""(다음|이번|지난)\s*주(?:\s*(월|화|수|목|금|토|일)요일?)?""")
    
    // 추가 패턴들
    private val dayPattern =
        Regex("""(내일|모레|오늘|어제|그저께)""")
    
    private val relativeDayPattern =
        Regex("""(\d+)\s*일\s*(후|전|뒤|앞)""")
    
    private val monthPattern =
        Regex("""(다음|이번|지난)\s*달""")

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
        // 일반화된 주간 패턴 매칭
        weekPattern.find(text)?.let { match ->
            val weekType = match.groupValues[1] // "다음", "이번", "지난"
            val weekdayToken = match.groupValues.getOrNull(2) // "월", "화", "수", etc.
            
            val offsetWeeks = when (weekType) {
                "다음" -> 1
                "이번" -> 0
                "지난" -> -1
                else -> 0
            }
            
            // 요일이 지정되지 않은 경우 (예: "다음주") 주 전체 범위 반환
            if (weekdayToken.isNullOrBlank()) {
                val weekStart = resolveWeekday(now, offsetWeeks, "월") // 월요일 시작
                val weekEnd = resolveWeekday(now, offsetWeeks, "일") // 일요일 끝
                val startResolved = weekStart.withHour(0).withMinute(0).withSecond(0).withNano(0)
                val endResolved = weekEnd.withHour(23).withMinute(59).withSecond(59).withNano(0)
                return Resolution(
                    timestampMillis = startResolved.toInstant().toEpochMilli(),
                    confidence = 0.7,
                    endTimeMillis = endResolved.toInstant().toEpochMilli()
                )
            }
            
            // 요일이 지정된 경우 (예: "다음주 수요일") 특정 날짜 반환
            val target = resolveWeekday(now, offsetWeeks, weekdayToken)
            val resolved = targetTime(target)
            return Resolution(resolved.toInstant().toEpochMilli(), 0.7)
        }
        
        // 다음달, 이번달, 지난달 패턴 매칭
        monthPattern.find(text)?.let { match ->
            val monthType = match.groupValues[1] // "다음", "이번", "지난"
            val targetMonth = when (monthType) {
                "다음" -> now.plusMonths(1)
                "이번" -> now
                "지난" -> now.minusMonths(1)
                else -> now
            }
            val monthStart = targetMonth.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
            val monthEnd = targetMonth.withDayOfMonth(targetMonth.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(0)
            return Resolution(
                timestampMillis = monthStart.toInstant().toEpochMilli(),
                confidence = 0.7,
                endTimeMillis = monthEnd.toInstant().toEpochMilli()
            )
        }
        
        // 월별 상대적 표현 처리 (일반화된 패턴)
        if (lower.contains("월 이후") || lower.contains("월부터")) {
            val monthPattern = Regex("""(\d{1,2})월\s*(이후|부터)""")
            monthPattern.find(lower)?.let { match ->
                val month = match.groupValues[1].toIntOrNull()
                if (month != null && month in 1..12) {
                    val monthStart = now.withMonth(month).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
                    return Resolution(monthStart.toInstant().toEpochMilli(), 0.7)
                }
            }
        }
        // 일반화된 날짜 패턴 매칭
        dayPattern.find(text)?.let { match ->
            val dayType = match.groupValues[1]
            val base = when (dayType) {
                "내일", "tomorrow" -> now.plusDays(1)
                "모레" -> now.plusDays(2)
                "오늘", "today" -> now
                "어제" -> now.minusDays(1)
                "그저께" -> now.minusDays(2)
                else -> now
            }
            return Resolution(targetTime(base).toInstant().toEpochMilli(), 0.65)
        }
        
        // 상대적 일수 패턴 매칭 (예: "3일 후", "5일 전")
        relativeDayPattern.find(text)?.let { match ->
            val days = match.groupValues[1].toIntOrNull() ?: return@let
            val direction = match.groupValues[2]
            val base = when (direction) {
                "후", "뒤" -> now.plusDays(days.toLong())
                "전", "앞" -> now.minusDays(days.toLong())
                else -> now
            }
            return Resolution(targetTime(base).toInstant().toEpochMilli(), 0.6)
        }
        
        // 영어 패턴 매칭
        if (lower.contains("next week")) {
            val target = resolveWeekday(now, offsetWeeks = 1, weekdayToken = null)
            val resolved = targetTime(target)
            return Resolution(resolved.toInstant().toEpochMilli(), 0.6)
        }
        return null
    }

    private fun resolveWeekday(now: ZonedDateTime, offsetWeeks: Int, weekdayToken: String?): ZonedDateTime {
        val weekday = weekdayToken?.takeIf { it.isNotBlank() }
            ?.let { token ->
                val trimmed = token.take(1)
                weekdayMap[trimmed]
            }
        val targetDay = weekday ?: DayOfWeek.MONDAY
        
        return when (offsetWeeks) {
            1 -> {
                // 다음주: 현재 요일에서 다음주 같은 요일로
                // 10월 13일(월요일) → 다음주 수요일 = 10월 22일 (9일 후)
                // 공식: 다음주이므로 무조건 7일 이상 더해야 함
                val daysUntilTarget = (targetDay.value - now.dayOfWeek.value + 7) % 7
                val daysToAdd = if (daysUntilTarget == 0) 7 else daysUntilTarget + 7
                now.plusDays(daysToAdd.toLong())
            }
            0 -> {
                // 이번주: 현재 요일에서 이번주 같은 요일로
                val daysUntilTarget = (targetDay.value - now.dayOfWeek.value + 7) % 7
                val daysToAdd = if (daysUntilTarget == 0) 0 else daysUntilTarget
                now.plusDays(daysToAdd.toLong())
            }
            -1 -> {
                // 지난주: 현재 요일에서 지난주 같은 요일로
                val daysUntilTarget = (targetDay.value - now.dayOfWeek.value + 7) % 7
                val daysToAdd = if (daysUntilTarget == 0) -7 else daysUntilTarget - 7
                now.plusDays(daysToAdd.toLong())
            }
            else -> {
                // 기타: 기존 로직 사용
                val base = now.plusWeeks(offsetWeeks.toLong())
                var result = base
                while (result.dayOfWeek != targetDay) {
                    result = result.plusDays(1)
                }
                result
            }
        }
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
