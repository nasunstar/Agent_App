package com.example.agent_app.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Google Sign-In 헬퍼 클래스
 * Google Sign-In을 통해 OAuth 토큰을 자동으로 받아옵니다.
 */
class GoogleSignInHelper(private val context: Context) {
    
    companion object {
        private const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        const val RC_SIGN_IN = 9001
    }
    
    /**
     * Google Sign-In Client 생성 (기본 방식 - Refresh Token 없음)
     */
    private fun getSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GMAIL_SCOPE))
            .build()
        
        return GoogleSignIn.getClient(context, gso)
    }
    
    /**
     * Google Sign-In Client 생성 (서버 클라이언트 ID 포함 - ID 토큰 요청용)
     */
    private fun getSignInClientWithServerId(serverClientId: String): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GMAIL_SCOPE))
            .requestIdToken(serverClientId)
            .build()
        
        return GoogleSignIn.getClient(context, gso)
    }
    
    /**
     * Sign-In Intent 반환 (계정 선택 화면 강제 표시)
     * 
     * 참고: Google Sign-In SDK는 refresh token을 직접 제공하지 않습니다.
     * Refresh token이 필요하면 GoogleOAuth2Flow를 사용하세요.
     */
    suspend fun getSignInIntentWithAccountSelection(): Intent {
        // 같은 클라이언트 인스턴스를 재사용
        val client = getSignInClient()
        // 기존 로그인 상태를 먼저 로그아웃하여 계정 선택 화면이 나타나도록 함
        try {
            client.signOut().await()
        } catch (e: Exception) {
            // 로그아웃 실패 무시 (이미 로그아웃 상태일 수 있음)
        }
        return client.signInIntent
    }
    
    /**
     * OAuth 2.0 플로우를 사용한 인증 (Refresh Token 포함)
     * 
     * 이 메서드는 GoogleOAuth2Flow를 사용하여 refresh token까지 받을 수 있습니다.
     * Custom Tab을 열어서 사용자가 인증하고, redirect URI로 돌아옵니다.
     */
    fun getOAuth2AuthorizationUrl(
        clientId: String,
        scope: String = GMAIL_SCOPE,
        state: String = java.util.UUID.randomUUID().toString()
    ): String {
        val oauthFlow = GoogleOAuth2Flow(context)
        return oauthFlow.getAuthorizationUrl(clientId, scope, state)
    }
    
    /**
     * Sign-In Intent 반환 (기존 방식 - 자동으로 마지막 로그인 계정 사용)
     */
    fun getSignInIntent(): Intent {
        return getSignInClient().signInIntent
    }
    
    /**
     * Sign-In 결과에서 계정 정보 가져오기 (동기)
     * 주의: 이 메서드는 suspend 함수가 아니므로 즉시 사용 가능한 결과만 반환
     */
    fun getSignInResultFromIntent(data: Intent?): GoogleSignInAccount? {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                task.result
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Sign-In 결과에서 계정 정보 가져오기 (비동기)
     */
    suspend fun getSignInResultFromIntentAsync(data: Intent?): GoogleSignInAccount? {
        if (data == null) {
            android.util.Log.e("GoogleSignInHelper", "Google Sign-In 실패: Intent가 null입니다")
            return null
        }
        
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            // await()는 task가 완료될 때까지 기다리고, 실패하면 자동으로 예외를 던집니다
            val account = task.await()
            android.util.Log.d("GoogleSignInHelper", "Google Sign-In 성공: ${account.email ?: "이메일 없음"}")
            account
        } catch (e: com.google.android.gms.common.api.ApiException) {
            android.util.Log.e("GoogleSignInHelper", "Google Sign-In ApiException: ${e.statusCode} - ${e.status?.statusMessage}", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("GoogleSignInHelper", "Google Sign-In 실패: ${e.javaClass.simpleName} - ${e.message}", e)
            null
        }
    }
    
    /**
     * 현재 로그인된 계정 가져오기
     */
    suspend fun getLastSignedInAccount(): GoogleSignInAccount? {
        return try {
            GoogleSignIn.getLastSignedInAccount(context)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 로그아웃
     */
    suspend fun signOut() {
        try {
            getSignInClient().signOut().await()
        } catch (e: Exception) {
            // 로그아웃 실패 무시
        }
    }
}

