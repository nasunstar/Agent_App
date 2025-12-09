package com.example.agent_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.agent_app.R
import com.example.agent_app.data.db.AppDatabase
import com.example.agent_app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 일정 알림 체크 백그라운드 서비스
 * 주기적으로 일정을 확인하고 알림을 발송합니다.
 */
class EventNotificationScheduler : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var checkJob: Job? = null
    
    companion object {
        private const val TAG = "EventNotificationScheduler"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "event_notification_scheduler"
        private const val CHECK_INTERVAL_HOURS = 1L // 1시간마다 체크
        
        fun startService(context: Context) {
            val intent = Intent(context, EventNotificationScheduler::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, EventNotificationScheduler::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "일정 알림 스케줄러 서비스 시작")
        
        // 알림 채널 생성
        createNotificationChannel()
        EventNotificationService.createNotificationChannel(this)
        
        // 주기적으로 알림 체크
        checkJob = serviceScope.launch {
            while (true) {
                try {
                    Log.d(TAG, "일정 알림 체크 시작")
                    EventNotificationService.checkAndSendNotifications(this@EventNotificationScheduler)
                    
                    // 모든 일정에 대해 알림 스케줄링 확인
                    val database = AppDatabase.build(this@EventNotificationScheduler)
                    val eventDao = database.eventDao()
                    val events = eventDao.getAllEvents()
                    
                    for (event in events) {
                        if (event.startAt != null) {
                            EventNotificationService.scheduleNotificationForEvent(
                                event,
                                eventDao
                            )
                        }
                    }
                    
                    Log.d(TAG, "일정 알림 체크 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "일정 알림 체크 오류", e)
                }
                
                delay(TimeUnit.HOURS.toMillis(CHECK_INTERVAL_HOURS))
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "서비스 시작 명령 수신")
        
        // 포그라운드 서비스로 시작 (Android 8.0 이상 필수)
        // Android 14 이상에서는 서비스 타입을 명시해야 함
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        return START_STICKY // 서비스가 종료되면 자동으로 재시작
    }
    
    /**
     * 알림 채널 생성
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "일정 알림 스케줄러",
                NotificationManager.IMPORTANCE_MIN // 최대한 조용/비표시
            ).apply {
                description = "일정 알림을 체크하는 백그라운드 서비스"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 포그라운드 서비스용 알림 생성
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("일정 알림 모니터링")
            .setContentText("일정 알림을 확인하고 있습니다")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "일정 알림 스케줄러 서비스 종료")
        checkJob?.cancel()
        serviceScope.cancel()
    }
}
