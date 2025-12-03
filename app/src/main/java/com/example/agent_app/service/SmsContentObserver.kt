package com.example.agent_app.service

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.Telephony
import android.util.Log
import com.example.agent_app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SMS 데이터베이스 변경을 모니터링하는 ContentObserver
 * BroadcastReceiver가 작동하지 않는 경우를 대비한 백업 방법
 */
class SmsContentObserver(
    private val context: android.content.Context,
    handler: Handler
) : ContentObserver(handler) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastProcessedSmsId: String = ""
    private var lastCheckTime: Long = System.currentTimeMillis()
    
    companion object {
        private const val TAG = "SmsContentObserver"
        private const val CHECK_INTERVAL_MS = 2000L // 2초마다 확인 (더 빠른 반응)
    }
    
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        Log.d(TAG, "SMS 데이터베이스 변경 감지 - uri: $uri, selfChange: $selfChange")
        
        // 너무 자주 호출되는 것을 방지하기 위해 디바운싱
        val now = System.currentTimeMillis()
        if (now - lastCheckTime < CHECK_INTERVAL_MS) {
            Log.d(TAG, "디바운싱: ${now - lastCheckTime}ms 경과, ${CHECK_INTERVAL_MS}ms 대기 중")
            return
        }
        val previousCheckTime = lastCheckTime
        lastCheckTime = now
        
        scope.launch {
            try {
                // 약간의 지연 후 최신 SMS 확인 (데이터베이스 쓰기 완료 대기)
                delay(1000)
                checkAndProcessNewSms()
            } catch (e: Exception) {
                Log.e(TAG, "SMS 변경 처리 실패", e)
            }
        }
    }
    
    private suspend fun checkAndProcessNewSms() {
        try {
            // 자동 처리 활성화 여부 확인
            val isAutoProcessEnabled = com.example.agent_app.util.AutoProcessSettings.isSmsAutoProcessEnabled(context)
            if (!isAutoProcessEnabled) {
                Log.d(TAG, "SMS 자동 처리 비활성화 상태 - 처리 건너뜀")
                return
            }
            
            // 최신 SMS 읽기
            val readResult = com.example.agent_app.util.SmsReader.readSmsMessages(context, lastCheckTime - 10000) // 최근 10초 이내
            when (readResult) {
                is com.example.agent_app.util.SmsReader.SmsReadResult.Success -> {
                    val cutoffTime = lastCheckTime - 10000 // 최근 10초 이내
                    val newMessages = readResult.messages.filter { 
                        it.timestamp > cutoffTime && it.timestamp <= System.currentTimeMillis()
                    }
                    
                    if (newMessages.isNotEmpty()) {
                        Log.d(TAG, "새 SMS ${newMessages.size}개 발견 (기준 시간: $cutoffTime)")
                        
                        val appContainer = AppContainer(context)
                        val aiAgent = appContainer.huenDongMinAiAgent
                        val ingestRepository = appContainer.ingestRepository
                        val contactDao = appContainer.contactDao
                        
                        for (sms in newMessages) {
                            val originalSmsId = "sms-auto-${sms.id}"
                            
                            // 중복 체크
                            val existingItem = ingestRepository.getById(originalSmsId)
                            if (existingItem != null) {
                                Log.d(TAG, "이미 처리된 SMS, 건너뜀: $originalSmsId")
                                continue
                            }
                            
                            // 전화번호부 확인 (전화번호부에 있으면 스팸이 아님)
                            val normalizedPhone = com.example.agent_app.util.PhoneNumberUtils.normalize(sms.address)
                            val contact = contactDao.findByPhoneNumber(sms.address, normalizedPhone)
                            if (contact != null) {
                                Log.d(TAG, "✅ 전화번호부에 등록된 번호 - 처리 진행: ${sms.address} (${contact.name})")
                            } else {
                                Log.d(TAG, "전화번호부에 없는 번호: ${sms.address}")
                            }
                            
                            // 기간 확인 (실시간 SMS는 항상 처리됨)
                            val isWithinPeriod = com.example.agent_app.util.AutoProcessSettings.isWithinSmsAutoProcessPeriod(context, sms.timestamp)
                            if (!isWithinPeriod) {
                                Log.d(TAG, "과거 SMS가 자동 처리 기간 밖 - 건너뜀 (타임스탬프: ${sms.timestamp})")
                                continue
                            }
                            
                            // SMS 처리
                            val result = aiAgent.processSMSForEvent(
                                smsBody = sms.body,
                                smsAddress = sms.address,
                                receivedTimestamp = sms.timestamp,
                                originalSmsId = originalSmsId
                            )
                            
                            lastCheckTime = sms.timestamp // 마지막 처리 시간 업데이트
                            Log.d(TAG, "✅ SMS 자동 처리 완료 - Type: ${result.type}, Events: ${result.events.size}, 발신자: ${sms.address}")
                        }
                    }
                }
                is com.example.agent_app.util.SmsReader.SmsReadResult.Error -> {
                    Log.e(TAG, "SMS 읽기 실패: ${readResult.errorType} - ${readResult.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS 확인 및 처리 실패", e)
        }
    }
}

