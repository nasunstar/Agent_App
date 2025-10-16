package com.example.agent_app.domain.chat.usecase

import com.example.agent_app.domain.chat.model.QueryFilters
import com.example.agent_app.util.TimeResolver
import java.time.ZoneId
import java.time.ZonedDateTime

class ProcessUserQueryUseCase(
    private val timeResolver: TimeResolverAdapter = TimeResolverAdapter(),
) {
    operator fun invoke(rawQuestion: String): QueryFilters {
        val trimmed = rawQuestion.trim()
        if (trimmed.isEmpty()) {
            return QueryFilters()
        }
        val resolution = timeResolver.resolve(trimmed)
        val (start, end) = when {
            resolution == null -> null to null
            // 범위가 명시된 경우 (예: "다음주", "이번달") 그대로 사용
            resolution.endTimeMillis != null -> resolution.timestampMillis to resolution.endTimeMillis
            // 단일 시점인 경우 (예: "10월 17일 이후") 60일 윈도우 생성
            else -> windowFor(resolution.timestampMillis)
        }
        val keywords = extractKeywords(trimmed)
        val source = detectSource(trimmed)
        
        android.util.Log.d("ProcessUserQueryUseCase", "질문: $trimmed")
        android.util.Log.d("ProcessUserQueryUseCase", "TimeResolver 결과: $resolution")
        android.util.Log.d("ProcessUserQueryUseCase", "시간 범위: start=$start, end=$end")
        android.util.Log.d("ProcessUserQueryUseCase", "키워드: $keywords")
        android.util.Log.d("ProcessUserQueryUseCase", "소스: $source")
        
        return QueryFilters(
            startTimeMillis = start,
            endTimeMillis = end,
            source = source,
            keywords = keywords,
        )
    }

    private fun windowFor(center: Long): Pair<Long, Long> {
        // 여유 있는 윈도우(미래 60일)로 관련 데이터 포함
        val windowSize = 60 * 24 * 60 * 60 * 1000L // 60일
        return center to (center + windowSize)
    }

    private fun extractKeywords(text: String): List<String> {
        val lower = text.lowercase()
        val keywords = mutableListOf<String>()

        // 도메인 힌트
        if (lower.contains("google") && (lower.contains("메일") || lower.contains("이메일"))) {
            keywords.add("gmail")
        }
        if (lower.contains("github")) keywords.add("github")
        if (lower.contains("steam")) keywords.add("steam")
        if (lower.contains("openai")) keywords.add("openai")
        if (lower.contains("이메일") || lower.contains("메일")) keywords.add("email")
        if (lower.contains("약속") || lower.contains("만나") || lower.contains("미팅") || lower.contains("appointment")) {
            keywords.add("meeting")
        }

        // 기본 토큰화
        val tokens = lower
            .split(" ", "\n", "\t", ",", ".", "?", "!", ":", ";", "에서", "으로", "에게", "가", "은", "는", "을", "를")
            .mapNotNull { token ->
                val trimmed = token.trim()
                trimmed.takeIf { it.length >= 2 && it !in STOPWORDS }
            }
        keywords.addAll(tokens)

        // 날짜/시간성 토큰 제거는 항상 수행 (TimeResolver 성공 여부와 무관)
        val sanitized = sanitizeDateTimeTokens(keywords)
        return sanitized.distinct().take(8)
    }

    private fun detectSource(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("gmail") || lower.contains("email") ||
            lower.contains("이메일") || lower.contains("메일") ||
            (lower.contains("google") && (lower.contains("메일") || lower.contains("이메일"))) -> "gmail"
            lower.contains("sms") -> "sms"
            lower.contains("ocr") -> "ocr"
            else -> null
        }
    }

    class TimeResolverAdapter(
        private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
    ) {
        fun resolve(text: String): TimeResolver.Resolution? = TimeResolver.resolve(text)

        fun now(): ZonedDateTime = ZonedDateTime.now(zoneId)
    }

    companion object {
        private val STOPWORDS = setOf(
            "the", "is", "are", "and", "or", "a", "an", "what", "when", "where", "who", "how",
            "me", "please", "show", "tell", "about",
            // 한국어 일반 불용어/질문형 표현
            "있어", "있니", "있나요", "있습니까", "해줘", "해줘요", "줘", "좀",
            // 시간 관련 표현 (추가로 별도 제거 로직도 적용됨)
            "이후", "이전", "오늘", "내일", "모레", "이번주", "다음주"
        )

        private val DATE_TIME_REGEXES = listOf(
            // 10월 / 17일 / 2024년 / 9시 / 30분
            Regex("\\b\\d{1,2}\\s*(월|일|시|분)\\b"),
            Regex("\\b\\d{4}\\s*년?\\b"),
            // 10/17, 10-17
            Regex("\\b\\d{1,2}[/-]\\d{1,2}\\b"),
        )

        private val DATE_TIME_WORDS = setOf(
            "이후", "이전", "오늘", "내일", "모레", "이번주", "다음주", "이번달", "다음달", "지난달",
            "월", "일", "시", "분"
        )

        private fun sanitizeDateTimeTokens(candidates: List<String>): List<String> {
            val result = candidates.filter { token ->
                if (token.isBlank()) return@filter false
                if (token.all { it.isDigit() }) return@filter false
                if (DATE_TIME_WORDS.any { token.contains(it) }) return@filter false
                if (DATE_TIME_REGEXES.any { it.containsMatchIn(token) }) return@filter false
                true
            }
            return result
        }
    }
}

