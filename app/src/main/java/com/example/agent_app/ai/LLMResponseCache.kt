package com.example.agent_app.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * MOA-LLM-Optimization: LLM Response Caching
 * 
 * 입력 해시 기반 캐싱으로 중복 LLM 호출 방지
 * - cacheKey = SHA256(prompt + text)
 * - SharedPreferences 기반 저장
 * - 캐시 유효기간 72시간
 */
class LLMResponseCache(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "llm_response_cache",
        Context.MODE_PRIVATE
    )
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private val TAG = "LLMResponseCache"
    
    // 캐시 유효기간: 72시간 (밀리초)
    private val CACHE_EXPIRY_MS = 72 * 60 * 60 * 1000L
    
    @Serializable
    data class CachedResponse(
        val result: String,  // JSON 문자열 또는 텍스트 결과
        val timestamp: Long,  // 캐시 저장 시간
        val sourceType: String  // 소스 타입 (디버깅용)
    )
    
    /**
     * 캐시 키 생성 (SHA256 해시)
     * 
     * @param prompt 프롬프트 텍스트
     * @param text 입력 텍스트
     * @return SHA256 해시 문자열
     */
    private fun generateCacheKey(prompt: String, text: String): String {
        val input = "$prompt|$text"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 캐시에서 결과 조회
     * 
     * @param prompt 프롬프트 텍스트
     * @param text 입력 텍스트
     * @param sourceType 소스 타입
     * @return 캐시된 결과 (유효한 경우), null (캐시 미스 또는 만료)
     */
    fun getCachedResult(
        prompt: String,
        text: String,
        sourceType: String
    ): String? {
        val cacheKey = generateCacheKey(prompt, text)
        val cachedJson = prefs.getString(cacheKey, null) ?: run {
            Log.d(TAG, "[$sourceType] 캐시 미스: key=$cacheKey")
            return null
        }
        
        try {
            val cached = json.decodeFromString<CachedResponse>(cachedJson)
            
            // 캐시 만료 체크
            val now = System.currentTimeMillis()
            val age = now - cached.timestamp
            
            if (age > CACHE_EXPIRY_MS) {
                Log.d(TAG, "[$sourceType] 캐시 만료: age=${age / 1000 / 60}분")
                prefs.edit().remove(cacheKey).apply()  // 만료된 캐시 삭제
                return null
            }
            
            Log.d(TAG, "[$sourceType] 캐시 히트: age=${age / 1000 / 60}분")
            return cached.result
            
        } catch (e: Exception) {
            Log.w(TAG, "[$sourceType] 캐시 파싱 실패", e)
            prefs.edit().remove(cacheKey).apply()  // 손상된 캐시 삭제
            return null
        }
    }
    
    /**
     * 캐시에 결과 저장
     * 
     * @param prompt 프롬프트 텍스트
     * @param text 입력 텍스트
     * @param result LLM 결과
     * @param sourceType 소스 타입
     */
    fun saveCachedResult(
        prompt: String,
        text: String,
        result: String,
        sourceType: String
    ) {
        val cacheKey = generateCacheKey(prompt, text)
        val cached = CachedResponse(
            result = result,
            timestamp = System.currentTimeMillis(),
            sourceType = sourceType
        )
        
        try {
            val cachedJson = json.encodeToString(CachedResponse.serializer(), cached)
            prefs.edit().putString(cacheKey, cachedJson).apply()
            Log.d(TAG, "[$sourceType] 캐시 저장 완료: key=$cacheKey")
        } catch (e: Exception) {
            Log.w(TAG, "[$sourceType] 캐시 저장 실패", e)
        }
    }
    
    /**
     * sourceId 기반 캐시 키 생성 (더 강력한 캐싱)
     * Gmail Thread, SMS Thread, Push Notification Key 등에 사용
     * 
     * @param sourceId 소스 ID (예: Gmail message ID, SMS ID, Push notification ID)
     * @param sourceType 소스 타입
     * @return 캐시 키
     */
    fun getCachedResultBySourceId(
        sourceId: String,
        sourceType: String
    ): String? {
        val cacheKey = "source_${sourceType}_$sourceId"
        val cachedJson = prefs.getString(cacheKey, null) ?: run {
            Log.d(TAG, "[$sourceType] 소스 ID 캐시 미스: sourceId=$sourceId")
            return null
        }
        
        try {
            val cached = json.decodeFromString<CachedResponse>(cachedJson)
            
            // 캐시 만료 체크
            val now = System.currentTimeMillis()
            val age = now - cached.timestamp
            
            if (age > CACHE_EXPIRY_MS) {
                Log.d(TAG, "[$sourceType] 소스 ID 캐시 만료: age=${age / 1000 / 60}분")
                prefs.edit().remove(cacheKey).apply()
                return null
            }
            
            Log.d(TAG, "[$sourceType] 소스 ID 캐시 히트: sourceId=$sourceId, age=${age / 1000 / 60}분")
            return cached.result
            
        } catch (e: Exception) {
            Log.w(TAG, "[$sourceType] 소스 ID 캐시 파싱 실패", e)
            prefs.edit().remove(cacheKey).apply()
            return null
        }
    }
    
    /**
     * sourceId 기반 캐시 저장
     * 
     * @param sourceId 소스 ID
     * @param result LLM 결과
     * @param sourceType 소스 타입
     */
    fun saveCachedResultBySourceId(
        sourceId: String,
        result: String,
        sourceType: String
    ) {
        val cacheKey = "source_${sourceType}_$sourceId"
        val cached = CachedResponse(
            result = result,
            timestamp = System.currentTimeMillis(),
            sourceType = sourceType
        )
        
        try {
            val cachedJson = json.encodeToString(CachedResponse.serializer(), cached)
            prefs.edit().putString(cacheKey, cachedJson).apply()
            Log.d(TAG, "[$sourceType] 소스 ID 캐시 저장 완료: sourceId=$sourceId")
        } catch (e: Exception) {
            Log.w(TAG, "[$sourceType] 소스 ID 캐시 저장 실패", e)
        }
    }
    
    /**
     * 캐시 전체 삭제 (디버깅/테스트용)
     */
    fun clearCache() {
        prefs.edit().clear().apply()
        Log.d(TAG, "캐시 전체 삭제 완료")
    }
    
    /**
     * 만료된 캐시만 삭제 (정리 작업)
     */
    fun cleanExpiredCache() {
        val now = System.currentTimeMillis()
        val allKeys = prefs.all.keys
        var cleanedCount = 0
        
        allKeys.forEach { key ->
            val cachedJson = prefs.getString(key, null) ?: return@forEach
            try {
                val cached = json.decodeFromString<CachedResponse>(cachedJson)
                val age = now - cached.timestamp
                
                if (age > CACHE_EXPIRY_MS) {
                    prefs.edit().remove(key).apply()
                    cleanedCount++
                }
            } catch (e: Exception) {
                // 손상된 캐시 삭제
                prefs.edit().remove(key).apply()
                cleanedCount++
            }
        }
        
        Log.d(TAG, "만료된 캐시 정리 완료: ${cleanedCount}개 삭제")
    }
}

