package com.example.agent_app.data.repo

import com.example.agent_app.data.dao.IngestItemDao
import com.example.agent_app.data.entity.IngestItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class IngestRepository(
    private val dao: IngestItemDao,
    private val parser: IngestItemParser = IngestItemParser(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun upsert(item: IngestItem) = withContext(dispatcher) {
        val enriched = parser.enrich(item)
        dao.upsert(enriched)
    }

    fun observeBySource(source: String): Flow<List<IngestItem>> = dao.observeBySource(source)
}
