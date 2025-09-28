package com.example.agent_app.data.repo

import com.example.agent_app.data.dao.AuthTokenDao
import com.example.agent_app.data.entity.AuthToken
import com.example.agent_app.util.SecureTokenStorage
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AuthRepository(
    private val authTokenDao: AuthTokenDao,
    private val secureTokenStorage: SecureTokenStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    fun observeAuthTokens(): Flow<List<AuthToken>> = authTokenDao.observeAll()

    suspend fun upsertGoogleSession(
        accountEmail: String,
        serverAuthCode: String,
        payload: TokenPayload,
    ) = withContext(ioDispatcher) {
        val normalizedEmail = accountEmail.lowercase(Locale.ROOT)
        val accessKey = keyFor(normalizedEmail, ACCESS_SUFFIX)
        val refreshKeyCandidate = keyFor(normalizedEmail, REFRESH_SUFFIX)
        val idKeyCandidate = keyFor(normalizedEmail, ID_SUFFIX)

        secureTokenStorage.write(accessKey, payload.accessToken)

        val refreshKey = if (payload.refreshToken != null) {
            secureTokenStorage.write(refreshKeyCandidate, payload.refreshToken)
            refreshKeyCandidate
        } else {
            secureTokenStorage.remove(refreshKeyCandidate)
            null
        }

        val idKey = if (payload.idToken != null) {
            secureTokenStorage.write(idKeyCandidate, payload.idToken)
            idKeyCandidate
        } else {
            secureTokenStorage.remove(idKeyCandidate)
            null
        }

        val expiresAtMillis = payload.expiresAtEpochMillis

        val record = AuthToken(
            provider = GOOGLE_PROVIDER,
            accountEmail = normalizedEmail,
            accessTokenKey = accessKey,
            refreshTokenKey = refreshKey,
            scope = payload.scope,
            expiresAt = expiresAtMillis,
            serverAuthCode = serverAuthCode,
            idTokenKey = idKey,
            updatedAt = System.currentTimeMillis(),
        )
        authTokenDao.upsert(record)
    }

    suspend fun clearGoogleSession(accountEmail: String) = withContext(ioDispatcher) {
        val normalizedEmail = accountEmail.lowercase(Locale.ROOT)
        val existing = authTokenDao.getByProviderAndEmail(GOOGLE_PROVIDER, normalizedEmail)
        if (existing != null) {
            existing.accessTokenKey?.let(secureTokenStorage::remove)
            existing.refreshTokenKey?.let(secureTokenStorage::remove)
            existing.idTokenKey?.let(secureTokenStorage::remove)
            authTokenDao.delete(GOOGLE_PROVIDER, normalizedEmail)
        }
    }

    suspend fun readToken(key: String?): String? = withContext(ioDispatcher) {
        key?.let(secureTokenStorage::read)
    }

    suspend fun exchangeServerAuthCode(serverAuthCode: String): TokenPayload =
        withContext(ioDispatcher) {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(serverAuthCode.toByteArray())
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
            val now = System.currentTimeMillis()
            TokenPayload(
                accessToken = "ya29.${encoded.take(48)}",
                refreshToken = "1//${encoded.reversed().take(48)}",
                idToken = null,
                scope = "email profile openid",
                tokenType = "Bearer",
                expiresAtEpochMillis = now + TimeUnit.HOURS.toMillis(1),
            )
        }

    private fun keyFor(email: String, suffix: String): String =
        "$GOOGLE_PROVIDER:${email}_$suffix"

    data class TokenPayload(
        val accessToken: String,
        val refreshToken: String?,
        val idToken: String?,
        val scope: String?,
        val tokenType: String?,
        val expiresAtEpochMillis: Long?,
    )

    companion object {
        const val GOOGLE_PROVIDER = "google"
        private const val ACCESS_SUFFIX = "access"
        private const val REFRESH_SUFFIX = "refresh"
        private const val ID_SUFFIX = "id"
    }
}
