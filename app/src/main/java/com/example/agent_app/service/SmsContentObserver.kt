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
        
        Log.d(TAG, "🔔 SMS 데이터베이스 변경 감지 - uri: $uri, selfChange: $selfChange")
        
        // 너무 자주 호출되는 것을 방지하기 위해 디바운싱
        val now = System.currentTimeMillis()
        val timeSinceLastCheck = now - lastCheckTime
        if (timeSinceLastCheck < CHECK_INTERVAL_MS) {
            Log.d(TAG, "⏸️ 디바운싱: ${timeSinceLastCheck}ms 경과, ${CHECK_INTERVAL_MS}ms 대기 중 (건너뜀)")
            return
        }
        val previousCheckTime = lastCheckTime
        lastCheckTime = now
        
        Log.d(TAG, "✅ 디바운싱 통과 - 이전 체크: $previousCheckTime, 현재: $now, 차이: ${timeSinceLastCheck}ms")
        
        scope.launch {
            try {
                Log.d(TAG, "⏳ SMS 저장 완료 대기 중 (1.5초)...")
                // 데이터베이스 쓰기 완료 대기 (SMS가 완전히 저장될 때까지)
                delay(1500) // 1.5초로 증가 (SMS 저장 완료 대기)
                Log.d(TAG, "✅ 대기 완료, SMS 확인 및 처리 시작")
                checkAndProcessNewSms(previousCheckTime) // 이전 체크 시간 전달
            } catch (e: Exception) {
                Log.e(TAG, "❌ SMS 변경 처리 실패", e)
            }
        }
    }
    
    private suspend fun checkAndProcessNewSms(sinceTimestamp: Long = lastCheckTime - 30000) {
        try {
            Log.d(TAG, "🔍 checkAndProcessNewSms 시작 - sinceTimestamp: $sinceTimestamp")
            
            // 자동 처리 활성화 여부 확인
            val isAutoProcessEnabled = com.example.agent_app.util.AutoProcessSettings.isSmsAutoProcessEnabled(context)
            Log.d(TAG, "📋 자동 처리 활성화 여부: $isAutoProcessEnabled")
            if (!isAutoProcessEnabled) {
                Log.w(TAG, "⚠️ SMS 자동 처리 비활성화 상태 - 처리 건너뜀")
                return
            }
            
            // 최신 SMS 읽기 (최근 2분 이내로 범위 확대, onChange 시점의 SMS를 확실히 포함)
            val now = System.currentTimeMillis()
            val readSince = now - 120000 // 최근 2분 이내
            Log.d(TAG, "📖 SMS 읽기 시작 - 기준 시간: $readSince (현재: $now, 차이: ${now - readSince}ms)")
            val readResult = com.example.agent_app.util.SmsReader.readSmsMessages(context, readSince)
            when (readResult) {
                is com.example.agent_app.util.SmsReader.SmsReadResult.Success -> {
                    Log.d(TAG, "✅ SMS 읽기 성공 - 총 ${readResult.messages.size}개 메시지 발견")
                    // 이전에 처리한 SMS ID를 추적하여 중복 방지
                    val newMessages = readResult.messages.filter { 
                        it.timestamp > readSince && it.timestamp <= now
                    }
                    Log.d(TAG, "🔍 필터링 후 새 SMS: ${newMessages.size}개 (기준: $readSince ~ $now)")
                    
                    if (newMessages.isEmpty()) {
                        Log.d(TAG, "ℹ️ 새 SMS 없음 (모든 메시지가 이미 처리되었거나 범위 밖)")
                    } else {
                        Log.d(TAG, "🎯 새 SMS ${newMessages.size}개 발견 (기준 시간: $readSince)")
                        
                        val appContainer = AppContainer(context)
                        val aiAgent = appContainer.huenDongMinAiAgent
                        val ingestRepository = appContainer.ingestRepository
                        val contactDao = appContainer.contactDao
                        
                        for (sms in newMessages) {
                            val originalSmsId = "sms-auto-${sms.id}"
                            Log.d(TAG, "📨 SMS 처리 시작 - ID: $originalSmsId, 발신자: ${sms.address}, 본문 길이: ${sms.body.length}, 타임스탬프: ${sms.timestamp}")
                            
                            // 중복 체크
                            val existingItem = ingestRepository.getById(originalSmsId)
                            if (existingItem != null) {
                                Log.d(TAG, "⏭️ 이미 처리된 SMS, 건너뜀: $originalSmsId")
                                continue
                            }
                            
                            // 전화번호부 확인 (전화번호부에 있으면 스팸이 아님)
                            val normalizedPhone = com.example.agent_app.util.PhoneNumberUtils.normalize(sms.address)
                            val contact = contactDao.findByPhoneNumber(sms.address, normalizedPhone)
                            if (contact != null) {
                                Log.d(TAG, "✅ 전화번호부에 등록된 번호 - 처리 진행: ${sms.address} (${contact.name})")
                            } else {
                                Log.d(TAG, "ℹ️ 전화번호부에 없는 번호: ${sms.address} (그래도 처리 진행)")
                            }
                            
                            // 기간 확인 (실시간 SMS는 항상 처리됨)
                            val isWithinPeriod = com.example.agent_app.util.AutoProcessSettings.isWithinSmsAutoProcessPeriod(context, sms.timestamp)
                            Log.d(TAG, "📅 기간 확인 - isWithinPeriod: $isWithinPeriod, timestamp: ${sms.timestamp}")
                            if (!isWithinPeriod) {
                                Log.w(TAG, "⚠️ 과거 SMS가 자동 처리 기간 밖 - 건너뜀 (타임스탬프: ${sms.timestamp})")
                                continue
                            }
                            
                            // SMS 처리
                            Log.d(TAG, "🤖 AI 에이전트로 SMS 처리 시작...")
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
                    Log.e(TAG, "❌ SMS 읽기 실패: ${readResult.errorType} - ${readResult.message}")
                    if (readResult.exception != null) {
                        Log.e(TAG, "예외 상세:", readResult.exception)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ SMS 확인 및 처리 실패", e)
            e.printStackTrace()
        }
    }
}

