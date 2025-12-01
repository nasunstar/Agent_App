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
    private val embeddingStore: EmbeddingStore? = null,
    private val embeddingGenerator: EmbeddingGeneratorInterface = EmbeddingGenerator(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun upsert(item: IngestItem) = withContext(dispatcher) {
        dao.upsert(item)
        embeddingStore?.let { store ->
            val text = listOfNotNull(item.title, item.body).joinToString("\n").trim()
            if (text.isNotEmpty()) {
                val embedding = embeddingGenerator.generateEmbedding(text)
                store.saveEmbedding(item.id, embedding)
            }
        }
    }

    fun observeBySource(source: String): Flow<List<IngestItem>> = dao.observeBySource(source)

    suspend fun getById(id: String): IngestItem? = withContext(dispatcher) {
        dao.getById(id)
    }

    suspend fun getBySource(source: String): List<IngestItem> = withContext(dispatcher) {
        dao.getBySource(source)
    }

    suspend fun clearAll() = withContext(dispatcher) {
        dao.clearAll()
    }
}
