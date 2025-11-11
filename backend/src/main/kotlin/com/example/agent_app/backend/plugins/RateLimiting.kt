package com.example.agent_app.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 간단한 Rate Limiting 구현
 * 프로덕션 환경에서는 Redis 등을 사용하는 것이 좋습니다.
 */
data class RateLimitInfo(
    val count: Int,
    val resetTime: Long
)

object RateLimiter {
    private val requests = ConcurrentHashMap<String, RateLimitInfo>()
    private val mutex = Mutex()
    
    // IP별 요청 제한: 1분에 60회
    private const val MAX_REQUESTS = 60
    private const val WINDOW_MS = 60_000L // 1분
    
    /**
     * 요청이 허용되는지 확인
     * @return true면 허용, false면 제한
     */
    suspend fun isAllowed(clientId: String): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        val info = requests[clientId]
        
        if (info == null || now > info.resetTime) {
            // 새로운 윈도우 시작
            requests[clientId] = RateLimitInfo(1, now + WINDOW_MS)
            return true
        }
        
        if (info.count >= MAX_REQUESTS) {
            // 제한 초과
            return false
        }
        
        // 요청 수 증가
        requests[clientId] = info.copy(count = info.count + 1)
        return true
    }
    
    /**
     * 남은 요청 수와 리셋 시간 반환
     */
    suspend fun getRemaining(clientId: String): Pair<Int, Long> = mutex.withLock {
        val now = System.currentTimeMillis()
        val info = requests[clientId]
        
        if (info == null || now > info.resetTime) {
            return MAX_REQUESTS to (now + WINDOW_MS)
        }
        
        return (MAX_REQUESTS - info.count) to info.resetTime
    }
    
    /**
     * 오래된 엔트리 정리 (메모리 누수 방지)
     */
    suspend fun cleanup() = mutex.withLock {
        val now = System.currentTimeMillis()
        requests.entries.removeAll { now > it.value.resetTime }
    }
}

/**
 * Rate Limiting 인터셉터
 */
suspend fun ApplicationCall.rateLimit(): Boolean {
    val clientIp = request.header("X-Forwarded-For")
        ?.split(",")
        ?.firstOrNull()
        ?.trim()
        ?: request.local.remoteHost
    return RateLimiter.isAllowed(clientIp)
}

