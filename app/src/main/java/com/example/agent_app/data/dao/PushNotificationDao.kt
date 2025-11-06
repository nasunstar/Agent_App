package com.example.agent_app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.agent_app.data.entity.PushNotification
import kotlinx.coroutines.flow.Flow

@Dao
interface PushNotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: PushNotification)

    @Delete
    suspend fun delete(notification: PushNotification)

    @Query("SELECT * FROM push_notifications WHERE id = :id")
    suspend fun getById(id: Long): PushNotification?

    @Query("SELECT * FROM push_notifications ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<PushNotification>

    @Query("SELECT * FROM push_notifications ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PushNotification>>

    @Query("SELECT * FROM push_notifications WHERE package_name = :packageName ORDER BY timestamp DESC")
    fun observeByPackage(packageName: String): Flow<List<PushNotification>>

    @Query("SELECT * FROM push_notifications WHERE package_name = :packageName ORDER BY timestamp DESC")
    suspend fun getByPackage(packageName: String): List<PushNotification>

    @Query(
        "SELECT * FROM push_notifications WHERE " +
            "(:start IS NULL OR timestamp >= :start) " +
            "AND (:end IS NULL OR timestamp <= :end) " +
            "AND (:packageName IS NULL OR package_name = :packageName) " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun filterForSearch(
        start: Long?,
        end: Long?,
        packageName: String?,
        limit: Int,
    ): List<PushNotification>

    @Query(
        "SELECT * FROM push_notifications WHERE " +
            "(:start IS NULL OR timestamp >= :start) " +
            "AND (:end IS NULL OR timestamp <= :end) " +
            "ORDER BY timestamp DESC"
    )
    suspend fun getByTimestampRange(start: Long?, end: Long?): List<PushNotification>

    // 앱별 통계
    @Query(
        "SELECT package_name, app_name, COUNT(*) as count " +
            "FROM push_notifications " +
            "WHERE (:start IS NULL OR timestamp >= :start) " +
            "AND (:end IS NULL OR timestamp <= :end) " +
            "GROUP BY package_name, app_name " +
            "ORDER BY count DESC"
    )
    suspend fun getAppStatistics(start: Long?, end: Long?): List<AppNotificationStats>

    // 시간대별 통계
    @Query(
        "SELECT strftime('%H', datetime(timestamp/1000, 'unixepoch')) as hour, COUNT(*) as count " +
            "FROM push_notifications " +
            "WHERE (:start IS NULL OR timestamp >= :start) " +
            "AND (:end IS NULL OR timestamp <= :end) " +
            "GROUP BY hour " +
            "ORDER BY hour"
    )
    suspend fun getHourlyStatistics(start: Long?, end: Long?): List<HourlyNotificationStats>

    @Query("DELETE FROM push_notifications")
    suspend fun clearAll()
}

data class AppNotificationStats(
    val package_name: String,
    val app_name: String?,
    val count: Int
)

data class HourlyNotificationStats(
    val hour: String,
    val count: Int
)

