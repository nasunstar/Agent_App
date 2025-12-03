package com.example.agent_app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.agent_app.R
import com.example.agent_app.data.db.AppDatabase
import com.example.agent_app.data.dao.EventDao
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.entity.EventNotification
import com.example.agent_app.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.Calendar

/**
 * 일정 알림 서비스 - 하루 전, 당일, 2시간 전 알림 발송
 */
class EventNotificationService {
    
    companion object {
        private const val TAG = "EventNotificationService"
        private const val CHANNEL_ID = "event_notifications"
        private const val CHANNEL_NAME = "일정 알림"
        private const val NOTIFICATION_ID_PREFIX = 1000
        private val ONE_DAY_MS = TimeUnit.DAYS.toMillis(1)
        private val TWO_HOURS_MS = TimeUnit.HOURS.toMillis(2)
        
        // 알림 타입 상수
        private const val CHANNEL_1DAY_BEFORE = "push_1day_before"
        private const val CHANNEL_SAME_DAY = "push_same_day"
        private const val CHANNEL_2HOURS_BEFORE = "push_2hours_before"
        
        /**
         * 알림 채널 생성
         */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "일정 알림 (하루 전, 당일, 2시간 전)"
                    enableVibration(true)
                    setShowBadge(true)
                }
                
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }
        }
        
        /**
         * 일정에 대한 알림 스케줄링 (하루 전, 당일, 2시간 전)
         */
        suspend fun scheduleNotificationForEvent(
            event: Event,
            eventDao: EventDao
        ) = withContext(Dispatchers.IO) {
            if (event.startAt == null) {
                Log.d(TAG, "일정 시작 시간이 없어 알림 스케줄링 건너뛰기: ${event.title}")
                return@withContext
            }
            
            val now = System.currentTimeMillis()
            val startAt = event.startAt
            
            // 이미 지난 일정이면 스케줄링하지 않음
            if (startAt <= now) {
                Log.d(TAG, "이미 지난 일정이라 알림 스케줄링 건너뛰기: ${event.title}")
                return@withContext
            }
            
            // 알림 시간이 너무 먼 미래면 스케줄링하지 않음 (30일 이상)
            val thirtyDaysLater = now + TimeUnit.DAYS.toMillis(30)
            if (startAt > thirtyDaysLater) {
                Log.d(TAG, "너무 먼 미래 일정이라 알림 스케줄링 건너뛰기: ${event.title}")
                return@withContext
            }
            
            // 기존 알림이 있는지 확인
            val existingNotifications = try {
                eventDao.observeNotifications(event.id).first()
            } catch (e: Exception) {
                Log.e(TAG, "알림 조회 오류", e)
                emptyList()
            }
            
            // 1. 하루 전 알림 (일정 시작 시간 - 1일)
            val oneDayBefore = startAt - ONE_DAY_MS
            if (oneDayBefore > now && oneDayBefore <= thirtyDaysLater) {
                val hasNotification = existingNotifications.any { 
                    it.notifyAt == oneDayBefore && it.channel == CHANNEL_1DAY_BEFORE
                }
                if (!hasNotification) {
                    val notification = EventNotification(
                        eventId = event.id,
                        notifyAt = oneDayBefore,
                        channel = CHANNEL_1DAY_BEFORE
                    )
                    eventDao.upsertNotification(notification)
                    Log.d(TAG, "하루 전 알림 스케줄링: ${event.title}, 시간: ${java.util.Date(oneDayBefore)}")
                }
            }
            
            // 2. 당일 알림 (일정 시작일의 자정)
            val calendar = Calendar.getInstance().apply {
                timeInMillis = startAt
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val sameDayMidnight = calendar.timeInMillis
            
            // 당일 알림은 일정 시작일의 자정이어야 하고, 아직 지나지 않았어야 함
            if (sameDayMidnight > now && sameDayMidnight <= thirtyDaysLater && sameDayMidnight < startAt) {
                val hasNotification = existingNotifications.any { 
                    it.notifyAt == sameDayMidnight && it.channel == CHANNEL_SAME_DAY
                }
                if (!hasNotification) {
                    val notification = EventNotification(
                        eventId = event.id,
                        notifyAt = sameDayMidnight,
                        channel = CHANNEL_SAME_DAY
                    )
                    eventDao.upsertNotification(notification)
                    Log.d(TAG, "당일 알림 스케줄링: ${event.title}, 시간: ${java.util.Date(sameDayMidnight)}")
                }
            }
            
            // 3. 2시간 전 알림 (일정 시작 시간 - 2시간)
            val twoHoursBefore = startAt - TWO_HOURS_MS
            if (twoHoursBefore > now && twoHoursBefore <= thirtyDaysLater) {
                val hasNotification = existingNotifications.any { 
                    it.notifyAt == twoHoursBefore && it.channel == CHANNEL_2HOURS_BEFORE
                }
                if (!hasNotification) {
                    val notification = EventNotification(
                        eventId = event.id,
                        notifyAt = twoHoursBefore,
                        channel = CHANNEL_2HOURS_BEFORE
                    )
                    eventDao.upsertNotification(notification)
                    Log.d(TAG, "2시간 전 알림 스케줄링: ${event.title}, 시간: ${java.util.Date(twoHoursBefore)}")
                }
            }
        }
        
        /**
         * 모든 알림 대상을 확인하고 알림 발송
         */
        suspend fun checkAndSendNotifications(context: Context) = withContext(Dispatchers.IO) {
            val database = AppDatabase.build(context)
            val eventDao = database.eventDao()
            
            val now = System.currentTimeMillis()
            val oneHourLater = now + TimeUnit.HOURS.toMillis(1)
            
            var sentCount = 0
            
            // 모든 알림 타입에 대해 처리
            val channels = listOf(CHANNEL_1DAY_BEFORE, CHANNEL_SAME_DAY, CHANNEL_2HOURS_BEFORE)
            
            for (channel in channels) {
                // 알림 시간이 지금부터 1시간 이내인 일정들 조회
                val events = try {
                    eventDao.getEventsWithNotificationsInRange(
                        startTime = now,
                        endTime = oneHourLater,
                        channel = channel,
                        limit = 100
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "일정 조회 오류 (channel: $channel)", e)
                    continue
                }
                
                for (event in events) {
                    if (event.startAt == null) continue
                    
                    // 알림이 이미 발송되었는지 확인 (중복 방지)
                    val notifications = try {
                        eventDao.observeNotifications(event.id).first()
                    } catch (e: Exception) {
                        Log.e(TAG, "알림 조회 오류", e)
                        continue
                    }
                    
                    // 현재 시간부터 1시간 이내인 미발송 알림 찾기
                    val scheduledNotification = notifications.firstOrNull { notification ->
                        notification.notifyAt >= now &&
                        notification.notifyAt <= oneHourLater &&
                        notification.channel == channel &&
                        notification.sentAt == null
                    }
                    
                    if (scheduledNotification != null) {
                        // 알림 발송
                        sendNotification(context, event, channel)
                        
                        // 발송 시간 기록
                        try {
                            val updatedNotification = scheduledNotification.copy(sentAt = now)
                            eventDao.upsertNotification(updatedNotification)
                        } catch (e: Exception) {
                            Log.e(TAG, "알림 발송 시간 기록 오류", e)
                        }
                        
                        sentCount++
                    }
                }
            }
            
            Log.d(TAG, "알림 확인 완료: ${sentCount}개 알림 발송")
        }
        
        /**
         * 실제 알림 발송
         */
        private fun sendNotification(context: Context, event: Event, channel: String) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("event_id", event.id)
            }
            
            // 각 알림 타입별로 다른 notificationId를 사용하여 중복 방지
            val notificationIdOffset = when (channel) {
                CHANNEL_1DAY_BEFORE -> 0
                CHANNEL_SAME_DAY -> 10000
                CHANNEL_2HOURS_BEFORE -> 20000
                else -> 0
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                event.id.toInt() + notificationIdOffset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // 알림 타입에 따른 메시지 선택
            val contentText = when (channel) {
                CHANNEL_1DAY_BEFORE -> context.getString(
                    R.string.event_notification_1day_before,
                    event.title
                )
                CHANNEL_SAME_DAY -> context.getString(
                    R.string.event_notification_same_day,
                    event.title
                )
                CHANNEL_2HOURS_BEFORE -> context.getString(
                    R.string.event_notification_2hours_before,
                    event.title
                )
                else -> context.getString(
                    R.string.event_notification_1day_before,
                    event.title
                )
            }
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("일정 알림")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            
            val notificationId = NOTIFICATION_ID_PREFIX + event.id.toInt() + notificationIdOffset
            notificationManager.notify(notificationId, notification)
            
            Log.d(TAG, "알림 발송 완료: ${event.title} (타입: $channel)")
        }
    }
}

