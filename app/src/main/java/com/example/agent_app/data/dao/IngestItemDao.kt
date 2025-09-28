package com.example.agent_app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.agent_app.data.entity.IngestItem
import kotlinx.coroutines.flow.Flow

@Dao
interface IngestItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: IngestItem)

    @Update
    suspend fun update(item: IngestItem)

    @Delete
    suspend fun delete(item: IngestItem)

    @Query("SELECT * FROM ingest_items WHERE id = :id")
    suspend fun getById(id: String): IngestItem?

    @Query("SELECT * FROM ingest_items ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<IngestItem>

    @Query("SELECT * FROM ingest_items WHERE source = :source ORDER BY timestamp DESC")
    fun observeBySource(source: String): Flow<List<IngestItem>>

    @Query(
        "SELECT * FROM ingest_items WHERE (:start IS NULL OR timestamp >= :start) " +
            "AND (:end IS NULL OR timestamp <= :end) ORDER BY timestamp DESC"
    )
    suspend fun getByTimestampRange(start: Long?, end: Long?): List<IngestItem>

    @Query(
        "SELECT * FROM ingest_items WHERE due_date IS NOT NULL " +
            "AND (:after IS NULL OR due_date >= :after) " +
            "AND (:before IS NULL OR due_date <= :before) ORDER BY due_date ASC"
    )
    fun observeDueBetween(after: Long?, before: Long?): Flow<List<IngestItem>>
}
