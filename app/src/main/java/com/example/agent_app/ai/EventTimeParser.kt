package com.example.agent_app.ai

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 시간 표현을 규칙 기반으로 추출/해석하기 위한 유틸리티.
 *
 * 기존 HuenDongMinAiAgent에서 LLM 의존도를 줄이기 위해 우선
 * 모든 입력 텍스트를 동일한 규칙으로 선처리한 뒤 결과를 AI 혹은
 * 로컬 로직이 재사용할 수 있도록 한다.
 */
enum class TimeExprKind {
    ABSOLUTE_DATE,
    RELATIVE_DATE,
    WEEKDAY,
    TIME_OF_DAY,
    RANGE,
    DURATION
}

data class TimeExpression(
    val text: String,
    val kind: TimeExprKind,
    val startIndex: Int,
    val endIndex: Int,
    val meta: Map<String, Any?>
)

data class ResolveContext(
    val referenceEpochMs: Long,
    val timeZoneId: String = "Asia/Seoul"
) {
    val zoneId: ZoneId = ZoneId.of(timeZoneId)
    val referenceDate: ZonedDateTime =
        Instant.ofEpochMilli(referenceEpochMs).atZone(zoneId)
}

data class CandidateTimeWindow(
    val start: ZonedDateTime,
    val end: ZonedDateTime?,
    val allDay: Boolean,
    val sourceText: String,
    val confidence: Double = 1.0
) {
    val startEpochMs: Long get() = start.toInstant().toEpochMilli()
    val endEpochMs: Long? get() = end?.toInstant()?.toEpochMilli()
}

object EventTimeParser {

    private val absoluteWithYear = Regex("""(\d{4})[\.\/\-년\s]*?(\d{1,2})[\.\/\-월\s]*?(\d{1,2})(?:일)?""")
    private val monthDayPattern = Regex("""(?<!\d)(\d{1,2})[\.\/월\s]*(\d{1,2})(?:일)?""")
    private val rangePattern = Regex("""(?<!\d)(\d{1,2})[\.\/](\d{1,2})\s*[~\-]\s*(\d{1,2})""")
    private val timePattern = Regex("""(오전|오후|AM|PM|am|pm)?\s*(\d{1,2})(?:시|:)\s*(\d{0,2})""")
    // "X월 Y일" 형식에서 "일"이 날짜의 일(day)로 인식되지 않도록 수정
    // "X일 동안" 또는 "X일간" 같은 명시적 기간 표현만 매칭 (단, "X월 Y일" 형식 제외)
    private val durationHoursPattern = Regex("""(\d+)\s*(시간|hour|시간짜리)(?:\s*(?:동안|간))?""", RegexOption.IGNORE_CASE)
    // Android ICU 정규식은 가변 길이 look-behind를 지원하지 않으므로, 매칭 후 검증하는 방식으로 변경
    private val durationDaysPattern = Regex("""(\d+)\s*일\s*(?:동안|간)""", RegexOption.IGNORE_CASE)
    private val monthDayBeforePattern = Regex("""(\d{1,2})\s*월\s*(\d+)\s*일""")
    private val relativeKeywords = mapOf(
        "오늘" to 0,
        "내일" to 1,
        "모레" to 2,
        "글피" to 3,
        "어제" to -1,
        "그저께" to -2
        // "이번", "다음"은 WEEKDAY 패턴에서 처리하므로 제외
    )
    private val relativePattern = Regex("""(\d+)\s*(일|주)\s*(뒤|후|후에)""")
    private val weekdayPattern =
        Regex("""(?:(이번|다음)\s*주\s*)?((?:월|화|수|목|금|토|일)요일)""")

    fun extractTimeExpressions(text: String): List<TimeExpression> {
        val expressions = mutableListOf<TimeExpression>()

        absoluteWithYear.findAll(text).forEach { match ->
            expressions += TimeExpression(
                text = match.value,
                kind = TimeExprKind.ABSOLUTE_DATE,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                meta = mapOf(
                    "year" to match.groupValues[1].toInt(),
                    "month" to match.groupValues[2].toInt(),
                    "day" to match.groupValues[3].toInt()
                )
            )
        }

        rangePattern.findAll(text).forEach { match ->
            val month = match.groupValues[1].toInt()
            val startDay = match.groupValues[2].toInt()
            val endDay = match.groupValues[3].toInt()
            expressions += TimeExpression(
                text = match.value,
                kind = TimeExprKind.RANGE,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                meta = mapOf(
                    "month" to month,
                    "startDay" to startDay,
                    "endDay" to endDay
                )
            )
        }

        monthDayPattern.findAll(text).forEach { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            expressions += TimeExpression(
                text = match.value,
                kind = TimeExprKind.ABSOLUTE_DATE,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                meta = mapOf(
                    "month" to month,
                    "day" to day
                )
            )
        }

        weekdayPattern.findAll(text).forEach { match ->
            val modifier = match.groupValues[1]
            val weekday = match.groupValues[2]
            expressions += TimeExpression(
                text = match.value,
                kind = TimeExprKind.WEEKDAY,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                meta = mapOf(
                    "weekday" to weekday,
                    "weekOffset" to when (modifier) {
                        "다음" -> 1
                        "이번" -> 0
                        else -> 0
                    }
                )
            )
        }

        relativeKeywords.forEach { (keyword, offset) ->
            Regex(keyword).findAll(text).forEach { match ->
                expressions += TimeExpression(
                    text = match.value,
                    kind = TimeExprKind.RELATIVE_DATE,
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                    meta = mapOf("offsetDays" to offset)
                )
            }
        }

        relativePattern.findAll(text).forEach { match ->
            val amount = match.groupValues[1].toInt()
            val unit = match.groupValues[2]
            val offsetDays = if (unit == "주") amount * 7 else amount
            expressions += TimeExpression(
                text = match.value,
                kind = TimeExprKind.RELATIVE_DATE,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                meta = mapOf("offsetDays" to offsetDays)
            )
        }

        timePattern.findAll(text).forEach { match ->
            val meridiem = match.groupValues[1]
            val hour = match.groupValues[2].toInt()
            val minute = match.groupValues[3].takeIf { it.isNotBlank() }?.toInt() ?: 0
            expressions += TimeExpression(
                text = match.value,
                kind = TimeExprKind.TIME_OF_DAY,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                meta = mapOf(
                    "hour" to hour,
                    "minute" to minute,
                    "meridiem" to meridiem
                )
            )
        }

        // 시간 기간: "3시간", "3시간 동안" 등
        durationHoursPattern.findAll(text).forEach { match ->
            val amount = match.groupValues[1].toInt()
            val unit = match.groupValues[2]
            expressions += TimeExpression(
                text = match.value,
                kind = TimeExprKind.DURATION,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                meta = mapOf(
                    "amount" to amount,
                    "unit" to unit
                )
            )
        }
        
        // 일 기간: "3일 동안", "3일간" 등 (단, "11월 30일" 형식 제외)
        durationDaysPattern.findAll(text).forEach { match ->
            // "X월 Y일" 형식인지 확인 - 매칭된 부분 앞에 "X월 Y일" 패턴이 있는지 검사
            val startIdx = match.range.first
            val matchedDay = match.groupValues[1].toIntOrNull()
            
            // 앞부분에 "X월 Y일" 패턴이 있고, 그 "일" 부분이 현재 매칭과 겹치는지 확인
            val isMonthDayFormat = matchedDay != null && monthDayBeforePattern.findAll(text).any { monthDayMatch ->
                val monthDayEnd = monthDayMatch.range.last
                val monthDayDay = monthDayMatch.groupValues[2].toIntOrNull()
                // "X월 Y일" 패턴의 "일" 부분이 현재 "X일 동안" 매칭과 겹치거나 가까운지 확인
                monthDayDay == matchedDay && monthDayEnd >= startIdx - 5 && monthDayEnd <= startIdx + 2
            }
            
            // "X월 Y일" 형식이 아니면 기간으로 인식
            if (!isMonthDayFormat) {
                val amount = matchedDay ?: match.groupValues[1].toInt()
                expressions += TimeExpression(
                    text = match.value,
                    kind = TimeExprKind.DURATION,
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                    meta = mapOf(
                        "amount" to amount,
                        "unit" to "일"
                    )
                )
            }
        }

        return expressions.sortedBy { it.startIndex }
    }

    fun resolveExpressions(
        text: String,
        expressions: List<TimeExpression>,
        context: ResolveContext
    ): List<CandidateTimeWindow> {
        if (expressions.isEmpty()) return emptyList()

        val baseDate = context.referenceDate.toLocalDate()
        var targetDate: LocalDate? = null
        var targetEndDate: LocalDate? = null  // RANGE 표현의 종료 날짜
        var targetHour = 0
        var targetMinute = 0
        var hasTime = false
        var durationHours: Int? = null
        var durationDays: Int? = null

        // 1. 명시적 날짜 찾기 (최우선)
        val absoluteDate = expressions.firstOrNull {
            it.kind == TimeExprKind.ABSOLUTE_DATE || it.kind == TimeExprKind.RANGE
        }
        if (absoluteDate != null) {
            when (absoluteDate.kind) {
                TimeExprKind.ABSOLUTE_DATE -> {
                    val year = absoluteDate.meta["year"] as? Int ?: baseDate.year
                    val month = absoluteDate.meta["month"] as? Int
                    val day = absoluteDate.meta["day"] as? Int
                    if (month != null && day != null) {
                        targetDate = try {
                            LocalDate.of(year, month, day)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                TimeExprKind.RANGE -> {
                    val month = absoluteDate.meta["month"] as? Int
                    val startDay = absoluteDate.meta["startDay"] as? Int
                    val endDay = absoluteDate.meta["endDay"] as? Int
                    if (month != null && startDay != null) {
                        targetDate = try {
                            LocalDate.of(baseDate.year, month, startDay)
                        } catch (e: Exception) {
                            null
                        }
                        // RANGE의 종료 날짜도 설정
                        if (endDay != null && targetDate != null) {
                            targetEndDate = try {
                                LocalDate.of(baseDate.year, month, endDay)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        // 2. 상대적 표현 처리
        if (targetDate == null) {
            targetDate = baseDate
        }
        expressions.filter { it.kind == TimeExprKind.RELATIVE_DATE || it.kind == TimeExprKind.WEEKDAY }
            .forEach { expr ->
                when (expr.kind) {
                    TimeExprKind.RELATIVE_DATE -> {
                        val offset = expr.meta["offsetDays"] as? Int ?: 0
                        targetDate = targetDate?.plusDays(offset.toLong())
                    }
                    TimeExprKind.WEEKDAY -> {
                        val weekdayStr = expr.meta["weekday"] as? String ?: ""
                        val weekOffset = expr.meta["weekOffset"] as? Int ?: 0
                        val weekday = parseWeekday(weekdayStr)
                        if (weekday != null && targetDate != null) {
                            val currentWeekday = targetDate.dayOfWeek.value
                            val targetWeekday = weekday.value
                            
                            if (weekOffset == 0) {
                                // "이번주" 또는 요일만: 현재 날짜 이후 가장 가까운 해당 요일
                                var daysToAdd = (targetWeekday - currentWeekday + 7) % 7
                                if (daysToAdd == 0) daysToAdd = 7 // 같은 요일이면 다음 주
                                targetDate = targetDate.plusDays(daysToAdd.toLong())
                            } else {
                                // "다음주": 다음 주의 해당 요일 (일요일~토요일 기준)
                                // 현재 주의 일요일 찾기
                                val daysFromSunday = if (currentWeekday == 7) 0 else currentWeekday
                                val thisWeekSunday = targetDate.minusDays(daysFromSunday.toLong())
                                // 다음 주 일요일
                                val nextWeekSunday = thisWeekSunday.plusDays(7)
                                // 다음 주의 해당 요일 (일요일=7, 월요일=1, ..., 토요일=6)
                                val daysToTargetWeekday = if (targetWeekday == 7) {
                                    0L // 일요일
                                } else {
                                    targetWeekday.toLong() // 월요일=1, 화요일=2, ..., 토요일=6
                                }
                                targetDate = nextWeekSunday.plusDays(daysToTargetWeekday)
                            }
                        }
                    }
                    else -> {}
                }
            }

        // 3. 시간 처리
        val timeExpr = expressions.firstOrNull { it.kind == TimeExprKind.TIME_OF_DAY }
        if (timeExpr != null) {
            val hour = timeExpr.meta["hour"] as? Int ?: 0
            val minute = timeExpr.meta["minute"] as? Int ?: 0
            val meridiem = timeExpr.meta["meridiem"] as? String ?: ""
            targetHour = when {
                meridiem.contains("오후", ignoreCase = true) || meridiem.contains("PM", ignoreCase = true) -> {
                    if (hour < 12) hour + 12 else hour
                }
                meridiem.contains("오전", ignoreCase = true) || meridiem.contains("AM", ignoreCase = true) -> {
                    if (hour == 12) 0 else hour
                }
                else -> hour
            }
            targetMinute = minute
            hasTime = true
        }

        // 4. 기간 처리
        val durationExpr = expressions.firstOrNull { it.kind == TimeExprKind.DURATION }
        if (durationExpr != null) {
            val amount = durationExpr.meta["amount"] as? Int
            val unit = (durationExpr.meta["unit"] as? String).orEmpty()
            if (amount != null) {
                if (unit.contains("시간", ignoreCase = true) || unit.contains("hour", ignoreCase = true)) {
                    durationHours = amount
                } else if (unit.contains("일", ignoreCase = true)) {
                    durationDays = amount
                }
            }
        }

        // 최종 날짜/시간 생성
        val finalDate = targetDate ?: baseDate
        val startDateTime = finalDate.atTime(targetHour, targetMinute).atZone(context.zoneId)
        
        // 종료 시간 결정 우선순위:
        // 1. RANGE 표현의 종료 날짜가 있으면 사용
        // 2. 기간(DURATION)이 명시되어 있으면 사용
        // 3. 시간이 있으면 기본 1시간
        // 4. 종일 이벤트(allDay)면 다음날 00:00
        val endDateTime = when {
            targetEndDate != null -> {
                // RANGE 표현: 종료 날짜의 00:00 (하루 종일)
                targetEndDate.plusDays(1).atTime(0, 0).atZone(context.zoneId)
            }
            durationDays != null -> {
                startDateTime.plusDays(durationDays.toLong())
            }
            durationHours != null -> {
                startDateTime.plusHours(durationHours.toLong())
            }
            hasTime -> {
                startDateTime.plusHours(1) // 기본 1시간
            }
            else -> {
                // 종일 이벤트: 다음날 00:00
                finalDate.plusDays(1).atTime(0, 0).atZone(context.zoneId)
            }
        }

        return listOf(
            CandidateTimeWindow(
                start = startDateTime,
                end = endDateTime,
                allDay = !hasTime,
                sourceText = text,
                confidence = 1.0
            )
        )
    }

    private fun parseWeekday(weekdayStr: String): DayOfWeek? {
        return when {
            weekdayStr.contains("월") -> DayOfWeek.MONDAY
            weekdayStr.contains("화") -> DayOfWeek.TUESDAY
            weekdayStr.contains("수") -> DayOfWeek.WEDNESDAY
            weekdayStr.contains("목") -> DayOfWeek.THURSDAY
            weekdayStr.contains("금") -> DayOfWeek.FRIDAY
            weekdayStr.contains("토") -> DayOfWeek.SATURDAY
            weekdayStr.contains("일") -> DayOfWeek.SUNDAY
            else -> null
        }
    }
}

// 확장 함수: ZonedDateTime을 문자열로 변환
private fun ZonedDateTime.formatDateString(): String {
    return "${year}-${monthValue.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"
}

private fun ZonedDateTime.formatTimeString(): String {
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

