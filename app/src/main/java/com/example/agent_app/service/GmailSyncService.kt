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
import com.example.agent_app.data.repo.AuthRepository
import com.example.agent_app.data.repo.GmailSyncManager
import com.example.agent_app.di.AppContainer
import com.example.agent_app.gmail.GmailServiceFactory
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
 * Gmail 실시간 동기화 백그라운드 서비스
 * 주기적으로 Gmail을 동기화하고 중복을 방지합니다.
 */
class GmailSyncService : Service() {
    
    private lateinit var appContainer: AppContainer
    private lateinit var gmailSyncManager: GmailSyncManager
    private lateinit var authRepository: AuthRepository
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "gmail_sync_channel"
        private const val SYNC_INTERVAL_MINUTES = 5L // 5분마다 동기화
        
        fun startService(context: Context) {
            val intent = Intent(context, GmailSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, GmailSyncService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("GmailSyncService", "서비스 생성")
        
        appContainer = AppContainer(applicationContext)
        gmailSyncManager = GmailSyncManager(
            context = applicationContext,
            gmailApi = GmailServiceFactory.create(),
            ingestItemDao = appContainer.database.ingestItemDao(),
            classifiedDataRepository = appContainer.classifiedDataRepository
        )
        authRepository = appContainer.authRepository
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("GmailSyncService", "서비스 시작")
        
        startForeground(NOTIFICATION_ID, createNotification("Gmail 동기화 중..."))
        startPeriodicSync()
        
        return START_STICKY // 서비스가 종료되면 자동으로 재시작
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("GmailSyncService", "서비스 종료")
        syncJob?.cancel()
        serviceScope.cancel()
        appContainer.close()
    }
    
    /**
     * 주기적 동기화 시작
     */
    private fun startPeriodicSync() {
        syncJob = serviceScope.launch {
            while (true) {
                try {
                    performSync()
                    delay(TimeUnit.MINUTES.toMillis(SYNC_INTERVAL_MINUTES))
                } catch (e: Exception) {
                    Log.e("GmailSyncService", "동기화 오류", e)
                    delay(TimeUnit.MINUTES.toMillis(1)) // 오류 시 1분 후 재시도
                }
            }
        }
    }
    
    /**
     * 실제 동기화 수행
     */
    private suspend fun performSync() {
        Log.d("GmailSyncService", "동기화 수행 시작")
        
        val token = authRepository.getGoogleToken()
        if (token?.accessToken.isNullOrBlank()) {
            Log.w("GmailSyncService", "토큰이 없어 동기화 건너뛰기")
            return
        }
        
        val result = gmailSyncManager.syncIncremental(token!!.accessToken)
        
        when (result) {
            is com.example.agent_app.data.repo.GmailSyncResult.Success -> {
                Log.d("GmailSyncService", "동기화 성공: ${result.upsertedCount}개 메시지")
                updateNotification("마지막 동기화: ${result.upsertedCount}개 새 메시지")
            }
            is com.example.agent_app.data.repo.GmailSyncResult.Unauthorized -> {
                Log.w("GmailSyncService", "인증 실패 - 토큰 갱신 필요")
                updateNotification("인증 실패 - 토큰 갱신 필요")
            }
            is com.example.agent_app.data.repo.GmailSyncResult.NetworkError -> {
                Log.e("GmailSyncService", "네트워크 오류: ${result.message}")
                updateNotification("동기화 오류: ${result.message}")
            }
            is com.example.agent_app.data.repo.GmailSyncResult.MissingToken -> {
                Log.w("GmailSyncService", "토큰 없음")
                updateNotification("토큰 없음")
            }
        }
    }
    
    /**
     * 알림 채널 생성
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gmail 동기화",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Gmail 실시간 동기화 상태"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 포그라운드 서비스용 알림 생성
     */
    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gmail 동기화")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    /**
     * 알림 업데이트
     */
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
