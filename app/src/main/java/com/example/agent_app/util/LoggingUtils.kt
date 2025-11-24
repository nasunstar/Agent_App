package com.example.agent_app.util

import timber.log.Timber

/**
 * MOA-Logging: 로깅 유틸리티 클래스
 * 
 * 민감 정보 필터링 및 구조화된 로깅 제공
 */
object LoggingUtils {
    
    /**
     * 민감 정보를 마스킹하는 함수
     * API 키, 토큰, 비밀번호 등을 자동으로 감지하여 마스킹
     */
    fun maskSensitiveInfo(text: String): String {
        if (text.isBlank()) return text
        
        var masked = text
        
        // API 키 패턴 마스킹 (예: "api_key": "sk-...", "OPENAI_API_KEY": "...")
        masked = masked.replace(
            Regex("""(api[_-]?key|token|password|secret|auth[_-]?token)\s*[:=]\s*["']?([^"'\s]{10,})["']?""", 
                RegexOption.IGNORE_CASE),
            "$1: ***MASKED***"
        )
        
        // OpenAI API 키 패턴 (sk-로 시작)
        masked = masked.replace(
            Regex("""sk-[a-zA-Z0-9]{20,}"""),
            "sk-***MASKED***"
        )
        
        // 일반적인 토큰 패턴 (긴 문자열)
        masked = masked.replace(
            Regex("""["']([a-zA-Z0-9_-]{32,})["']"""),
            "\"***MASKED***\""
        )
        
        return masked
    }
    
    /**
     * 안전한 디버그 로깅 (민감 정보 자동 마스킹)
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = maskSensitiveInfo(message)
        if (throwable != null) {
            Timber.tag(tag).d(throwable, safeMessage)
        } else {
            Timber.tag(tag).d(safeMessage)
        }
    }
    
    /**
     * 안전한 정보 로깅
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = maskSensitiveInfo(message)
        if (throwable != null) {
            Timber.tag(tag).i(throwable, safeMessage)
        } else {
            Timber.tag(tag).i(safeMessage)
        }
    }
    
    /**
     * 안전한 경고 로깅
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = maskSensitiveInfo(message)
        if (throwable != null) {
            Timber.tag(tag).w(throwable, safeMessage)
        } else {
            Timber.tag(tag).w(safeMessage)
        }
    }
    
    /**
     * 안전한 에러 로깅 (Crashlytics로도 전송)
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = maskSensitiveInfo(message)
        if (throwable != null) {
            Timber.tag(tag).e(throwable, safeMessage)
        } else {
            Timber.tag(tag).e(safeMessage)
        }
    }
}

