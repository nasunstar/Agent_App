package com.example.agent_app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.agent_app.data.entity.EventType
import kotlinx.coroutines.flow.Flow

@Dao
interface EventTypeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(eventType: EventType): Long

    @Insert
    suspend fun insert(eventType: EventType): Long

    @Update
    suspend fun update(eventType: EventType)

    @Query("SELECT * FROM event_types WHERE id = :id")
    suspend fun getById(id: Long): EventType?

    @Query("SELECT * FROM event_types WHERE type_name = :typeName LIMIT 1")
    suspend fun getByName(typeName: String): EventType?

    @Query("SELECT * FROM event_types ORDER BY type_name ASC")
    fun observeAll(): Flow<List<EventType>>
}
