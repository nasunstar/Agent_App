package com.example.agent_app.backend.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Google OAuth 토큰 응답 DTO
 */
@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    
    @SerialName("id_token")
    val idToken: String? = null,
    
    @SerialName("expires_in")
    val expiresIn: Int,
    
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    
    @SerialName("scope")
    val scope: String? = null,
    
    // 에러 응답 필드
    @SerialName("error")
    val error: String? = null,
    
    @SerialName("error_description")
    val errorDescription: String? = null
)

