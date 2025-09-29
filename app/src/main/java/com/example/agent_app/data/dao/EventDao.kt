package com.example.agent_app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.entity.EventDetail
import com.example.agent_app.data.entity.EventNotification
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: Event): Long

    @Insert
    suspend fun insert(event: Event): Long

    @Update
    suspend fun update(event: Event)

    @Delete
    suspend fun delete(event: Event)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): Event?

    @Query(
        "SELECT * FROM events WHERE user_id = :userId " +
            "ORDER BY CASE WHEN start_at IS NULL THEN 1 ELSE 0 END, start_at ASC"
    )
    fun observeByUser(userId: Long): Flow<List<Event>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDetail(detail: EventDetail): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotification(notification: EventNotification): Long

    @Query("SELECT * FROM event_details WHERE event_id = :eventId")
    suspend fun getDetail(eventId: Long): EventDetail?

    @Query("SELECT * FROM event_notifications WHERE event_id = :eventId ORDER BY notify_at ASC")
    fun observeNotifications(eventId: Long): Flow<List<EventNotification>>

    @Transaction
    suspend fun replaceNotifications(eventId: Long, notifications: List<EventNotification>) {
        deleteNotifications(eventId)
        notifications.forEach { upsertNotification(it) }
    }

    @Query("DELETE FROM event_notifications WHERE event_id = :eventId")
    suspend fun deleteNotifications(eventId: Long)
    
    @Query("SELECT * FROM events ORDER BY start_at ASC")
    suspend fun getAll(): List<Event>
}
