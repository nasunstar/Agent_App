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
        val (start, end) = resolution?.let { windowFor(it.timestampMillis) } ?: (null to null)
        val keywords = extractKeywords(trimmed)
        val source = detectSource(trimmed)
        return QueryFilters(
            startTimeMillis = start,
            endTimeMillis = end,
            source = source,
            keywords = keywords,
        )
    }

    private fun windowFor(center: Long): Pair<Long, Long> {
        val twelveHours = 12 * 60 * 60 * 1000L
        return (center - twelveHours) to (center + twelveHours)
    }

    private fun extractKeywords(text: String): List<String> {
        val lower = text.lowercase()
        val keywords = mutableListOf<String>()
        
        // 한국어 구문 패턴 매칭
        if (lower.contains("google") && (lower.contains("메일") || lower.contains("이메일"))) {
            keywords.add("gmail")
        }
        if (lower.contains("github")) {
            keywords.add("github")
        }
        if (lower.contains("steam")) {
            keywords.add("steam")
        }
        if (lower.contains("openai")) {
            keywords.add("openai")
        }
        if (lower.contains("이메일") || lower.contains("메일")) {
            keywords.add("email")
        }
        
        // 기존 토큰 기반 추출
        val tokens = lower
            .split(" ", "\n", "\t", ",", ".", "?", "!", ":", ";", "에서", "온", "이", "가", "을", "를", "에", "의", "뭐야", "뭔지")
            .mapNotNull { token ->
                val trimmed = token.trim()
                trimmed.takeIf { it.length >= 2 && it !in STOPWORDS }
            }
        
        keywords.addAll(tokens)
        return keywords.distinct().take(8)
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
            "me", "please", "show", "tell", "about", "최근", "문의", "알려줘", "줘"
        )
    }
}
