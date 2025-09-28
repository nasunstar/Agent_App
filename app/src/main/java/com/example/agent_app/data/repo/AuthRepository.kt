package com.example.agent_app.data.repo

import com.example.agent_app.data.dao.AuthTokenDao
import com.example.agent_app.data.entity.AuthToken
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AuthRepository(
    private val dao: AuthTokenDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeGoogleToken(): Flow<AuthToken?> =
        dao.observeAll().map { tokens -> tokens.firstOrNull { it.provider == GOOGLE_PROVIDER } }

    suspend fun getGoogleToken(): AuthToken? = withContext(dispatcher) {
        dao.getByProvider(GOOGLE_PROVIDER)
    }

    suspend fun upsertGoogleToken(
        accessToken: String,
        refreshToken: String?,
        scope: String?,
        expiresAt: Long?,
    ) = withContext(dispatcher) {
        val token = AuthToken(
            provider = GOOGLE_PROVIDER,
            accessToken = accessToken,
            refreshToken = refreshToken,
            scope = scope,
            expiresAt = expiresAt,
        )
        dao.upsert(token)
    }

    suspend fun clearGoogleToken() = withContext(dispatcher) {
        val current = dao.getByProvider(GOOGLE_PROVIDER)
        if (current != null) {
            dao.delete(current)
        }
    }

    private companion object {
        const val GOOGLE_PROVIDER = "google"
    }
}
