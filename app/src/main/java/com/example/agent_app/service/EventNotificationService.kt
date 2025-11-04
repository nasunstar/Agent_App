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

/**
 * 일정 알림 서비스 - 하루 전 알림 발송
 */
class EventNotificationService {
    
    companion object {
        private const val TAG = "EventNotificationService"
        private const val CHANNEL_ID = "event_notifications"
        private const val CHANNEL_NAME = "일정 알림"
        private const val NOTIFICATION_ID_PREFIX = 1000
        private val ONE_DAY_MS = TimeUnit.DAYS.toMillis(1)
        
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
                    description = "일정 하루 전 알림"
                    enableVibration(true)
                    setShowBadge(true)
                }
                
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }
        }
        
        /**
         * 일정에 대한 알림 스케줄링
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
            val oneDayBefore = event.startAt - ONE_DAY_MS
            
            // 이미 지난 일정이면 스케줄링하지 않음
            if (oneDayBefore <= now) {
                Log.d(TAG, "이미 지난 일정이거나 1일 전이 지나서 알림 스케줄링 건너뛰기: ${event.title}")
                return@withContext
            }
            
            // 알림 시간이 너무 먼 미래면 스케줄링하지 않음 (30일 이상)
            val thirtyDaysLater = now + TimeUnit.DAYS.toMillis(30)
            if (oneDayBefore > thirtyDaysLater) {
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
            
            // 이미 동일한 시간의 알림이 있으면 건너뛰기
            val hasExistingNotification = existingNotifications.any { 
                it.notifyAt == oneDayBefore && it.channel == "push"
            }
            
            if (!hasExistingNotification) {
                val notification = EventNotification(
                    eventId = event.id,
                    notifyAt = oneDayBefore,
                    channel = "push"
                )
                eventDao.upsertNotification(notification)
                Log.d(TAG, "알림 스케줄링 완료: ${event.title}, 알림 시간: ${java.util.Date(oneDayBefore)}")
            } else {
                Log.d(TAG, "이미 알림이 스케줄링되어 있음: ${event.title}")
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
            
            // 알림 시간이 지금부터 1시간 이내인 일정들 조회
            val events = try {
                eventDao.getEventsWithNotificationsInRange(
                    startTime = now,
                    endTime = oneHourLater,
                    channel = "push",
                    limit = 100
                )
            } catch (e: Exception) {
                Log.e(TAG, "일정 조회 오류", e)
                emptyList()
            }
            
            var sentCount = 0
            for (event in events) {
                if (event.startAt == null) continue
                
                val oneDayBefore = event.startAt - ONE_DAY_MS
                
                // 알림 시간이 지금부터 1시간 이내인지 확인
                if (oneDayBefore >= now && oneDayBefore <= oneHourLater) {
                    // 알림이 이미 발송되었는지 확인 (중복 방지)
                    val notifications = try {
                        eventDao.observeNotifications(event.id).first()
                    } catch (e: Exception) {
                        Log.e(TAG, "알림 조회 오류", e)
                        continue
                    }
                    
                    val scheduledNotification = notifications.firstOrNull { 
                        it.notifyAt == oneDayBefore && it.channel == "push" && it.sentAt == null
                    }
                    
                    if (scheduledNotification != null) {
                        // 알림 발송
                        sendNotification(context, event)
                        
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
        private fun sendNotification(context: Context, event: Event) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("event_id", event.id)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                event.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("일정 알림")
                .setContentText("내일 '${event.title}' 일정이 있습니다")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            
            val notificationId = NOTIFICATION_ID_PREFIX + event.id.toInt()
            notificationManager.notify(notificationId, notification)
            
            Log.d(TAG, "알림 발송 완료: ${event.title}")
        }
    }
}

