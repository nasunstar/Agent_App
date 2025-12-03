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
    
    @Query("SELECT * FROM events ORDER BY start_at ASC")
    suspend fun getAllEvents(): List<Event>
    
    @Query(
        "SELECT * FROM events WHERE " +
            "(:startTime IS NULL OR start_at IS NULL OR start_at >= :startTime) " +
            "AND (:endTime IS NULL OR start_at IS NULL OR start_at <= :endTime) " +
            "ORDER BY CASE WHEN start_at IS NULL THEN 1 ELSE 0 END, start_at ASC LIMIT :limit"
    )
    suspend fun searchByTimeRange(
        startTime: Long?,
        endTime: Long?,
        limit: Int = 50
    ): List<Event>
    
    @Query("SELECT * FROM events WHERE source_id = :sourceId")
    suspend fun getBySourceId(sourceId: String): List<Event>
    
    @Query("SELECT * FROM events WHERE source_type = :sourceType ORDER BY start_at ASC")
    suspend fun getBySourceType(sourceType: String): List<Event>
    
    // MOA-Needs-Review: 검토 필요한 일정 조회
    @Query("SELECT * FROM events WHERE status = :status ORDER BY start_at ASC")
    suspend fun getEventsByStatus(status: String): List<Event>
    
    @Query("SELECT * FROM events WHERE status = 'needs_review' ORDER BY start_at ASC")
    suspend fun getNeedsReviewEvents(): List<Event>
    
    @Query("DELETE FROM events")
    suspend fun clearAll()

    @Query("""
        SELECT * FROM events 
        WHERE title = :title 
          AND (start_at = :startAt OR (:startAt IS NULL AND start_at IS NULL))
          AND (location = :location OR (location IS NULL AND :location IS NULL))
        LIMIT 1
    """)
    suspend fun findDuplicateEvent(title: String?, startAt: Long?, location: String?): Event?
    
    /**
     * 다가오는 일정 조회 (지금부터 특정 시간 이내의 일정)
     */
    @Query(
        "SELECT * FROM events WHERE " +
            "start_at IS NOT NULL " +
            "AND start_at >= :startTime " +
            "AND start_at <= :endTime " +
            "ORDER BY start_at ASC LIMIT :limit"
    )
    suspend fun getUpcomingEvents(
        startTime: Long,
        endTime: Long,
        limit: Int = 50
    ): List<Event>
    
    /**
     * 알림 시간이 지금부터 특정 시간 이내인 일정 조회
     */
    @Query(
        "SELECT e.* FROM events e " +
            "INNER JOIN event_notifications en ON e.id = en.event_id " +
            "WHERE en.notify_at >= :startTime " +
            "AND en.notify_at <= :endTime " +
            "AND en.channel = :channel " +
            "AND en.sent_at IS NULL " +
            "ORDER BY en.notify_at ASC LIMIT :limit"
    )
    suspend fun getEventsWithNotificationsInRange(
        startTime: Long,
        endTime: Long,
        channel: String = "push",
        limit: Int = 50
    ): List<Event>

    @Query(
        """
        SELECT * FROM events 
        WHERE start_at BETWEEN :startTime AND :endTime
        ORDER BY start_at ASC
        """
    )
    suspend fun getEventsBetween(
        startTime: Long,
        endTime: Long
    ): List<Event>
}
