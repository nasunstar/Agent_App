package com.example.agent_app.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Google OAuth 2.0 Refresh Token을 사용하여 Access Token을 갱신하는 클래스
 */
class GoogleTokenRefresher {
    
    companion object {
        private const val TAG = "GoogleTokenRefresher"
        private const val TOKEN_REFRESH_URL = "https://oauth2.googleapis.com/token"
    }
    
    /**
     * Refresh Token을 사용하여 새로운 Access Token을 가져옵니다.
     * 
     * @param refreshToken Refresh Token
     * @param clientId Google OAuth Client ID (Web Client ID)
     * @return TokenRefreshResult
     */
    suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String
    ): TokenRefreshResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(TOKEN_REFRESH_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            
            val postData = buildString {
                append("client_id=$clientId")
                append("&refresh_token=$refreshToken")
                append("&grant_type=refresh_token")
            }
            
            connection.outputStream.use { output ->
                output.write(postData.toByteArray(Charsets.UTF_8))
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "토큰 갱신 성공")
                
                // JSON 파싱 (간단한 파싱)
                val accessToken = extractJsonValue(response, "access_token")
                val expiresIn = extractJsonValue(response, "expires_in")?.toLongOrNull()
                
                if (accessToken != null) {
                    // expires_in은 초 단위이므로 밀리초로 변환
                    val expiresAt = expiresIn?.let { System.currentTimeMillis() + (it * 1000) }
                    TokenRefreshResult.Success(
                        accessToken = accessToken,
                        expiresAt = expiresAt
                    )
                } else {
                    TokenRefreshResult.Failure("응답에서 access_token을 찾을 수 없습니다.")
                }
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "토큰 갱신 실패: $responseCode - $errorResponse")
                TokenRefreshResult.Failure("토큰 갱신 실패: HTTP $responseCode")
            }
        } catch (e: IOException) {
            Log.e(TAG, "토큰 갱신 네트워크 오류", e)
            TokenRefreshResult.Failure("네트워크 오류: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "토큰 갱신 오류", e)
            TokenRefreshResult.Failure("토큰 갱신 실패: ${e.message}")
        }
    }
    
    /**
     * JSON 문자열에서 특정 키의 값을 추출합니다.
     * 숫자와 문자열 모두 지원
     */
    private fun extractJsonValue(json: String, key: String): String? {
        // 문자열 값 추출: "key": "value"
        val stringPattern = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val stringMatch = stringPattern.find(json)
        if (stringMatch != null) {
            return stringMatch.groupValues[1]
        }
        
        // 숫자 값 추출: "key": 123
        val numberPattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
        val numberMatch = numberPattern.find(json)
        if (numberMatch != null) {
            return numberMatch.groupValues[1]
        }
        
        return null
    }
}

sealed class TokenRefreshResult {
    data class Success(
        val accessToken: String,
        val expiresAt: Long?
    ) : TokenRefreshResult()
    
    data class Failure(val message: String) : TokenRefreshResult()
}

