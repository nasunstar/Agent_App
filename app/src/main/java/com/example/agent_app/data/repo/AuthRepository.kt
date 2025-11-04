package com.example.agent_app.data.repo

import android.content.Context
import com.example.agent_app.data.dao.AuthTokenDao
import com.example.agent_app.data.entity.AuthToken
import com.example.agent_app.util.TokenEncryption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AuthRepository(
    private val dao: AuthTokenDao,
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val encryption = TokenEncryption(context)
    // 기존 호환성을 위한 메서드 (첫 번째 Google 계정 반환)
    fun observeGoogleToken(): Flow<AuthToken?> =
        dao.observeAllByProvider(GOOGLE_PROVIDER).map { tokens -> 
            tokens.firstOrNull()?.let { decryptToken(it) }
        }

    suspend fun getGoogleToken(): AuthToken? = withContext(dispatcher) {
        dao.getAllByProvider(GOOGLE_PROVIDER).firstOrNull()?.let { decryptToken(it) }
    }

    // 특정 이메일의 토큰 조회
    suspend fun getGoogleTokenByEmail(email: String?): AuthToken? = withContext(dispatcher) {
        val emailKey = email?.trim()?.takeIf { it.isNotEmpty() } ?: ""
        dao.getByProviderAndEmail(GOOGLE_PROVIDER, emailKey)?.let { decryptToken(it) }
    }

    // 모든 Google 계정 조회
    fun observeAllGoogleTokens(): Flow<List<AuthToken>> =
        dao.observeAllByProvider(GOOGLE_PROVIDER).map { tokens ->
            tokens.map { decryptToken(it) }
        }

    suspend fun getAllGoogleTokens(): List<AuthToken> = withContext(dispatcher) {
        dao.getAllByProvider(GOOGLE_PROVIDER).map { decryptToken(it) }
    }

    suspend fun upsertGoogleToken(
        accessToken: String,
        refreshToken: String?,
        scope: String?,
        expiresAt: Long?,
        email: String? = null, // 이메일 추가
    ) = withContext(dispatcher) {
        // 토큰 암호화
        val encryptedAccessToken = encryption.encrypt(accessToken) ?: accessToken
        val encryptedRefreshToken = refreshToken?.let { encryption.encrypt(it) ?: it }
        
        val token = AuthToken(
            provider = GOOGLE_PROVIDER,
            email = email?.trim()?.takeIf { it.isNotEmpty() } ?: "", // null이면 빈 문자열
            accessToken = encryptedAccessToken,
            refreshToken = encryptedRefreshToken,
            scope = scope,
            expiresAt = expiresAt,
        )
        dao.upsert(token)
    }
    
    /**
     * 저장된 토큰 복호화 (내부 사용)
     */
    private fun decryptToken(token: AuthToken): AuthToken {
        return token.copy(
            accessToken = encryption.decrypt(token.accessToken) ?: token.accessToken,
            refreshToken = token.refreshToken?.let { encryption.decrypt(it) ?: it }
        )
    }

    suspend fun clearGoogleToken(email: String? = null) = withContext(dispatcher) {
        val emailKey = email?.trim()?.takeIf { it.isNotEmpty() } ?: ""
        if (emailKey.isEmpty() && email == null) {
            // 모든 Google 토큰 삭제
            val tokens = dao.getAllByProvider(GOOGLE_PROVIDER)
            tokens.forEach { dao.delete(it) }
        } else {
            // 특정 이메일의 토큰만 삭제
            val token = dao.getByProviderAndEmail(GOOGLE_PROVIDER, emailKey)
            if (token != null) {
                dao.delete(token)
            }
        }
    }

    private companion object {
        const val GOOGLE_PROVIDER = "google"
    }
}
