package com.example.agent_app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 백엔드 서버 설정 관리 유틸리티
 * 
 * 설정 우선순위:
 * 1. SharedPreferences (사용자가 앱에서 설정)
 * 2. BuildConfig (빌드 시 설정)
 * 3. 기본값 (에뮬레이터용)
 */
object BackendConfig {
    private const val PREFS_NAME = "backend_config"
    private const val KEY_BACKEND_URL = "backend_url"
    
    // 기본값: 에뮬레이터용 (10.0.2.2 = 호스트의 localhost)
    private const val DEFAULT_BACKEND_URL_EMULATOR = "http://10.0.2.2:8080"
    
    // 실제 기기용 기본값 (필요시 변경)
    private const val DEFAULT_BACKEND_URL_DEVICE = "http://192.168.219.104:8080"
    
    /**
     * 백엔드 서버 URL 가져오기
     * 
     * @param context Android 컨텍스트
     * @param useEmulator true면 에뮬레이터용 URL, false면 실제 기기용 URL
     */
    fun getBackendUrl(context: Context, useEmulator: Boolean = false): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_BACKEND_URL, null)
        
        return savedUrl ?: if (useEmulator) {
            DEFAULT_BACKEND_URL_EMULATOR
        } else {
            DEFAULT_BACKEND_URL_DEVICE
        }
    }
    
    /**
     * 백엔드 서버 URL 저장
     */
    fun setBackendUrl(context: Context, url: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BACKEND_URL, url).apply()
    }
    
    /**
     * 저장된 설정 초기화
     */
    fun clearConfig(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
    
    /**
     * 에뮬레이터인지 자동 감지 (시뮬레이터)
     */
    fun isEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || "google_sdk" == android.os.Build.PRODUCT
    }
}

