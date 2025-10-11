package com.example.agent_app.data.search

import android.database.sqlite.SQLiteException
import com.example.agent_app.data.dao.IngestItemDao
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.domain.chat.model.ChatContextItem
import com.example.agent_app.domain.chat.model.QueryFilters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class HybridSearchEngine(
    private val ingestItemDao: IngestItemDao,
    private val embeddingStore: EmbeddingStore,
    private val embeddingGenerator: EmbeddingGenerator,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun search(
        question: String,
        filters: QueryFilters,
        limit: Int = DEFAULT_LIMIT,
    ): List<ChatContextItem> = withContext(dispatcher) {
        val sanitizedQuestion = question.trim()
        val keywordCandidates = runCatching {
            if (sanitizedQuestion.isBlank()) emptyList() else {
                val query = buildMatchQuery(filters.keywords.ifEmpty { extractKeywords(sanitizedQuestion) })
                if (query.isBlank()) emptyList() else ingestItemDao.searchByFullText(query, limit * 3)
            }
        }.recoverCatching { throwable ->
            if (throwable is SQLiteException) emptyList() else throw throwable
        }.getOrDefault(emptyList())

        val structuredCandidates = ingestItemDao.filterForSearch(
            start = filters.startTimeMillis,
            end = filters.endTimeMillis,
            source = filters.source,
            limit = limit * 4,
        )

        val combined = (keywordCandidates + structuredCandidates)
            .associateBy { it.id }
            .values
            .toList()
        if (combined.isEmpty()) {
            return@withContext emptyList()
        }

        val queryEmbedding = embeddingGenerator.generateEmbedding(sanitizedQuestion)
        val cachedEmbeddings = embeddingStore.getEmbeddings(combined.map(IngestItem::id))

        val scored = combined.mapIndexed { index, item ->
            val keywords = filters.keywords.ifEmpty { extractKeywords(sanitizedQuestion) }
            val keywordScore = keywordRelevance(item, keywords, index)
            val itemEmbedding = cachedEmbeddings[item.id] ?: buildEmbeddingFor(item).also {
                embeddingStore.saveEmbedding(item.id, it)
            }
            val vectorScore = cosineSimilarity(queryEmbedding, itemEmbedding)
            val recency = recencyBoost(item, filters)
            val finalScore = keywordScore * 0.5 + vectorScore * 0.4 + recency * 0.1
            Pair(item, finalScore)
        }

        scored
            .sortedByDescending { it.second }
            .take(limit)
            .mapIndexed { position, (item, score) ->
                ChatContextItem(
                    itemId = item.id,
                    title = item.title.orEmpty(),
                    body = item.body.orEmpty(),
                    source = item.source,
                    timestamp = item.timestamp,
                    relevance = score,
                    position = position + 1,
                )
            }
    }

    private suspend fun buildEmbeddingFor(item: IngestItem): FloatArray = withContext(dispatcher) {
        val text = listOfNotNull(item.title, item.body).joinToString("\n").trim()
        embeddingGenerator.generateEmbedding(text)
    }

    private fun keywordRelevance(item: IngestItem, keywords: List<String>, index: Int): Double {
        if (keywords.isEmpty()) return 0.2
        val haystack = listOfNotNull(item.title, item.body)
            .joinToString(" ")
            .lowercase()
        var matches = 0
        keywords.forEach { keyword ->
            if (haystack.contains(keyword.lowercase())) {
                matches += 1
            }
        }
        if (matches == 0) return max(0.1, 0.5 - index * 0.05)
        val ratio = matches.toDouble() / keywords.size
        return min(1.0, 0.5 + ratio * 0.5)
    }

    private fun recencyBoost(item: IngestItem, filters: QueryFilters): Double {
        val targetStart = filters.startTimeMillis ?: return 0.2
        val targetEnd = filters.endTimeMillis ?: return 0.2
        if (item.timestamp in targetStart..targetEnd) {
            return 0.6
        }
        val distance = min(
            kotlin.math.abs(item.timestamp - targetStart),
            kotlin.math.abs(item.timestamp - targetEnd)
        )
        return when {
            distance < DAY_MILLIS -> 0.45
            distance < 3 * DAY_MILLIS -> 0.35
            distance < 7 * DAY_MILLIS -> 0.2
            else -> 0.05
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0.0
        var dot = 0.0
        var magA = 0.0
        var magB = 0.0
        for (index in a.indices) {
            val va = a[index].toDouble()
            val vb = b[index].toDouble()
            dot += va * vb
            magA += va * va
            magB += vb * vb
        }
        if (magA == 0.0 || magB == 0.0) return 0.0
        return dot / (kotlin.math.sqrt(magA) * kotlin.math.sqrt(magB))
    }

    private fun buildMatchQuery(keywords: List<String>): String {
        if (keywords.isEmpty()) return ""
        return keywords.joinToString(" ") { keyword ->
            val sanitized = keyword
                .replace('"', ' ')
                .replace("'", " ")
                .trim()
            if (sanitized.isBlank()) "" else "\"$sanitized*\""
        }.trim()
    }

    private fun extractKeywords(text: String): List<String> = text
        .lowercase()
        .split(" ", "\n", "\t", ",", ".", "?", "!", ":", ";")
        .mapNotNull { token ->
            val trimmed = token.trim()
            trimmed.takeIf { it.length >= 2 && it !in STOPWORDS }
        }
        .take(6)

    companion object {
        private const val DEFAULT_LIMIT = 5
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        private val STOPWORDS = setOf(
            "the", "is", "are", "and", "or", "a", "an", "what", "when", "where", "who", "how",
            "please", "tell", "show", "about", "me", "of", "to", "for", "in"
        )
    }
}
