package com.example.agent_app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.agent_app.R
import com.example.agent_app.data.repo.AuthRepository
import com.example.agent_app.data.repo.GmailRepositoryWithAi
import com.example.agent_app.di.AppContainer
import com.example.agent_app.gmail.GmailServiceFactory
import com.example.agent_app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Gmail 실시간 동기화 Worker
 * 1분마다 백그라운드에서 실행되어 새 Gmail 메시지를 확인하고 처리합니다.
 */
class GmailRealtimeSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Gmail 실시간 동기화 시작 (백그라운드 실행, 5초 주기)")
            Log.d(TAG, "📱 현재 스레드: ${Thread.currentThread().name}, 백그라운드: ${!Thread.currentThread().name.contains("main")}")
            
            // Gmail 자동 처리 활성화 여부 확인
            val isAutoProcessEnabled = com.example.agent_app.util.AutoProcessSettings.isGmailAutoProcessEnabled(applicationContext)
            if (!isAutoProcessEnabled) {
                Log.d(TAG, "⚠️ Gmail 자동 처리 비활성화 - 동기화 건너뜀")
                return@withContext Result.success()
            }
            
            val appContainer = AppContainer(applicationContext)
            val authRepository = appContainer.authRepository
            val gmailRepository = GmailRepositoryWithAi(
                api = GmailServiceFactory.create(),
                huenDongMinAiAgent = appContainer.huenDongMinAiAgent,
                ingestRepository = appContainer.ingestRepository
            )
            
            // 모든 Google 계정 가져오기
            val accounts = authRepository.getAllGoogleTokens()
            if (accounts.isEmpty()) {
                Log.d(TAG, "ℹ️ 동기화할 계정이 없음 - 다음 작업 스케줄링 안 함")
                // 토큰이 없으면 다음 작업을 스케줄링하지 않음 (메모리 절약)
                return@withContext Result.success()
            }
            
            // 토큰이 있는 계정이 있는지 확인
            val hasValidToken = accounts.any { it.accessToken.isNotBlank() }
            if (!hasValidToken) {
                Log.d(TAG, "ℹ️ 유효한 토큰이 없음 - 다음 작업 스케줄링 안 함")
                // 토큰이 없으면 다음 작업을 스케줄링하지 않음 (메모리 절약)
                return@withContext Result.success()
            }
            
            Log.d(TAG, "📧 총 ${accounts.size}개 계정 확인 중...")
            
            var successCount = 0
            var errorCount = 0
            
            for (account in accounts) {
                try {
                    // 토큰이 있는 계정만 처리
                    if (account.accessToken.isBlank()) {
                        Log.d(TAG, "⏭️ 계정 ${account.email}의 토큰이 없어 건너뜀")
                        continue
                    }
                    
                    Log.d(TAG, "📬 계정 ${account.email} 동기화 시작...")
                    
                    // 마지막 동기화 시간 가져오기 (없으면 최근 1분 이내만)
                    val lastSyncTime = getLastSyncTime(applicationContext, account.email)
                    val sinceTimestamp = if (lastSyncTime > 0L) {
                        lastSyncTime
                    } else {
                        // 첫 동기화이거나 시간이 없으면 최근 1분 이내만
                        System.currentTimeMillis() - (60 * 1000)
                    }
                    
                    Log.d(TAG, "⏰ 동기화 범위: ${sinceTimestamp} ~ 현재 (최근 ${(System.currentTimeMillis() - sinceTimestamp) / 1000}초)")
                    
                    var accessToken = account.accessToken
                    
                    // 토큰 만료 체크 및 갱신
                    if (account.expiresAt != null && account.expiresAt!! < System.currentTimeMillis()) {
                        if (!account.refreshToken.isNullOrBlank()) {
                            try {
                                val refresher = com.example.agent_app.auth.GoogleTokenRefresher()
                                val clientId = com.example.agent_app.BuildConfig.GOOGLE_WEB_CLIENT_ID
                                when (val refreshResult = refresher.refreshAccessToken(account.refreshToken, clientId)) {
                                    is com.example.agent_app.auth.TokenRefreshResult.Success -> {
                                        accessToken = refreshResult.accessToken
                                        authRepository.upsertGoogleToken(
                                            accessToken = refreshResult.accessToken,
                                            refreshToken = account.refreshToken,
                                            scope = account.scope,
                                            expiresAt = refreshResult.expiresAt,
                                            email = account.email,
                                        )
                                        Log.d(TAG, "✅ 계정 ${account.email}의 토큰 갱신 완료")
                                    }
                                    else -> {
                                        Log.w(TAG, "⚠️ 계정 ${account.email}의 토큰 갱신 실패")
                                        // 토큰 갱신 실패 알림
                                        showTokenExpiredNotification(applicationContext, account.email, "토큰 갱신에 실패했습니다. 다시 로그인해주세요.")
                                        continue
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ 계정 ${account.email}의 토큰 갱신 실패", e)
                                // 토큰 갱신 실패 알림
                                showTokenExpiredNotification(applicationContext, account.email, "토큰 갱신 중 오류가 발생했습니다: ${e.message}")
                                continue
                            }
                        } else {
                            Log.w(TAG, "⚠️ 계정 ${account.email}의 Refresh Token이 없어 갱신 불가")
                            // Refresh Token 없음 알림
                            showTokenExpiredNotification(applicationContext, account.email, "Refresh Token이 없어 토큰을 갱신할 수 없습니다. 다시 로그인해주세요.")
                            continue
                        }
                    }
                    
                    // 최신 메일만 동기화 (sinceTimestamp 이후)
                    val result = gmailRepository.syncRecentMessages(
                        accessToken = accessToken,
                        sinceTimestamp = sinceTimestamp,
                        onProgress = { _, _ -> } // 백그라운드 작업이므로 진행률 업데이트 불필요
                    )
                    
                    when (result) {
                        is com.example.agent_app.data.repo.GmailSyncResult.Success -> {
                            // 마지막 동기화 시간 업데이트
                            saveLastSyncTime(applicationContext, account.email, result.endTimestamp)
                            Log.d(TAG, "✅ 계정 ${account.email} 동기화 완료 - ${result.upsertedCount}개 메시지, ${result.eventCount}개 일정 추출")
                            successCount++
                        }
                        is com.example.agent_app.data.repo.GmailSyncResult.Unauthorized -> {
                            Log.w(TAG, "⚠️ 계정 ${account.email} 인증 실패 (401)")
                            // 인증 실패 알림 (토큰 만료 가능성)
                            showTokenExpiredNotification(applicationContext, account.email, "Gmail 인증에 실패했습니다. 토큰이 만료되었을 수 있습니다.")
                            errorCount++
                        }
                        is com.example.agent_app.data.repo.GmailSyncResult.NetworkError -> {
                            Log.w(TAG, "⚠️ 계정 ${account.email} 네트워크 오류: ${result.message}")
                            errorCount++
                        }
                        com.example.agent_app.data.repo.GmailSyncResult.MissingToken -> {
                            Log.w(TAG, "⚠️ 계정 ${account.email} 토큰 없음")
                            errorCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 계정 ${account.email} 동기화 실패", e)
                    errorCount++
                }
            }
            
            Log.d(TAG, "✅ Gmail 실시간 동기화 완료 - 성공: $successCount, 실패: $errorCount")
            
            // 체인 방식: 성공 시 다음 작업 스케줄링 (5초 후)
            // 토큰이 있는 경우에만 계속 실행
            val hasToken = authRepository.getAllGoogleTokens().any { it.accessToken.isNotBlank() }
            if (hasToken) {
                scheduleNextWork(applicationContext, 5)
            } else {
                Log.d(TAG, "ℹ️ 토큰이 없어 다음 작업 스케줄링 안 함")
            }
            
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Gmail 실시간 동기화 실패", e)
            
            // 실패 시에도 토큰이 있으면 다음 작업 스케줄링 (5초 후 재시도)
            try {
                val appContainer = AppContainer(applicationContext)
                val authRepository = appContainer.authRepository
                val hasToken = authRepository.getAllGoogleTokens().any { it.accessToken.isNotBlank() }
                if (hasToken) {
                    scheduleNextWork(applicationContext, 5)
                } else {
                    Log.d(TAG, "ℹ️ 토큰이 없어 다음 작업 스케줄링 안 함")
                }
            } catch (checkError: Exception) {
                Log.e(TAG, "토큰 확인 실패", checkError)
            }
            
            Result.success() // 실패해도 다음 작업은 계속 진행
        }
    }
    
    /**
     * 계정별 마지막 동기화 시간 가져오기
     */
    private fun getLastSyncTime(context: Context, email: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("last_sync_${email.hashCode()}", 0L)
    }
    
    /**
     * 계정별 마지막 동기화 시간 저장
     */
    private fun saveLastSyncTime(context: Context, email: String, timestamp: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("last_sync_${email.hashCode()}", timestamp).apply()
    }
    
    /**
     * 토큰 만료 알림 표시 (중복 방지)
     */
    private fun showTokenExpiredNotification(context: Context, email: String, message: String) {
        try {
            // 중복 알림 방지: 최근 1시간 이내에 같은 계정에 대한 알림이 있었는지 확인
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastNotificationTime = prefs.getLong("last_notification_${email.hashCode()}", 0L)
            val now = System.currentTimeMillis()
            val oneHourAgo = now - (60 * 60 * 1000)
            
            if (lastNotificationTime > oneHourAgo) {
                Log.d(TAG, "⏭️ 계정 $email 에 대한 알림이 최근에 표시되어 건너뜀 (중복 방지)")
                return
            }
            
            // 마지막 알림 시간 저장
            prefs.edit().putLong("last_notification_${email.hashCode()}", now).apply()
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 알림 채널 생성 (Android O 이상)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            // 알림 클릭 시 앱 열기
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                email.hashCode(), // 계정별로 다른 ID 사용
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // 시스템 기본 경고 아이콘 사용
                .setContentTitle("Gmail 토큰 만료")
                .setContentText("$email: $message")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("계정: $email\n\n$message\n\n앱을 열어 다시 로그인해주세요."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            
            // 계정별로 다른 알림 ID 사용 (여러 계정 동시 만료 시 각각 표시)
            notificationManager.notify(email.hashCode(), notification)
            
            Log.d(TAG, "📢 토큰 만료 알림 표시: $email")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 알림 표시 실패", e)
        }
    }
    
    companion object {
        private const val TAG = "GmailRealtimeSyncWorker"
        private const val PREFS_NAME = "gmail_realtime_sync"
        private const val WORK_NAME = "gmail_realtime_sync_work"
        private const val CHANNEL_ID = "gmail_token_expired_channel"
        private const val CHANNEL_NAME = "Gmail 토큰 만료 알림"
        private const val CHANNEL_DESCRIPTION = "Gmail 계정 토큰이 만료되었을 때 알림을 표시합니다."
        
        /**
         * 주기적 작업 시작 (테스트용: 5초마다 실행)
         * 주의: WorkManager의 PeriodicWorkRequest는 시스템 제약으로 최소 15분 간격이 적용될 수 있습니다.
         * 더 짧은 간격이 필요하면 OneTimeWorkRequest 체인 방식을 사용하세요.
         */
        fun startPeriodicWork(context: Context) {
            // 테스트용: 5초 간격 (실제로는 시스템이 조정할 수 있음)
            val workRequest = PeriodicWorkRequestBuilder<GmailRealtimeSyncWorker>(
                5, TimeUnit.SECONDS, // 최소 간격 (테스트용)
                10, TimeUnit.SECONDS  // 유연한 간격
            )
                .addTag(WORK_NAME)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP, // 이미 있으면 유지
                workRequest
            )
            
            Log.d(TAG, "✅ Gmail 실시간 동기화 작업 등록 완료 (테스트용: 5초 주기)")
        }
        
        /**
         * Gmail 실시간 동기화 시작 (토큰 체크 후 시작)
         * Google API 토큰이 있으면 5초 주기로 동기화 시작, 없으면 시작하지 않음
         */
        fun startRepeatingWorkIfTokenExists(context: Context) {
            // Coroutine scope에서 실행 (suspend 함수 호출을 위해)
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    // Gmail 자동 처리 활성화 여부 확인
                    val isAutoProcessEnabled = com.example.agent_app.util.AutoProcessSettings.isGmailAutoProcessEnabled(context)
                    if (!isAutoProcessEnabled) {
                        Log.d(TAG, "⚠️ Gmail 자동 처리 비활성화 - 동기화 시작 안 함")
                        stopRepeatingWork(context)
                        return@launch
                    }
                    
                    // Google API 토큰 확인
                    val appContainer = AppContainer(context)
                    val authRepository = appContainer.authRepository
                    val accounts = authRepository.getAllGoogleTokens() // suspend 함수
                    
                    // 토큰이 있는 계정이 있는지 확인
                    val hasValidToken = accounts.any { it.accessToken.isNotBlank() }
                    
                    if (!hasValidToken) {
                        Log.d(TAG, "ℹ️ Google API 토큰이 없어 Gmail 동기화 시작 안 함")
                        stopRepeatingWork(context)
                        return@launch
                    }
                    
                    // 토큰이 있으면 동기화 시작
                    startRepeatingWork(context)
                    Log.d(TAG, "✅ Gmail 실시간 동기화 시작 (토큰 확인 완료, 5초 주기)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Gmail 동기화 시작 확인 실패", e)
                    stopRepeatingWork(context)
                }
            }
        }
        
        /**
         * 더 짧은 간격을 위한 OneTimeWorkRequest 체인 방식 (테스트용)
         * 5초마다 실행되도록 체인으로 연결
         * 주의: 이 함수는 토큰 체크를 하지 않으므로, startRepeatingWorkIfTokenExists()를 사용하세요
         */
        private fun startRepeatingWork(context: Context) {
            val workManager = WorkManager.getInstance(context)
            
            // 기존 작업 취소
            workManager.cancelAllWorkByTag(WORK_NAME)
            
            // 첫 작업 시작
            scheduleNextWork(context, 5) // 5초 후 실행
            
            Log.d(TAG, "✅ Gmail 실시간 동기화 작업 시작 (체인 방식, 5초 주기)")
        }
        
        /**
         * Gmail 실시간 동기화 중지
         */
        fun stopRepeatingWork(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelAllWorkByTag(WORK_NAME)
            Log.d(TAG, "⏹️ Gmail 실시간 동기화 중지")
        }
        
        /**
         * 다음 작업 스케줄링 (체인 방식)
         * Worker 내부에서 성공 시 자동으로 다음 작업을 스케줄링
         */
        fun scheduleNextWork(context: Context, delaySeconds: Long) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<GmailRealtimeSyncWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .addTag(WORK_NAME)
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "📅 다음 Gmail 동기화 작업 스케줄링: ${delaySeconds}초 후")
        }
        
        /**
         * 주기적 작업 중지
         */
        fun stopPeriodicWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "⏹️ Gmail 실시간 동기화 작업 중지")
        }
    }
}

