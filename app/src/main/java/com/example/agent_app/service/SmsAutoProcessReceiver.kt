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
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) {
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
            
            Log.d(TAG, "SMS 처리 시작 - 발신자: $address, 본문 길이: ${body.length}")
            
            // 백그라운드에서 처리
            scope.launch {
                try {
                    // AppContainer에서 의존성 가져오기
                    val appContainer = AppContainer(context)
                    val aiAgent = appContainer.huenDongMinAiAgent
                    val ingestRepository = appContainer.ingestRepository
                    
                    // SMS 메시지 처리
                    val result = aiAgent.processSMSForEvent(
                        smsBody = body,
                        smsAddress = address,
                        receivedTimestamp = timestamp,
                        originalSmsId = "sms-auto-$smsId"
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

