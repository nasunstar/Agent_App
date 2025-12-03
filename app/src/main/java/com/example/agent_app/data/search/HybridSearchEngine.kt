package com.example.agent_app.data.search

import android.database.sqlite.SQLiteException
import com.example.agent_app.data.dao.IngestItemDao
import com.example.agent_app.data.dao.EventDao
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.data.entity.Event
import com.example.agent_app.domain.chat.model.ChatContextItem
import com.example.agent_app.domain.chat.model.QueryFilters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class HybridSearchEngine(
    private val ingestItemDao: IngestItemDao,
    private val eventDao: EventDao,
    private val embeddingStore: EmbeddingStore,
    private val embeddingGenerator: EmbeddingGeneratorInterface,
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

        // Event 테이블에서 시간 범위 검색
        val eventCandidates = runCatching {
            val events = eventDao.searchByTimeRange(
                startTime = filters.startTimeMillis,
                endTime = filters.endTimeMillis,
                limit = limit * 2
            )
            filters.startTimeMillis?.let { start ->
                val startDate = java.time.Instant.ofEpochMilli(start)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                android.util.Log.d("HybridSearchEngine", "Event 검색 - 시작: $startDate ($start)")
            } ?: android.util.Log.d("HybridSearchEngine", "Event 검색 - 시작: null")
            
            filters.endTimeMillis?.let { end ->
                val endDate = java.time.Instant.ofEpochMilli(end)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                android.util.Log.d("HybridSearchEngine", "Event 검색 - 끝: $endDate ($end)")
            } ?: android.util.Log.d("HybridSearchEngine", "Event 검색 - 끝: null")
            
            android.util.Log.d("HybridSearchEngine", "Event 검색 결과: ${events.size}개")
            events.forEachIndexed { index, event ->
                event.startAt?.let { startAt ->
                    val eventDate = java.time.Instant.ofEpochMilli(startAt)
                        .atZone(java.time.ZoneId.of("Asia/Seoul"))
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    android.util.Log.d("HybridSearchEngine", "  일정 ${index + 1}: ${event.title} - $eventDate")
                } ?: android.util.Log.d("HybridSearchEngine", "  일정 ${index + 1}: ${event.title} - 시간 없음")
            }
            events
        }.getOrDefault(emptyList())

        val combined = (keywordCandidates + structuredCandidates)
            .associateBy { it.id }
            .values
            .toList()
            
        if (combined.isEmpty() && eventCandidates.isEmpty()) {
            return@withContext emptyList()
        }

        val queryEmbedding = embeddingGenerator.generateEmbedding(sanitizedQuestion)
        val cachedEmbeddings = embeddingStore.getEmbeddings(combined.map(IngestItem::id))
        val cachedEventEmbeddings = embeddingStore.getEmbeddings(eventCandidates.map { "event_${it.id}" })

        // IngestItem 결과 점수 계산 (개선된 하이브리드 검색)
        val scoredIngestItems = combined.mapIndexed { index, item ->
            val keywords = filters.keywords.ifEmpty { extractKeywords(sanitizedQuestion) }
            val keywordScore = keywordRelevance(item, keywords, index)
            val itemEmbedding = cachedEmbeddings[item.id] ?: buildEmbeddingFor(item).also {
                embeddingStore.saveEmbedding(item.id, it)
            }
            val vectorScore = cosineSimilarity(queryEmbedding, itemEmbedding)
            val recency = recencyBoost(item, filters)
            
            // 개선된 가중치: 키워드 매칭 강화 (정확한 매칭 중요), 벡터 유사도 유지, 최신성 조정
            // 키워드가 명확히 매칭되면 높은 점수, 벡터는 의미 유사도, 최신성은 보조
            val finalScore = when {
                keywordScore > 0.7 -> keywordScore * 0.5 + vectorScore * 0.3 + recency * 0.2  // 키워드 강한 매칭
                vectorScore > 0.7 -> keywordScore * 0.2 + vectorScore * 0.5 + recency * 0.3  // 벡터 강한 유사도
                else -> keywordScore * 0.35 + vectorScore * 0.35 + recency * 0.3  // 균형
            }
            ChatContextItem(
                itemId = item.id,
                title = item.title.orEmpty(),
                body = item.body.orEmpty(),
                source = item.source,
                timestamp = item.timestamp,
                relevance = finalScore,
                position = 0
            )
        }

        // Event 결과 점수 계산 (Contextual Retrieval + Hybrid Search 적용, 개선된 가중치)
        val scoredEventItems = eventCandidates.mapIndexed { index, event ->
            val keywords = filters.keywords.ifEmpty { extractKeywords(sanitizedQuestion) }
            val keywordScore = keywordRelevanceForEvent(event, keywords, index)
            val eventEmbedding = cachedEventEmbeddings["event_${event.id}"] ?: buildEmbeddingForEvent(event).also {
                embeddingStore.saveEmbedding("event_${event.id}", it)
            }
            val vectorScore = cosineSimilarity(queryEmbedding, eventEmbedding)
            val recency = recencyBoostForEvent(event, filters)
            
            // 일정 검색은 시간 관련성이 더 중요하므로 recency 가중치 증가
            val finalScore = when {
                keywordScore > 0.7 -> keywordScore * 0.5 + vectorScore * 0.25 + recency * 0.25  // 키워드 강한 매칭
                recency > 0.6 -> keywordScore * 0.25 + vectorScore * 0.25 + recency * 0.5  // 시간 범위 내
                vectorScore > 0.7 -> keywordScore * 0.2 + vectorScore * 0.5 + recency * 0.3  // 벡터 강한 유사도
                else -> keywordScore * 0.3 + vectorScore * 0.3 + recency * 0.4  // 기본 균형
            }
            
            ChatContextItem(
                itemId = "event_${event.id}",
                title = event.title,
                body = buildString {
                    event.body?.let { appendLine(it) }
                    if (event.location != null) append("장소: ${event.location}\n")
                    if (event.startAt != null) {
                        val startTime = java.time.Instant.ofEpochMilli(event.startAt)
                            .atZone(java.time.ZoneId.of("Asia/Seoul"))
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        append("시간: $startTime")
                    }
                },
                source = "event",
                timestamp = event.startAt ?: 0L,
                relevance = finalScore,
                position = 0
            )
        }

        // Event 결과와 IngestItem 결과 통합 및 정렬
        val allScoredResults = scoredIngestItems + scoredEventItems
        
        android.util.Log.d("HybridSearchEngine", "IngestItem 결과: ${scoredIngestItems.size}개")
        android.util.Log.d("HybridSearchEngine", "Event 결과: ${scoredEventItems.size}개")
        android.util.Log.d("HybridSearchEngine", "전체 결과: ${allScoredResults.size}개")
        allScoredResults.forEach { item ->
            android.util.Log.d("HybridSearchEngine", "결과: ${item.title}, relevance: ${item.relevance}, source: ${item.source}")
        }
        
        allScoredResults
            .sortedByDescending { it.relevance }
            .take(limit)
            .mapIndexed { position, item ->
                item.copy(position = position + 1)
            }
    }

    private suspend fun buildEmbeddingFor(item: IngestItem): FloatArray = withContext(dispatcher) {
        // Contextual Retrieval: 메타데이터를 포함한 맥락 정보 추가
        val contextualText = buildString {
            // 메타데이터 컨텍스트
            append("[출처: ${item.source}")
            item.timestamp.let { ts ->
                val date = java.time.Instant.ofEpochMilli(ts)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                append(", 시간: $date")
            }
            appendLine("]")
            // 실제 콘텐츠
            item.title?.let { appendLine(it) }
            item.body?.let { appendLine(it) }
        }.trim()
        embeddingGenerator.generateEmbedding(contextualText)
    }
    
    private suspend fun buildEmbeddingForEvent(event: Event): FloatArray = withContext(dispatcher) {
        // Event를 위한 Contextual Retrieval
        val contextualText = buildString {
            // 메타데이터 컨텍스트
            append("[이벤트")
            event.startAt?.let { ts ->
                val date = java.time.Instant.ofEpochMilli(ts)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                append(", 시간: $date")
            }
            event.location?.let { append(", 장소: $it") }
            appendLine("]")
            // 실제 콘텐츠
            appendLine(event.title)
            event.body?.let { appendLine(it) }
        }.trim()
        embeddingGenerator.generateEmbedding(contextualText)
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
    
    private fun keywordRelevanceForEvent(event: Event, keywords: List<String>, index: Int): Double {
        if (keywords.isEmpty()) return 0.2
        val haystack = listOfNotNull(event.title, event.body, event.location)
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
        
        // 시간 범위 내에 있는 경우 높은 점수
        if (item.timestamp in targetStart..targetEnd) {
            return 0.8 // 더 높은 점수로 시간 필터링 강화
        }
        
        // 시간 범위 밖의 경우 거리에 따른 점수
        val distance = min(
            kotlin.math.abs(item.timestamp - targetStart),
            kotlin.math.abs(item.timestamp - targetEnd)
        )
        return when {
            distance < DAY_MILLIS -> 0.6
            distance < 3 * DAY_MILLIS -> 0.4
            distance < 7 * DAY_MILLIS -> 0.3
            distance < 30 * DAY_MILLIS -> 0.1 // 30일 이내는 낮은 점수
            else -> 0.0 // 30일 이후는 거의 0점
        }
    }
    
    private fun recencyBoostForEvent(event: Event, filters: QueryFilters): Double {
        val timestamp = event.startAt ?: return 0.2
        val targetStart = filters.startTimeMillis ?: return 0.2
        val targetEnd = filters.endTimeMillis ?: return 0.2
        
        // 시간 범위 내에 있는 경우 높은 점수
        if (timestamp in targetStart..targetEnd) {
            return 0.8
        }
        
        // 시간 범위 밖의 경우 거리에 따른 점수
        val distance = min(
            kotlin.math.abs(timestamp - targetStart),
            kotlin.math.abs(timestamp - targetEnd)
        )
        return when {
            distance < DAY_MILLIS -> 0.6
            distance < 3 * DAY_MILLIS -> 0.4
            distance < 7 * DAY_MILLIS -> 0.3
            distance < 30 * DAY_MILLIS -> 0.1
            else -> 0.0
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
        val cleaned = sanitizeKeywords(keywords)
        if (cleaned.isEmpty()) return ""
        // OR로 완화하여 과도한 AND 제약을 피한다
        return cleaned.joinToString(" OR ") { keyword ->
            val sanitized = keyword
                .replace('"', ' ')
                .replace("'", " ")
                .trim()
            if (sanitized.isBlank()) "" else "\"$sanitized*\""
        }.trim()
    }

    private fun extractKeywords(text: String): List<String> =
        sanitizeKeywords(
            text.lowercase()
                .split(" ", "\n", "\t", ",", ".", "?", "!", ":", ";")
                .mapNotNull { token ->
                    val trimmed = token.trim()
                    trimmed.takeIf { it.length >= 2 && it !in STOPWORDS }
                }
                .take(10)
        )

    private fun sanitizeKeywords(candidates: List<String>): List<String> {
        return candidates.filter { token ->
            if (token.isBlank()) return@filter false
            if (token.all { it.isDigit() }) return@filter false
            if (DATE_TIME_WORDS.any { token.contains(it) }) return@filter false
            if (DATE_TIME_REGEXES.any { it.containsMatchIn(token) }) return@filter false
            true
        }
    }

    companion object {
        private const val DEFAULT_LIMIT = 5
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        private val STOPWORDS = setOf(
            "the", "is", "are", "and", "or", "a", "an", "what", "when", "where", "who", "how",
            "please", "tell", "show", "about", "me", "of", "to", "for", "in",
            // 한국어 흔한 불용어/질문형 표현
            "있어", "있니", "있나요", "있습니까", "좀", "해줘", "해줘요",
            // 시간 관련 표현 (추가로 별도 제거 로직도 적용됨)
            "이후", "이전", "오늘", "내일", "모레", "이번주", "다음주"
        )

        private val DATE_TIME_REGEXES = listOf(
            Regex("\\b\\d{1,2}\\s*(월|일|시|분)\\b"),
            Regex("\\b\\d{4}\\s*년?\\b"),
            Regex("\\b\\d{1,2}[/-]\\d{1,2}\\b"),
        )

        private val DATE_TIME_WORDS = setOf(
            "이후", "이전", "오늘", "내일", "모레", "이번주", "다음주", "이번달", "다음달", "지난달",
            "월", "일", "시", "분"
        )
    }
}
