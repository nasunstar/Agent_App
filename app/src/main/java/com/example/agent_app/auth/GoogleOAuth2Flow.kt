package com.example.agent_app.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * OAuth 2.0 플로우를 통해 Access Token과 Refresh Token을 모두 받는 클래스
 */
class GoogleOAuth2Flow(private val context: Context) {
    
    companion object {
        private const val TAG = "GoogleOAuth2Flow"
        private const val AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val REDIRECT_URI = "com.example.agent_app://oauth2callback"
    }
    
    /**
     * OAuth 2.0 인증 URL 생성
     */
    fun getAuthorizationUrl(
        clientId: String,
        scope: String,
        state: String
    ): String {
        return buildString {
            append(AUTHORIZATION_URL)
            append("?response_type=code")
            append("&client_id=${URLEncoder.encode(clientId, "UTF-8")}")
            append("&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}")
            append("&scope=${URLEncoder.encode(scope, "UTF-8")}")
            append("&access_type=offline")  // refresh token 받기
            append("&prompt=consent")  // 매번 동의 화면 표시 (refresh token 보장)
            append("&state=${URLEncoder.encode(state, "UTF-8")}")
        }
    }
    
    /**
     * Authorization code를 Access Token과 Refresh Token으로 교환
     */
    suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        clientId: String,
        clientSecret: String? = null
    ): TokenExchangeResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(TOKEN_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            
            val postData = buildString {
                append("code=${URLEncoder.encode(authorizationCode, "UTF-8")}")
                append("&client_id=${URLEncoder.encode(clientId, "UTF-8")}")
                if (clientSecret != null) {
                    append("&client_secret=${URLEncoder.encode(clientSecret, "UTF-8")}")
                }
                append("&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}")
                append("&grant_type=authorization_code")
            }
            
            connection.outputStream.use { output ->
                output.write(postData.toByteArray(Charsets.UTF_8))
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "토큰 교환 성공")
                
                // JSON 파싱
                val accessToken = extractJsonValue(response, "access_token")
                val refreshToken = extractJsonValue(response, "refresh_token")
                val expiresIn = extractJsonValue(response, "expires_in")?.toLongOrNull()
                val tokenType = extractJsonValue(response, "token_type")
                val scope = extractJsonValue(response, "scope")
                
                if (accessToken != null) {
                    val expiresAt = expiresIn?.let { System.currentTimeMillis() + (it * 1000) }
                    TokenExchangeResult.Success(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAt = expiresAt,
                        tokenType = tokenType ?: "Bearer",
                        scope = scope
                    )
                } else {
                    TokenExchangeResult.Failure("응답에서 access_token을 찾을 수 없습니다.")
                }
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "토큰 교환 실패: $responseCode - $errorResponse")
                
                val errorDescription = extractJsonValue(errorResponse ?: "", "error_description")
                TokenExchangeResult.Failure(
                    errorDescription ?: "토큰 교환 실패: HTTP $responseCode"
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "토큰 교환 네트워크 오류", e)
            TokenExchangeResult.Failure("네트워크 오류: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "토큰 교환 오류", e)
            TokenExchangeResult.Failure("토큰 교환 실패: ${e.message}")
        }
    }
    
    /**
     * Intent에서 authorization code 추출
     */
    fun extractAuthorizationCode(intent: Intent?): String? {
        val data = intent?.data ?: return null
        return data.getQueryParameter("code")
    }
    
    /**
     * Intent에서 state 추출 (CSRF 보호용)
     */
    fun extractState(intent: Intent?): String? {
        val data = intent?.data ?: return null
        return data.getQueryParameter("state")
    }
    
    /**
     * Intent에서 error 추출
     */
    fun extractError(intent: Intent?): String? {
        val data = intent?.data ?: return null
        return data.getQueryParameter("error")
    }
    
    /**
     * Custom Tab으로 인증 URL 열기
     */
    fun openAuthorizationUrl(context: Context, url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .build()
            
            // ViewModel에서 호출될 수 있으므로 항상 FLAG_ACTIVITY_NEW_TASK 플래그 추가
            val intent = customTabsIntent.intent.apply {
                data = Uri.parse(url)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Activity context든 Application context든 모두 작동하도록 처리
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "인증 URL 열기 실패", e)
            // 대체 방법: 기본 브라우저로 열기
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "브라우저로 열기 실패", e2)
                throw e2
            }
        }
    }
    
    /**
     * JSON 문자열에서 특정 키의 값을 추출합니다.
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

sealed class TokenExchangeResult {
    data class Success(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAt: Long?,
        val tokenType: String,
        val scope: String?
    ) : TokenExchangeResult()
    
    data class Failure(val message: String) : TokenExchangeResult()
}

