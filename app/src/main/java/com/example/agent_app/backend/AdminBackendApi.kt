package com.example.agent_app.backend

import kotlinx.serialization.Serializable
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * 백엔드 서버 API 인터페이스
 */
interface AdminBackendApi {
    /**
     * 연결된 관리자 계정 목록 조회
     */
    @GET("/admin/accounts")
    suspend fun getAdminAccounts(): AdminAccountsResponse
    
    /**
     * 관리자 계정 삭제
     */
    @DELETE("/admin/accounts/{email}")
    suspend fun deleteAdminAccount(@Path("email") email: String): DeleteResponse
}

@Serializable
data class AdminAccountsResponse(
    val accounts: List<AdminAccountDto>
)

@Serializable
data class AdminAccountDto(
    val id: Long,
    val email: String,
    val scopes: List<String>,
    val expiresAt: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class DeleteResponse(
    val message: String? = null,
    val email: String? = null,
    val error: String? = null
)

