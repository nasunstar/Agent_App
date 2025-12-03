package com.example.agent_app

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.agent_app.service.SmsContentObserver

/**
 * 앱 전역 Application 클래스
 * 백그라운드에서도 SMS ContentObserver를 유지하기 위해 사용
 */
class AgentApplication : Application() {
    
    private var smsContentObserver: SmsContentObserver? = null
    
    override fun onCreate() {
        super.onCreate()
        
        // SMS ContentObserver 등록 (백그라운드에서도 작동)
        registerSmsContentObserver()
    }
    
    private fun registerSmsContentObserver() {
        try {
            val handler = Handler(Looper.getMainLooper())
            smsContentObserver = SmsContentObserver(this, handler)
            
            // 여러 SMS URI에 등록하여 감지 확률 증가
            val smsUris = listOf(
                android.provider.Telephony.Sms.CONTENT_URI,
                android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                android.provider.Telephony.Sms.Sent.CONTENT_URI,
                android.provider.Telephony.Sms.Draft.CONTENT_URI,
                android.provider.Telephony.MmsSms.CONTENT_URI,
                android.net.Uri.parse("content://sms"),
                android.net.Uri.parse("content://sms/"),
                android.net.Uri.parse("content://sms/inbox"),
                android.net.Uri.parse("content://sms/sent"),
                android.net.Uri.parse("content://sms/draft"),
                android.net.Uri.parse("content://mms-sms/conversations/")
            )
            
            smsContentObserver?.let { observer ->
                smsUris.forEach { uri ->
                    try {
                        contentResolver.registerContentObserver(uri, true, observer)
                        Log.d("AgentApplication", "✅ SMS ContentObserver 등록 완료: $uri")
                    } catch (e: Exception) {
                        Log.e("AgentApplication", "SMS ContentObserver 등록 실패: $uri", e)
                    }
                }
            }
            
            Log.d("AgentApplication", "✅ SMS ContentObserver 등록 완료 (총 ${smsUris.size}개 URI)")
        } catch (e: Exception) {
            Log.e("AgentApplication", "SMS ContentObserver 등록 실패", e)
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        // SMS ContentObserver 해제
        smsContentObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
                Log.d("AgentApplication", "SMS ContentObserver 해제 완료")
            } catch (e: Exception) {
                Log.e("AgentApplication", "SMS ContentObserver 해제 실패", e)
            }
        }
        smsContentObserver = null
    }
}

