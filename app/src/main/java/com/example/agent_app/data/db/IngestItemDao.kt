package com.example.agent_app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface IngestItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: IngestItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<IngestItemEntity>): List<Long>

    @Update
    suspend fun update(item: IngestItemEntity)

    @Delete
    suspend fun delete(item: IngestItemEntity)

    @Query("DELETE FROM ingest_items WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM ingest_items WHERE id = :id")
    suspend fun getById(id: Long): IngestItemEntity?

    @Query(
        "SELECT * FROM ingest_items WHERE source = :source AND external_id = :externalId LIMIT 1"
    )
    suspend fun findBySourceAndExternalId(source: String, externalId: String): IngestItemEntity?

    @Query(
        """
        SELECT ingest_items.* FROM ingest_items
        JOIN ingest_items_fts ON ingest_items.rowid = ingest_items_fts.rowid
        WHERE ingest_items_fts MATCH :query
        ORDER BY bm25(ingest_items_fts)
        """
    )
    suspend fun search(query: String): List<IngestItemEntity>

    @Transaction
    @Query("SELECT * FROM ingest_items ORDER BY (received_at IS NULL), received_at DESC, id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<IngestItemEntity>
}
