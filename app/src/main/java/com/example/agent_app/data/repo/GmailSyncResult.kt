package com.example.agent_app.data.repo

/**
 * Gmail 동기화 결과
 */
sealed class GmailSyncResult {
    data class Success(
        val upsertedCount: Int,
        val eventCount: Int = 0, // 일정 추출 개수
        val startTimestamp: Long = 0L, // 스캔 시작 시간
        val endTimestamp: Long = 0L, // 스캔 끝 시간
    ) : GmailSyncResult()
    data class NetworkError(val message: String) : GmailSyncResult()
    object Unauthorized : GmailSyncResult()
    object MissingToken : GmailSyncResult()
}

