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
import com.example.agent_app.data.repo.GmailRepositoryWithAi
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
    private lateinit var gmailRepository: GmailRepositoryWithAi
    private lateinit var authRepository: AuthRepository
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var currentProgress: Float = 0f
    private var currentProgressMessage: String = "동기화 준비 중..."
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "gmail_sync_channel"
        private const val SYNC_INTERVAL_MINUTES = 5L // 5분마다 동기화
        
        private const val EXTRA_ACCOUNT_EMAIL = "account_email"
        private const val EXTRA_SINCE_TIMESTAMP = "since_timestamp"
        private const val EXTRA_MANUAL_SYNC = "manual_sync"
        
        fun startService(context: Context, accountEmail: String? = null, sinceTimestamp: Long = 0L, manualSync: Boolean = false) {
            val intent = Intent(context, GmailSyncService::class.java).apply {
                accountEmail?.let { putExtra(EXTRA_ACCOUNT_EMAIL, it) }
                putExtra(EXTRA_SINCE_TIMESTAMP, sinceTimestamp)
                putExtra(EXTRA_MANUAL_SYNC, manualSync)
            }
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
        val gmailApi = GmailServiceFactory.create()
        gmailSyncManager = GmailSyncManager(
            context = applicationContext,
            gmailApi = gmailApi,
            ingestItemDao = appContainer.database.ingestItemDao(),
            classifiedDataRepository = appContainer.classifiedDataRepository
        )
        gmailRepository = GmailRepositoryWithAi(
            api = gmailApi,
            huenDongMinAiAgent = appContainer.huenDongMinAiAgent,
            ingestRepository = appContainer.ingestRepository
        )
        authRepository = appContainer.authRepository
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("GmailSyncService", "서비스 시작")
        
        val manualSync = intent?.getBooleanExtra(EXTRA_MANUAL_SYNC, false) ?: false
        val accountEmail = intent?.getStringExtra(EXTRA_ACCOUNT_EMAIL)
        val sinceTimestamp = intent?.getLongExtra(EXTRA_SINCE_TIMESTAMP, 0L) ?: 0L
        
        startForeground(NOTIFICATION_ID, createNotification("Gmail 동기화 중...", 0f))
        
        if (manualSync && accountEmail != null) {
            // 수동 동기화
            serviceScope.launch {
                performManualSync(accountEmail, sinceTimestamp)
            }
        } else {
            // 주기적 동기화
            startPeriodicSync()
        }
        
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
     * 수동 동기화 수행 (백그라운드에서 실행)
     */
    private suspend fun performManualSync(accountEmail: String, sinceTimestamp: Long) {
        Log.d("GmailSyncService", "수동 동기화 수행 시작 - 계정: $accountEmail")
        
        try {
            val token = authRepository.getGoogleTokenByEmail(accountEmail)
            if (token?.accessToken.isNullOrBlank()) {
                Log.w("GmailSyncService", "토큰이 없어 동기화 건너뛰기")
                updateNotification("토큰이 없습니다", 0f)
                return
            }
            
            var accessToken = token!!.accessToken
            
            // 토큰 만료 체크 및 갱신
            if (token.expiresAt != null && token.expiresAt!! < System.currentTimeMillis()) {
                if (!token.refreshToken.isNullOrBlank()) {
                    try {
                        val refresher = com.example.agent_app.auth.GoogleTokenRefresher()
                        val clientId = com.example.agent_app.BuildConfig.GOOGLE_WEB_CLIENT_ID
                        when (val refreshResult = refresher.refreshAccessToken(token.refreshToken, clientId)) {
                            is com.example.agent_app.auth.TokenRefreshResult.Success -> {
                                accessToken = refreshResult.accessToken
                                authRepository.upsertGoogleToken(
                                    accessToken = refreshResult.accessToken,
                                    refreshToken = token.refreshToken,
                                    scope = token.scope,
                                    expiresAt = refreshResult.expiresAt,
                                    email = accountEmail,
                                )
                            }
                            else -> {
                                updateNotification("토큰 갱신 실패", 0f)
                                return
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GmailSyncService", "토큰 갱신 실패", e)
                        updateNotification("토큰 갱신 실패", 0f)
                        return
                    }
                }
            }
            
            val result = gmailRepository.syncRecentMessages(
                accessToken = accessToken,
                sinceTimestamp = sinceTimestamp,
                onProgress = { progress, message ->
                    currentProgress = progress
                    currentProgressMessage = message
                    updateNotification(message, progress)
                }
            )
            
            when (result) {
                is com.example.agent_app.data.repo.GmailSyncResult.Success -> {
                    Log.d("GmailSyncService", "동기화 성공: ${result.upsertedCount}개 메시지, 일정 ${result.eventCount}개")
                    updateNotification("동기화 완료: ${result.upsertedCount}개 메시지, 일정 ${result.eventCount}개", 1.0f)
                    // 3초 후 알림 제거
                    kotlinx.coroutines.delay(3000)
                    stopSelf()
                }
                is com.example.agent_app.data.repo.GmailSyncResult.Unauthorized -> {
                    Log.w("GmailSyncService", "인증 실패")
                    updateNotification("인증 실패 - 토큰 갱신 필요", 0f)
                    kotlinx.coroutines.delay(3000)
                    stopSelf()
                }
                is com.example.agent_app.data.repo.GmailSyncResult.NetworkError -> {
                    Log.e("GmailSyncService", "네트워크 오류: ${result.message}")
                    updateNotification("동기화 오류: ${result.message}", 0f)
                    kotlinx.coroutines.delay(3000)
                    stopSelf()
                }
                is com.example.agent_app.data.repo.GmailSyncResult.MissingToken -> {
                    Log.w("GmailSyncService", "토큰 없음")
                    updateNotification("토큰 없음", 0f)
                    kotlinx.coroutines.delay(3000)
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e("GmailSyncService", "수동 동기화 중 오류", e)
            updateNotification("동기화 오류: ${e.message}", 0f)
            kotlinx.coroutines.delay(3000)
            stopSelf()
        }
    }
    
    /**
     * 주기적 동기화 수행
     */
    private suspend fun performSync() {
        Log.d("GmailSyncService", "주기적 동기화 수행 시작")
        
        val token = authRepository.getGoogleToken()
        if (token?.accessToken.isNullOrBlank()) {
            Log.w("GmailSyncService", "토큰이 없어 동기화 건너뛰기")
            return
        }
        
        val result = gmailSyncManager.syncIncremental(token!!.accessToken)
        
        when (result) {
            is com.example.agent_app.data.repo.GmailSyncResult.Success -> {
                Log.d("GmailSyncService", "동기화 성공: ${result.upsertedCount}개 메시지")
                updateNotification("마지막 동기화: ${result.upsertedCount}개 새 메시지", 1.0f)
            }
            is com.example.agent_app.data.repo.GmailSyncResult.Unauthorized -> {
                Log.w("GmailSyncService", "인증 실패 - 토큰 갱신 필요")
                updateNotification("인증 실패 - 토큰 갱신 필요", 0f)
            }
            is com.example.agent_app.data.repo.GmailSyncResult.NetworkError -> {
                Log.e("GmailSyncService", "네트워크 오류: ${result.message}")
                updateNotification("동기화 오류: ${result.message}", 0f)
            }
            is com.example.agent_app.data.repo.GmailSyncResult.MissingToken -> {
                Log.w("GmailSyncService", "토큰 없음")
                updateNotification("토큰 없음", 0f)
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
    private fun createNotification(contentText: String, progress: Float = 0f): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gmail 동기화")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(progress < 1.0f)
            .setSilent(true)
        
        // 진행률 표시 (0.0 ~ 1.0)
        if (progress > 0f && progress < 1.0f) {
            builder.setProgress(100, (progress * 100).toInt(), false)
        } else if (progress >= 1.0f) {
            builder.setProgress(0, 0, false) // 진행률 제거
        }
        
        return builder.build()
    }
    
    /**
     * 알림 업데이트
     */
    private fun updateNotification(contentText: String, progress: Float = 0f) {
        val notification = createNotification(contentText, progress)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
