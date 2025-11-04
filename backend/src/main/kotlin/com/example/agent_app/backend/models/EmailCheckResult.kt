package com.example.agent_app.backend.models

import kotlinx.serialization.Serializable

/**
 * 계정별 메일 확인 결과
 */
@Serializable
data class EmailCheckResult(
    val email: String,
    val status: String, // "SUCCESS" or "ERROR"
    val errorMessage: String? = null,
    val data: EmailCheckData? = null
)

/**
 * 메일 확인 데이터
 */
@Serializable
data class EmailCheckData(
    val unreadCount: Int,
    val messages: List<EmailMessage>
)

/**
 * 메일 메시지 정보
 */
@Serializable
data class EmailMessage(
    val id: String,
    val threadId: String
)

/**
 * 모든 계정 메일 확인 응답
 */
@Serializable
data class EmailCheckResponse(
    val results: List<EmailCheckResult>,
    val error: String? = null
)

