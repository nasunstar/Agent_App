package com.example.agent_app.data.repo

import com.example.agent_app.data.dao.IngestItemDao
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.data.search.EmbeddingGenerator
import com.example.agent_app.data.search.EmbeddingGeneratorInterface
import com.example.agent_app.data.search.EmbeddingStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class IngestRepository(
    private val dao: IngestItemDao,
    private val parser: IngestItemParser = IngestItemParser(),
    private val embeddingStore: EmbeddingStore? = null,
    private val embeddingGenerator: EmbeddingGeneratorInterface = EmbeddingGenerator(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun upsert(item: IngestItem) = withContext(dispatcher) {
        val enriched = parser.enrich(item)
        dao.upsert(enriched)
        embeddingStore?.let { store ->
            val text = listOfNotNull(enriched.title, enriched.body).joinToString("\n").trim()
            if (text.isNotEmpty()) {
                val embedding = embeddingGenerator.generateEmbedding(text)
                store.saveEmbedding(enriched.id, embedding)
            }
        }
    }

    fun observeBySource(source: String): Flow<List<IngestItem>> = dao.observeBySource(source)

    suspend fun clearAll() = withContext(dispatcher) {
        dao.clearAll()
    }
}
