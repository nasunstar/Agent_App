package com.example.agent_app.backend.utils

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Google 인증 유틸리티
 * Refresh Token으로 새 Access Token을 발급받는 기능을 제공합니다.
 */
object GoogleAuthUtils {
    private val logger = LoggerFactory.getLogger(GoogleAuthUtils::class.java)
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    /**
     * 암호화된 Refresh Token으로 새 Access Token을 발급받습니다.
     * 
     * @param encryptedRefreshToken 암호화된 refresh_token
     * @param clientId Google OAuth Client ID
     * @param clientSecret Google OAuth Client Secret
     * @return 새로 발급된 access_token (실패 시 null)
     */
    suspend fun getNewAccessToken(
        encryptedRefreshToken: String,
        clientId: String,
        clientSecret: String
    ): String? {
        try {
            // 1. 암호화된 refresh_token 복호화
            val refreshToken = try {
                CryptoUtils.decrypt(encryptedRefreshToken)
            } catch (e: Exception) {
                logger.error("Refresh token 복호화 실패", e)
                return null
            }
            
            logger.info("Refresh token 복호화 성공, 토큰 갱신 시도 중...")
            
            // 2. Ktor Client로 https://oauth2.googleapis.com/token에 POST 요청
            val formBody = listOf(
                "refresh_token" to refreshToken,
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "grant_type" to "refresh_token"
            ).formUrlEncode()
            
            val response = client.post("https://oauth2.googleapis.com/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(formBody)
            }
            
            // 3. 응답 파싱
            val tokenResponse: RefreshTokenResponse = response.body()
            
            // 4. 에러 처리
            if (tokenResponse.error != null) {
                logger.error(
                    "Access token 갱신 실패: ${tokenResponse.error} - ${tokenResponse.errorDescription}"
                )
                
                // invalid_grant 오류는 refresh_token이 만료되었거나 무효한 경우
                if (tokenResponse.error == "invalid_grant") {
                    logger.warn("Refresh token이 무효하거나 만료되었습니다. 재인증이 필요합니다.")
                }
                
                return null
            }
            
            // 5. 성공하면 새로 발급된 access_token 반환
            logger.info("✅ Access token 갱신 성공 (Expires in: ${tokenResponse.expiresIn} seconds)")
            return tokenResponse.accessToken
            
        } catch (e: Exception) {
            logger.error("Access token 갱신 중 예외 발생", e)
            return null
        }
    }
}

/**
 * Refresh Token 응답 DTO
 */
@Serializable
private data class RefreshTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    
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

