package com.example.agent_app.data.repo

import com.example.agent_app.data.dao.AuthTokenDao
import com.example.agent_app.data.entity.AuthToken
import com.example.agent_app.util.SecureTokenStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private lateinit var authTokenDao: FakeAuthTokenDao
    private lateinit var secureTokenStorage: FakeTokenStorage

    @Before
    fun setup() {
        authTokenDao = FakeAuthTokenDao()
        secureTokenStorage = FakeTokenStorage()
    }

    @Test
    fun upsertGoogleSessionStoresMetadataAndSecrets() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = AuthRepository(authTokenDao, secureTokenStorage, dispatcher)
        val expiresAt = 123_000L
        val payload = AuthRepository.TokenPayload(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            idToken = null,
            scope = "email profile",
            tokenType = "Bearer",
            expiresAtEpochMillis = expiresAt,
        )

        repository.upsertGoogleSession("Person@Example.com", "server-code", payload)

        val stored = authTokenDao.getByProviderAndEmail(AuthRepository.GOOGLE_PROVIDER, "person@example.com")
        assertNotNull(stored)
        stored!!
        assertEquals("person@example.com", stored.accountEmail)
        assertEquals("google:person@example.com_access", stored.accessTokenKey)
        assertEquals("google:person@example.com_refresh", stored.refreshTokenKey)
        assertEquals(expiresAt, stored.expiresAt)
        assertEquals("server-code", stored.serverAuthCode)

        val accessKey = requireNotNull(stored.accessTokenKey)
        val refreshKey = requireNotNull(stored.refreshTokenKey)
        assertEquals("access-token", secureTokenStorage.read(accessKey))
        assertEquals("refresh-token", secureTokenStorage.read(refreshKey))

        val observed = authTokenDao.observeAll().first()
        assertEquals(1, observed.size)
    }

    private class FakeAuthTokenDao : AuthTokenDao {
        private val storage = MutableStateFlow<List<AuthToken>>(emptyList())

        override suspend fun upsert(token: AuthToken) {
            storage.update { current ->
                val filtered = current.filterNot { existing ->
                    existing.provider == token.provider
                }
                filtered + token
            }
        }

        override suspend fun update(token: AuthToken) {
            upsert(token)
        }

        override suspend fun getByProvider(provider: String): AuthToken? =
            storage.value.firstOrNull { it.provider == provider }

        override suspend fun getByProviderAndEmail(provider: String, accountEmail: String): AuthToken? =
            storage.value.firstOrNull {
                it.provider == provider && it.accountEmail == accountEmail
            }

        override suspend fun delete(provider: String, accountEmail: String) {
            storage.update { current ->
                current.filterNot {
                    it.provider == provider && it.accountEmail == accountEmail
                }
            }
        }

        override fun observeAll(): Flow<List<AuthToken>> = storage
    }

    private class FakeTokenStorage : SecureTokenStorage {
        private val map = mutableMapOf<String, String>()

        override fun write(key: String, value: String?) {
            if (value == null) {
                map.remove(key)
            } else {
                map[key] = value
            }
        }

        override fun read(key: String): String? = map[key]

        override fun remove(key: String) {
            map.remove(key)
        }
    }
}
