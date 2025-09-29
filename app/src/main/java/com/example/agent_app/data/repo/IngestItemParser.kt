package com.example.agent_app.data.repo

import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.util.TimeResolver
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class IngestItemParser(
    private val timeResolver: TimeResolver = TimeResolver,
) {
    fun enrich(item: IngestItem): IngestItem {
        val combinedText = listOfNotNull(item.title, item.body)
            .joinToString(" ")
            .trim()
        if (combinedText.isEmpty()) {
            return item
        }
        val resolution = timeResolver.resolve(combinedText)
        val keywordScore = detectKeywordConfidence(combinedText)

        val dueDate = resolution?.timestampMillis ?: item.dueDate
        val confidence = when {
            resolution != null && keywordScore > 0.0 ->
                min(0.95, resolution.confidence + keywordScore)
            resolution != null -> resolution.confidence
            keywordScore > 0.0 -> max(0.35, keywordScore + 0.25)
            else -> item.confidence
        }

        if (dueDate == item.dueDate && confidence == item.confidence) {
            return item
        }
        return item.copy(
            dueDate = dueDate,
            confidence = confidence,
        )
    }

    private fun detectKeywordConfidence(text: String): Double {
        if (text.isBlank()) return 0.0
        val normalized = text.lowercase(Locale.getDefault())
        var score = 0.0
        KEYWORDS.forEach { (keyword, weight) ->
            if (normalized.contains(keyword) || text.contains(keyword)) {
                score = max(score, weight)
            }
        }
        return score
    }

    companion object {
        private val KEYWORDS: Map<String, Double> = mapOf(
            "회의" to 0.35,
            "미팅" to 0.35,
            "약속" to 0.25,
            "마감" to 0.4,
            "제출" to 0.25,
            "deadline" to 0.45,
            "due" to 0.25,
            "reminder" to 0.2,
            "call" to 0.2,
            "meeting" to 0.3,
        )
    }
}
