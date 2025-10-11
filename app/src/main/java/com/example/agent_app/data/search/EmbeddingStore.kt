package com.example.agent_app.data.search

import com.example.agent_app.data.dao.EmbeddingDao
import com.example.agent_app.data.entity.IngestItemEmbedding
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EmbeddingStore(
    private val embeddingDao: EmbeddingDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getEmbeddings(ids: List<String>): Map<String, FloatArray> = withContext(dispatcher) {
        if (ids.isEmpty()) return@withContext emptyMap()
        embeddingDao.getEmbeddings(ids).associate { embedding ->
            embedding.itemId to embedding.vector.toFloatArray(embedding.dimension)
        }
    }

    suspend fun saveEmbedding(itemId: String, vector: FloatArray) = withContext(dispatcher) {
        val bytes = vector.toByteArray()
        embeddingDao.upsert(
            IngestItemEmbedding(
                itemId = itemId,
                vector = bytes,
                dimension = vector.size,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun ByteArray.toFloatArray(expectedDimension: Int): FloatArray {
        if (isEmpty()) return FloatArray(expectedDimension)
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(expectedDimension)
        for (index in 0 until expectedDimension) {
            floats[index] = if (buffer.remaining() >= Float.SIZE_BYTES) buffer.float else 0f
        }
        return floats
    }

    private fun FloatArray.toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        forEach { value -> buffer.putFloat(value) }
        return buffer.array()
    }
}
