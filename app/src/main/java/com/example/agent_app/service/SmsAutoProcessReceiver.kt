package com.example.agent_app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.agent_app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * SMS 수신 시 자동으로 메시지를 분류하는 BroadcastReceiver
 */
class SmsAutoProcessReceiver : BroadcastReceiver() {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "BroadcastReceiver 호출됨 - Action: ${intent.action}")
        
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) {
            Log.d(TAG, "SMS_RECEIVED_ACTION이 아님, 건너뜀: ${intent.action}")
            return
        }
        
        Log.d(TAG, "새 SMS 수신 감지")
        
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) {
                Log.d(TAG, "수신된 SMS 메시지가 없습니다.")
                return
            }
            
            // 첫 번째 메시지만 처리 (멀티파트 SMS의 경우)
            val smsMessage = messages[0]
            val address = smsMessage.displayOriginatingAddress ?: "Unknown"
            val body = smsMessage.messageBody ?: ""
            val timestamp = smsMessage.timestampMillis
            val smsId = smsMessage.indexOnIcc?.toString() ?: System.currentTimeMillis().toString()
            val originalSmsId = "sms-auto-$smsId"
            
            Log.d(TAG, "SMS 처리 시작 - 발신자: $address, 본문 길이: ${body.length}, 타임스탬프: $timestamp")
            
            // 자동 처리 활성화 여부 확인
            val isAutoProcessEnabled = com.example.agent_app.util.AutoProcessSettings.isSmsAutoProcessEnabled(context)
            Log.d(TAG, "SMS 자동 처리 활성화 여부: $isAutoProcessEnabled")
            
            if (!isAutoProcessEnabled) {
                Log.w(TAG, "⚠️ SMS 자동 처리 비활성화 상태 - 처리 건너뜀")
                Log.w(TAG, "⚠️ 앱을 재시작하거나 MainActivity에서 자동 처리가 활성화되었는지 확인하세요")
                return
            }
            
            // 기간 확인 (실시간 SMS는 항상 처리됨)
            val isWithinPeriod = com.example.agent_app.util.AutoProcessSettings.isWithinSmsAutoProcessPeriod(context, timestamp)
            val period = com.example.agent_app.util.AutoProcessSettings.getSmsAutoProcessPeriod(context)
            val now = System.currentTimeMillis()
            val isRecent = timestamp >= (now - 60 * 60 * 1000) // 최근 1시간 이내
            Log.d(TAG, "기간 확인 - isWithinPeriod: $isWithinPeriod, period: $period, isRecent: $isRecent, timestamp: $timestamp, now: $now")
            
            if (!isWithinPeriod) {
                if (period != null) {
                    Log.w(TAG, "⚠️ 과거 SMS가 자동 처리 기간 범위 밖 - 처리 건너뜀 (기간: ${period.first} ~ ${period.second})")
                } else {
                    Log.w(TAG, "⚠️ SMS 자동 처리 기간이 설정되지 않음 - 실시간 동기화 모드여야 하는데 isWithinPeriod가 false")
                }
                return
            }
            
            Log.d(TAG, "✅ SMS 자동 처리 조건 충족 - 처리 진행")
            
            // 백그라운드에서 처리 (트리거 방식: SMS 수신 시에만 실행)
            scope.launch {
                try {
                    // AppContainer에서 의존성 가져오기
                    val appContainer = AppContainer(context)
                    val aiAgent = appContainer.huenDongMinAiAgent
                    val ingestRepository = appContainer.ingestRepository
                    val contactDao = appContainer.contactDao
                    
                    // 전화번호부 확인 (전화번호부에 있으면 스팸이 아님)
                    val normalizedPhone = com.example.agent_app.util.PhoneNumberUtils.normalize(address)
                    val contact = contactDao.findByPhoneNumber(address, normalizedPhone)
                    if (contact != null) {
                        Log.d(TAG, "✅ 전화번호부에 등록된 번호 - 처리 진행: $address (${contact.name})")
                    } else {
                        Log.d(TAG, "전화번호부에 없는 번호: $address")
                    }
                    
                    // 메모리 최적화: 데이터베이스에서 중복 체크 (메모리 사용 없음)
                    val existingItem = ingestRepository.getById(originalSmsId)
                    if (existingItem != null) {
                        Log.d(TAG, "이미 처리된 SMS, 건너뜀: $originalSmsId")
                        return@launch
                    }
                    
                    // SMS 메시지 처리
                    val result = aiAgent.processSMSForEvent(
                        smsBody = body,
                        smsAddress = address,
                        receivedTimestamp = timestamp,
                        originalSmsId = originalSmsId
                    )
                    
                    Log.d(TAG, "SMS 자동 처리 완료 - Type: ${result.type}, Events: ${result.events.size}")
                    
                    // 분류된 데이터 새로고침은 앱이 포그라운드에 있을 때만 수행
                    // (백그라운드에서는 데이터베이스에만 저장)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "SMS 자동 처리 실패", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "SMS 수신 처리 실패", e)
        }
    }
    
    companion object {
        private const val TAG = "SmsAutoProcessReceiver"
    }
}

