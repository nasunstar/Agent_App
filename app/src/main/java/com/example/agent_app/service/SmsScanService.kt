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
import com.example.agent_app.ai.HuenDongMinAiAgent
import com.example.agent_app.data.repo.ClassifiedDataRepository
import com.example.agent_app.data.repo.IngestRepository
import com.example.agent_app.di.AppContainer
import com.example.agent_app.ui.MainActivity
import com.example.agent_app.util.SmsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * SMS 스캔을 백그라운드에서 처리하는 Foreground Service
 * 진행률을 푸시 알림으로 표시
 */
class SmsScanService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "SmsScanService"
        private const val CHANNEL_ID = "sms_scan_progress"
        private const val CHANNEL_NAME = "SMS 스캔 진행률"
        private const val NOTIFICATION_ID = 2000
        
        const val ACTION_SCAN_COMPLETE = "com.example.agent_app.action.SCAN_COMPLETE"
        const val ACTION_SCAN_PROGRESS = "com.example.agent_app.action.SCAN_PROGRESS"
        const val EXTRA_SINCE_TIMESTAMP = "since_timestamp"
        const val EXTRA_START_TIMESTAMP = "start_timestamp"
        const val EXTRA_END_TIMESTAMP = "end_timestamp"
        const val EXTRA_PROCESSED_COUNT = "processed_count"
        const val EXTRA_TOTAL_COUNT = "total_count"
        const val EXTRA_EVENT_COUNT = "event_count"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_PROGRESS = "progress"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        val sinceTimestamp = intent.getLongExtra(EXTRA_SINCE_TIMESTAMP, 0L)
        
        // sinceTimestamp가 0L이면 모든 SMS를 스캔 (첫 스캔 또는 전체 스캔)
        
        // Foreground Service 시작
        startForeground(NOTIFICATION_ID, createNotification("SMS 스캔 준비 중...", 0, 0))
        
        // 백그라운드에서 스캔 시작
        serviceScope.launch {
            try {
                scanSmsMessages(sinceTimestamp)
            } catch (e: Exception) {
                Log.e(TAG, "SMS 스캔 실패", e)
                updateNotification("SMS 스캔 실패: ${e.message}", 0, 0)
            } finally {
                // 완료 후 서비스 종료
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    private suspend fun scanSmsMessages(sinceTimestamp: Long) {
        val appContainer = AppContainer(this)
        val aiAgent = appContainer.huenDongMinAiAgent
        val ingestRepository = appContainer.ingestRepository
        val database = appContainer.database
        val classifiedDataRepository = ClassifiedDataRepository(
            openAIClassifier = com.example.agent_app.ai.OpenAIClassifier(),
            contactDao = database.contactDao(),
            eventDao = database.eventDao(),
            eventTypeDao = database.eventTypeDao(),
            noteDao = database.noteDao(),
            ingestRepository = ingestRepository,
        )
        
        val startTime = System.currentTimeMillis()
        
        try {
            // SMS 읽기 권한 확인
            if (!checkSmsPermission()) {
                val errorMessage = "SMS 읽기 권한이 필요합니다. 설정에서 권한을 허용해주세요."
                updateNotification(errorMessage, 0, 0)
                
                // 권한 오류 브로드캐스트 전송
                try {
                    val errorIntent = Intent(ACTION_SCAN_COMPLETE).apply {
                        putExtra(EXTRA_START_TIMESTAMP, sinceTimestamp)
                        putExtra(EXTRA_END_TIMESTAMP, System.currentTimeMillis())
                        putExtra(EXTRA_PROCESSED_COUNT, 0)
                        putExtra(EXTRA_EVENT_COUNT, 0)
                        putExtra(EXTRA_MESSAGE, errorMessage)
                        putExtra("error", true)
                        setPackage(packageName)  // 명시적 브로드캐스트
                    }
                    sendBroadcast(errorIntent)
                    Log.d(TAG, "권한 오류 브로드캐스트 전송")
                } catch (e: Exception) {
                    Log.e(TAG, "권한 오류 브로드캐스트 전송 실패", e)
                }
                kotlinx.coroutines.delay(3000)
                cancelNotification()
                return
            }
            
            // SMS 메시지 읽기
            updateNotification("SMS 메시지 읽는 중...", 0, 0)
            // windowEndTimestamp를 현재 시간으로 설정 (기간별 확인 시 필요)
            val windowEndTimestamp = System.currentTimeMillis()
            val readResult = SmsReader.readSmsMessages(this, sinceTimestamp, windowEndTimestamp = windowEndTimestamp)
            
            val messages = when (readResult) {
                is SmsReader.SmsReadResult.Success -> {
                    Log.d(TAG, "읽은 SMS 개수: ${readResult.messages.size}")
                    readResult.messages
                }
                is SmsReader.SmsReadResult.Error -> {
                    val errorMessage = when (readResult.errorType) {
                        SmsReader.SmsReadError.PERMISSION_DENIED -> {
                            "SMS 읽기 권한이 필요합니다. 설정에서 권한을 허용해주세요."
                        }
                        SmsReader.SmsReadError.CONTENT_PROVIDER_ERROR -> {
                            "SMS 데이터베이스에 접근할 수 없습니다. 앱을 재시작해보세요."
                        }
                        SmsReader.SmsReadError.DATA_ERROR -> {
                            "SMS 데이터 형식 오류가 발생했습니다."
                        }
                        SmsReader.SmsReadError.UNKNOWN_ERROR -> {
                            "SMS 읽기 중 오류가 발생했습니다: ${readResult.message}"
                        }
                    }
                    
                    Log.e(TAG, "SMS 읽기 실패: ${readResult.errorType} - ${readResult.message}")
                    if (readResult.exception != null) {
                        Log.e(TAG, "예외 상세", readResult.exception)
                    }
                    
                    updateNotification(errorMessage, 0, 0)
                    
                    // 오류 브로드캐스트 전송
                    try {
                        val errorIntent = Intent(ACTION_SCAN_COMPLETE).apply {
                            putExtra(EXTRA_START_TIMESTAMP, sinceTimestamp)
                            putExtra(EXTRA_END_TIMESTAMP, System.currentTimeMillis())
                            putExtra(EXTRA_PROCESSED_COUNT, 0)
                            putExtra(EXTRA_EVENT_COUNT, 0)
                            putExtra(EXTRA_MESSAGE, errorMessage)
                            putExtra("error", true)  // 오류 플래그
                            setPackage(packageName)  // 명시적 브로드캐스트
                        }
                        sendBroadcast(errorIntent)
                        Log.d(TAG, "오류 브로드캐스트 전송: ${readResult.errorType}")
                    } catch (e: Exception) {
                        Log.e(TAG, "오류 브로드캐스트 전송 실패", e)
                    }
                    kotlinx.coroutines.delay(5000)
                    cancelNotification()
                    return
                }
            }
            
            if (messages.isEmpty()) {
                val messageText = if (sinceTimestamp > 0L) {
                    "지정된 날짜 이후의 SMS 메시지가 없습니다."
                } else {
                    "SMS 메시지가 없습니다."
                }
                updateNotification(messageText, 0, 0)
                // 완료 브로드캐스트 전송 (빈 결과)
                try {
                    val completeIntent = Intent(ACTION_SCAN_COMPLETE).apply {
                        putExtra(EXTRA_START_TIMESTAMP, sinceTimestamp)
                        putExtra(EXTRA_END_TIMESTAMP, System.currentTimeMillis())
                        putExtra(EXTRA_PROCESSED_COUNT, 0)
                        putExtra(EXTRA_EVENT_COUNT, 0)
                        putExtra(EXTRA_MESSAGE, messageText)
                        setPackage(packageName)  // 명시적 브로드캐스트
                    }
                    sendBroadcast(completeIntent)
                    Log.d(TAG, "빈 결과 브로드캐스트 전송")
                } catch (e: Exception) {
                    Log.e(TAG, "빈 결과 브로드캐스트 전송 실패", e)
                }
                kotlinx.coroutines.delay(3000)
                cancelNotification()
                return
            }
            
            // 메시지 범위 계산 (최신/오래된 메시지의 타임스탬프)
            val messageTimestamps = messages.map { it.timestamp }
            val startTimestamp = messageTimestamps.minOrNull() ?: sinceTimestamp
            val endTimestamp = messageTimestamps.maxOrNull() ?: System.currentTimeMillis()
            
            // 전체 개수 브로드캐스트 전송
            val totalCount = messages.size
            val initialProgressIntent = Intent(ACTION_SCAN_PROGRESS).apply {
                putExtra(EXTRA_START_TIMESTAMP, startTimestamp)
                putExtra(EXTRA_END_TIMESTAMP, endTimestamp)
                putExtra(EXTRA_PROCESSED_COUNT, 0)
                putExtra(EXTRA_TOTAL_COUNT, totalCount)
                putExtra(EXTRA_PROGRESS, 0f)
                putExtra(EXTRA_MESSAGE, "SMS 메시지 읽기 완료: ${totalCount}개")
            }
            sendBroadcast(initialProgressIntent)
            
            // 각 SMS 메시지를 AI로 처리
            var processedCount = 0
            var eventCount = 0
            
            for ((index, sms) in messages.withIndex()) {
                try {
                    val currentProgress = (index + 1).toFloat() / totalCount.toFloat()
                    updateNotification(
                        "처리 중: ${index + 1}/${totalCount}",
                        index + 1,
                        totalCount
                    )
                    
                    // 진행 상황 브로드캐스트 전송
                    val progressIntent = Intent(ACTION_SCAN_PROGRESS).apply {
                        putExtra(EXTRA_START_TIMESTAMP, startTimestamp)
                        putExtra(EXTRA_END_TIMESTAMP, endTimestamp)
                        putExtra(EXTRA_PROCESSED_COUNT, index + 1)
                        putExtra(EXTRA_TOTAL_COUNT, totalCount)
                        putExtra(EXTRA_PROGRESS, currentProgress)
                        putExtra(EXTRA_MESSAGE, "처리 중: ${index + 1}/${totalCount}")
                    }
                    sendBroadcast(progressIntent)
                    
                    val result = aiAgent.processSMSForEvent(
                        smsBody = sms.body,
                        smsAddress = sms.address,
                        receivedTimestamp = sms.timestamp,
                        originalSmsId = "sms-${sms.id}"
                    )
                    
                    processedCount++
                    if (result.type == "event" && result.events.isNotEmpty()) {
                        eventCount += result.events.size
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "SMS 처리 실패: ${sms.id}", e)
                    // 개별 SMS 처리 실패해도 계속 진행
                    processedCount++
                }
            }
            
            // 분류된 데이터 다시 로드
            val contacts = classifiedDataRepository.getAllContacts()
            val events = classifiedDataRepository.getAllEvents()
            val notes = classifiedDataRepository.getAllNotes()
            
            val endTime = System.currentTimeMillis()
            val updateMessage = "${processedCount}개의 SMS를 처리했습니다. ${eventCount}개의 일정을 추출했습니다."
            
            // 완료 알림
            updateNotification(updateMessage, totalCount, totalCount)
            
            Log.d(TAG, "SMS 스캔 완료: ${processedCount}개 처리, ${eventCount}개 일정 추출")
            Log.d(TAG, "시간 범위: ${java.time.Instant.ofEpochMilli(startTimestamp).atZone(java.time.ZoneId.of("Asia/Seoul"))} ~ ${java.time.Instant.ofEpochMilli(endTimestamp).atZone(java.time.ZoneId.of("Asia/Seoul"))}")
            
            // 완료 브로드캐스트 전송 (MainViewModel에서 기록 업데이트)
            try {
                val completeIntent = Intent(ACTION_SCAN_COMPLETE).apply {
                    putExtra(EXTRA_START_TIMESTAMP, startTimestamp)
                    putExtra(EXTRA_END_TIMESTAMP, endTimestamp)
                    putExtra(EXTRA_PROCESSED_COUNT, processedCount)
                    putExtra(EXTRA_EVENT_COUNT, eventCount)
                    putExtra(EXTRA_MESSAGE, updateMessage)
                    setPackage(packageName)  // 명시적 브로드캐스트
                }
                sendBroadcast(completeIntent)
                Log.d(TAG, "완료 브로드캐스트 전송: ${processedCount}개 처리, ${eventCount}개 일정")
            } catch (e: Exception) {
                Log.e(TAG, "완료 브로드캐스트 전송 실패", e)
            }
            
            // 3초 후 알림 제거
            kotlinx.coroutines.delay(3000)
            cancelNotification()
            
        } catch (e: Exception) {
            Log.e(TAG, "SMS 스캔 실패", e)
            val errorMessage = "SMS 스캔 중 오류가 발생했습니다: ${e.message ?: e.javaClass.simpleName}"
            updateNotification(errorMessage, 0, 0)
            
            // 오류 브로드캐스트 전송 (UI가 무한 로딩되지 않도록)
            try {
                val errorIntent = Intent(ACTION_SCAN_COMPLETE).apply {
                    putExtra(EXTRA_START_TIMESTAMP, sinceTimestamp)
                    putExtra(EXTRA_END_TIMESTAMP, System.currentTimeMillis())
                    putExtra(EXTRA_PROCESSED_COUNT, 0)
                    putExtra(EXTRA_EVENT_COUNT, 0)
                    putExtra(EXTRA_MESSAGE, errorMessage)
                    putExtra("error", true)
                    setPackage(packageName)  // 명시적 브로드캐스트
                }
                sendBroadcast(errorIntent)
                Log.d(TAG, "예외 발생 브로드캐스트 전송")
            } catch (e: Exception) {
                Log.e(TAG, "예외 발생 브로드캐스트 전송 실패", e)
            }
            
            kotlinx.coroutines.delay(3000)
            cancelNotification()
        }
    }
    
    private fun checkSmsPermission(): Boolean {
        return android.content.pm.PackageManager.PERMISSION_GRANTED ==
            checkSelfPermission(android.Manifest.permission.READ_SMS)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SMS 스캔 진행률 알림"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(
        contentText: String,
        current: Int,
        total: Int
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS 메시지 스캔")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(total, current, total == 0)
        
        return builder.build()
    }
    
    private fun updateNotification(contentText: String, current: Int, total: Int) {
        val notification = createNotification(contentText, current, total)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun cancelNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID)
    }
}

