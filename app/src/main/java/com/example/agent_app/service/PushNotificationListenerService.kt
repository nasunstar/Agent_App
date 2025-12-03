package com.example.agent_app.service

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.agent_app.data.db.AppDatabase
import com.example.agent_app.data.entity.PushNotification
import com.example.agent_app.di.AppContainer
import com.example.agent_app.util.PushNotificationFilterSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 푸시 알림을 수신하고 데이터베이스에 저장하는 서비스
 */
class PushNotificationListenerService : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var database: AppDatabase? = null
    private var appContainer: AppContainer? = null
    
    // MOA-LLM-Optimization: 중복 처리 방지를 위한 처리된 알림 ID 추적
    private val processedNotificationIds = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.build(this)
        appContainer = AppContainer(this)
        Log.d(TAG, "PushNotificationListenerService 생성됨")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        try {
            val notification = sbn.notification
            val packageName = sbn.packageName
            val timestamp = sbn.postTime
            
            // 알림이 실제로 표시되는 경우만 저장 (중복 방지)
            val isGroupSummary = notification.extras.getBoolean("android.isGroupSummary", false)
            if (!isGroupSummary) {
                saveNotification(packageName, notification, timestamp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "알림 저장 실패", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // 알림이 제거되었을 때의 처리 (필요시)
    }

    private fun saveNotification(
        packageName: String,
        notification: android.app.Notification,
        timestamp: Long
    ) {
        serviceScope.launch {
            try {
                // 설정된 차단 목록에 있는 앱이면 저장/처리하지 않음
                val ctx = applicationContext
                if (PushNotificationFilterSettings.isPackageExcluded(ctx, packageName)) {
                    Log.d(TAG, "차단된 앱의 푸시 알림, 저장/처리 건너뜀: $packageName")
                    return@launch
                }
                
                val appName = getAppName(packageName)
                val title = notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                val text = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
                
                // 내용 기반 스팸 필터링 추가
                if (PushNotificationFilterSettings.isSpamNotification(title, text, packageName)) {
                    Log.d(TAG, "스팸 알림으로 판단되어 처리 건너뜀: $packageName - $title")
                    return@launch
                }
                val subText = notification.extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
                
                // 추가 메타데이터 수집
                val metaJson = buildMetaJson(notification)
                
                // 푸시 알림 ID 생성 (타임스탬프 + 패키지명 기반)
                val notificationId = "push-${timestamp}-${packageName.hashCode()}"
                
                // MOA-LLM-Optimization: 중복 처리 방지
                val uniqueKey = "${packageName}:${title}:${text}:${timestamp}"
                if (processedNotificationIds.contains(uniqueKey)) {
                    Log.d(TAG, "이미 처리된 푸시 알림, 건너뜀: $uniqueKey")
                    return@launch
                }
                processedNotificationIds.add(uniqueKey)
                // 메모리 관리: 최근 1000개만 유지
                if (processedNotificationIds.size > 1000) {
                    processedNotificationIds.remove(processedNotificationIds.first())
                }
                
                val pushNotification = PushNotification(
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    subText = subText,
                    timestamp = timestamp,
                    metaJson = metaJson
                )
                
                database?.pushNotificationDao()?.insert(pushNotification)
                
                Log.d(TAG, "푸시 알림 저장 완료: $appName - $title")
                
                // OpenAI로 자동 처리
                try {
                    val container = appContainer
                    if (container != null) {
                        val aiAgent = container.huenDongMinAiAgent
                        
                        val result = aiAgent.processPushNotificationForEvent(
                            appName = appName,
                            notificationTitle = title,
                            notificationText = text,
                            notificationSubText = subText,
                            receivedTimestamp = timestamp,
                            originalNotificationId = notificationId
                        )
                        
                        Log.d(TAG, "푸시 알림 자동 처리 완료 - Type: ${result.type}, Events: ${result.events.size}")
                    } else {
                        Log.w(TAG, "AppContainer가 초기화되지 않음")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "푸시 알림 자동 처리 실패", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "푸시 알림 저장 중 오류", e)
            }
        }
    }

    private fun getAppName(packageName: String): String? {
        return try {
            val packageManager = packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            Log.w(TAG, "앱 이름 가져오기 실패: $packageName", e)
            null
        }
    }

    private fun buildMetaJson(notification: android.app.Notification): String? {
        return try {
            val meta = JSONObject()
            
            // 카테고리
            notification.category?.let { meta.put("category", it) }
            
            // 채널 ID
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                notification.channelId?.let { meta.put("channel_id", it) }
            }
            
            // 우선순위
            meta.put("priority", notification.priority)
            
            // 작은 아이콘
            if (notification.getSmallIcon() != null) {
                meta.put("has_small_icon", true)
            }
            
            // 큰 아이콘
            if (notification.getLargeIcon() != null) {
                meta.put("has_large_icon", true)
            }
            
            // 사운드
            meta.put("has_sound", notification.sound != null)
            
            // 진동
            meta.put("has_vibration", notification.vibrate != null)
            
            // LED
            meta.put("has_led", notification.ledARGB != 0)
            
            if (meta.length() > 0) {
                meta.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "메타데이터 생성 실패", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        appContainer?.close()
        appContainer = null
        Log.d(TAG, "PushNotificationListenerService 종료됨")
    }

    companion object {
        private const val TAG = "PushNotificationListener"
    }
}

