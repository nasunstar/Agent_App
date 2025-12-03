package com.example.agent_app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 자동 처리 설정 관리 유틸리티
 * 기간 선택 후 활성화된 상태에서만 새 메시지/메일을 자동 처리
 */
object AutoProcessSettings {
    private const val PREFS_NAME = "auto_process_settings"
    private const val KEY_SMS_ENABLED = "sms_auto_process_enabled"
    private const val KEY_SMS_START_TIMESTAMP = "sms_auto_process_start_timestamp"
    private const val KEY_SMS_END_TIMESTAMP = "sms_auto_process_end_timestamp"
    private const val KEY_GMAIL_ENABLED = "gmail_auto_process_enabled"
    private const val KEY_GMAIL_START_TIMESTAMP = "gmail_auto_process_start_timestamp"
    private const val KEY_GMAIL_END_TIMESTAMP = "gmail_auto_process_end_timestamp"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * SMS 자동 처리 활성화 및 기간 설정
     * startTimestamp와 endTimestamp가 모두 0이면 기간 제한 없이 항상 활성화
     */
    fun enableSmsAutoProcess(context: Context, startTimestamp: Long, endTimestamp: Long) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_SMS_ENABLED, true)
            if (startTimestamp > 0L && endTimestamp > 0L) {
                putLong(KEY_SMS_START_TIMESTAMP, startTimestamp)
                putLong(KEY_SMS_END_TIMESTAMP, endTimestamp)
                android.util.Log.d("AutoProcessSettings", "SMS 자동 처리 활성화 - 기간: $startTimestamp ~ $endTimestamp")
            } else {
                // 기간 제한 없이 항상 활성화 (실시간 동기화)
                putLong(KEY_SMS_START_TIMESTAMP, 0L)
                putLong(KEY_SMS_END_TIMESTAMP, 0L)
                android.util.Log.d("AutoProcessSettings", "SMS 자동 처리 활성화 - 기간 제한 없음 (실시간 동기화)")
            }
            apply()
        }
    }
    
    /**
     * SMS 자동 처리 활성화 (기간 제한 없이 항상 활성화)
     */
    fun enableSmsAutoProcessAlways(context: Context) {
        enableSmsAutoProcess(context, 0L, 0L)
    }
    
    /**
     * SMS 자동 처리 비활성화
     */
    fun disableSmsAutoProcess(context: Context) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_SMS_ENABLED, false)
            apply()
        }
        android.util.Log.d("AutoProcessSettings", "SMS 자동 처리 비활성화")
    }
    
    /**
     * SMS 자동 처리 활성화 여부 확인
     */
    fun isSmsAutoProcessEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SMS_ENABLED, false)
    }
    
    /**
     * SMS 자동 처리 기간 가져오기
     */
    fun getSmsAutoProcessPeriod(context: Context): Pair<Long, Long>? {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_SMS_ENABLED, false)) {
            return null
        }
        val start = prefs.getLong(KEY_SMS_START_TIMESTAMP, 0L)
        val end = prefs.getLong(KEY_SMS_END_TIMESTAMP, 0L)
        return if (start > 0L && end > 0L) Pair(start, end) else null
    }
    
    /**
     * 특정 타임스탬프가 SMS 자동 처리 기간에 포함되는지 확인
     * 
     * 규칙:
     * 1. 기간이 설정되지 않았으면 항상 처리 (실시간 동기화)
     * 2. 실시간 SMS (최근 1시간 이내)는 항상 처리 (미래 일정을 위해)
     * 3. 과거 SMS도 항상 처리 (과거 스캔은 미래 일정을 찾기 위한 것이므로)
     * 
     * 주의: 기간 제한은 "최신 동기화 활성화" 기능에서만 사용되며,
     * 수동 스캔("지난 일정 가져오기")은 항상 모든 SMS를 처리합니다.
     */
    fun isWithinSmsAutoProcessPeriod(context: Context, timestamp: Long): Boolean {
        val period = getSmsAutoProcessPeriod(context)
        // 기간이 설정되지 않았으면 항상 처리 (실시간 동기화)
        if (period == null) {
            return true
        }
        
        // 실시간 SMS는 항상 처리 (미래 일정을 위해)
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (60 * 60 * 1000) // 1시간 전
        if (timestamp >= oneHourAgo) {
            android.util.Log.d("AutoProcessSettings", "✅ 실시간 SMS 감지 (최근 1시간 이내) - 항상 처리: timestamp=$timestamp, now=$now")
            return true
        }
        
        // 과거 SMS도 항상 처리 (과거 스캔은 미래 일정을 찾기 위한 것이므로)
        // 기간 제한은 "최신 동기화 활성화" 기능에서만 의미가 있으며,
        // 수동 스캔이나 ContentObserver를 통한 실시간 처리에서는 모든 SMS를 처리해야 함
        android.util.Log.d("AutoProcessSettings", "✅ 과거 SMS도 처리 (미래 일정을 찾기 위해): timestamp=$timestamp")
        return true
    }
    
    /**
     * Gmail 자동 처리 활성화 및 기간 설정
     */
    fun enableGmailAutoProcess(context: Context, startTimestamp: Long, endTimestamp: Long) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_GMAIL_ENABLED, true)
            putLong(KEY_GMAIL_START_TIMESTAMP, startTimestamp)
            putLong(KEY_GMAIL_END_TIMESTAMP, endTimestamp)
            apply()
        }
        android.util.Log.d("AutoProcessSettings", "Gmail 자동 처리 활성화 - 기간: $startTimestamp ~ $endTimestamp")
    }
    
    /**
     * Gmail 자동 처리 비활성화
     */
    fun disableGmailAutoProcess(context: Context) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_GMAIL_ENABLED, false)
            apply()
        }
        android.util.Log.d("AutoProcessSettings", "Gmail 자동 처리 비활성화")
    }
    
    /**
     * Gmail 자동 처리 활성화 여부 확인
     */
    fun isGmailAutoProcessEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_GMAIL_ENABLED, false)
    }
    
    /**
     * Gmail 자동 처리 기간 가져오기
     */
    fun getGmailAutoProcessPeriod(context: Context): Pair<Long, Long>? {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_GMAIL_ENABLED, false)) {
            return null
        }
        val start = prefs.getLong(KEY_GMAIL_START_TIMESTAMP, 0L)
        val end = prefs.getLong(KEY_GMAIL_END_TIMESTAMP, 0L)
        return if (start > 0L && end > 0L) Pair(start, end) else null
    }
    
    /**
     * 특정 타임스탬프가 Gmail 자동 처리 기간에 포함되는지 확인
     */
    fun isWithinGmailAutoProcessPeriod(context: Context, timestamp: Long): Boolean {
        val period = getGmailAutoProcessPeriod(context) ?: return false
        return timestamp >= period.first && timestamp <= period.second
    }
}

