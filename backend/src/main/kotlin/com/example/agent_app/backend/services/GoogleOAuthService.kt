package com.example.agent_app.backend.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Google OAuth2 서비스
 * 
 * 설정 방법:
 * 1. Google Cloud Console에서 OAuth 2.0 클라이언트 ID 생성
 * 2. 승인된 리디렉션 URI에 http://localhost:8080/admin/accounts/connect/google/callback 추가
 * 3. backend.conf 파일에 클라이언트 ID와 Secret 설정
 */
class GoogleOAuthService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    // TODO: 실제 환경에서는 환경 변수나 설정 파일에서 로드해야 합니다.
    private val clientId = System.getenv("GOOGLE_CLIENT_ID") 
        ?: "YOUR_CLIENT_ID_HERE"
    private val clientSecret = System.getenv("GOOGLE_CLIENT_SECRET") 
        ?: "YOUR_CLIENT_SECRET_HERE"
    private val redirectUri = System.getenv("OAUTH_REDIRECT_URI") 
        ?: "http://localhost:8080/admin/accounts/connect/google/callback"
    
    // Gmail API 스코프
    private val scopes = listOf(
        "https://www.googleapis.com/auth/gmail.readonly",
        "https://www.googleapis.com/auth/userinfo.email",
        "https://www.googleapis.com/auth/userinfo.profile"
    ).joinToString(" ")
    
    /**
     * Google OAuth 인증 URL 생성
     */
    fun getAuthorizationUrl(): String {
        val params = listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to scopes,
            "access_type" to "offline", // refresh token 받기 위해 필요
            "prompt" to "consent" // 항상 동의 화면 표시 (refresh token 받기 위해)
        )
        
        val queryString = params.joinToString("&") { (key, value) ->
            "$key=${URLBuilder().apply { encodedPath = value }.buildString()}"
        }
        
        return "https://accounts.google.com/o/oauth2/v2/auth?$queryString"
    }
    
    /**
     * 인증 코드를 액세스 토큰으로 교환
     */
    suspend fun exchangeCodeForTokens(code: String): TokenResponse {
        val response = client.post("https://oauth2.googleapis.com/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                listOf(
                    "code" to code,
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "redirect_uri" to redirectUri,
                    "grant_type" to "authorization_code"
                ).formUrlEncode()
            )
        }
        
        return response.body()
    }
    
    /**
     * 액세스 토큰으로 사용자 정보 가져오기
     */
    suspend fun getUserInfo(accessToken: String): UserInfo {
        val response = client.get("https://www.googleapis.com/oauth2/v2/userinfo") {
            bearerAuth(accessToken)
        }
        
        return response.body()
    }
    
    /**
     * Refresh Token으로 새 액세스 토큰 발급
     */
    suspend fun refreshAccessToken(refreshToken: String): TokenResponse {
        val response = client.post("https://oauth2.googleapis.com/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                listOf(
                    "refresh_token" to refreshToken,
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "grant_type" to "refresh_token"
                ).formUrlEncode()
            )
        }
        
        return response.body()
    }
}

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
    val tokenType: String,
    @SerialName("scope")
    val scope: String? = null
)

@Serializable
data class UserInfo(
    val id: String,
    val email: String,
    @SerialName("verified_email")
    val verifiedEmail: Boolean,
    val name: String? = null,
    @SerialName("given_name")
    val givenName: String? = null,
    @SerialName("family_name")
    val familyName: String? = null,
    val picture: String? = null,
    val locale: String? = null
)

