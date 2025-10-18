package com.example.agent_app.data.repo

/**
 * Gmail 동기화 결과
 */
sealed class GmailSyncResult {
    data class Success(val upsertedCount: Int) : GmailSyncResult()
    data class NetworkError(val message: String) : GmailSyncResult()
    object Unauthorized : GmailSyncResult()
    object MissingToken : GmailSyncResult()
}

