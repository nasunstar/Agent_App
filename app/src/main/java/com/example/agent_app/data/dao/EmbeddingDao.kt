package com.example.agent_app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.agent_app.data.entity.IngestItemEmbedding

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: IngestItemEmbedding)

    @Query("SELECT * FROM ingest_item_embeddings WHERE item_id IN (:ids)")
    suspend fun getEmbeddings(ids: List<String>): List<IngestItemEmbedding>

    @Query("DELETE FROM ingest_item_embeddings WHERE item_id = :id")
    suspend fun deleteById(id: String)
}
