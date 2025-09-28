package com.example.agent_app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

class AppDatabaseTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertAndFetchAuthToken() = runBlocking {
        val dao = database.authTokenDao()
        val token = AuthTokenEntity(
            provider = "google",
            accountEmail = "jane.doe@example.com",
            accessToken = "access-123",
            refreshToken = "refresh-456",
            scopes = listOf("gmail.readonly", "profile"),
            expiresAt = Instant.parse("2024-10-01T12:00:00Z"),
            createdAt = Instant.parse("2024-09-01T08:00:00Z"),
            updatedAt = Instant.parse("2024-09-01T08:00:00Z")
        )

        val rowId = dao.upsert(token)
        assertTrue(rowId > 0)

        val stored = dao.find("google", "jane.doe@example.com")
        assertNotNull(stored)
        stored!!
        assertEquals(token.scopes, stored.scopes)
        assertEquals(token.refreshToken, stored.refreshToken)

        val deleted = dao.delete("google", "jane.doe@example.com")
        assertEquals(1, deleted)
        assertNull(dao.find("google", "jane.doe@example.com"))
    }

    @Test
    fun ftsSearchReturnsRelevantIngestItems() = runBlocking {
        val dao = database.ingestItemDao()
        val first = IngestItemEntity(
            source = "gmail",
            externalId = "A",
            subject = "Weekly team sync",
            body = "We discussed launch blockers and action items.",
            labels = listOf("work", "team"),
            receivedAt = Instant.parse("2024-09-10T09:00:00Z"),
            createdAt = Instant.parse("2024-09-10T09:00:00Z"),
            updatedAt = Instant.parse("2024-09-10T09:00:00Z")
        )
        val second = IngestItemEntity(
            source = "gmail",
            externalId = "B",
            subject = "Personal reminder",
            body = "Pick up coffee beans on the way home.",
            labels = listOf("personal"),
            receivedAt = Instant.parse("2024-09-09T18:30:00Z"),
            createdAt = Instant.parse("2024-09-09T18:30:00Z"),
            updatedAt = Instant.parse("2024-09-09T18:30:00Z")
        )

        dao.upsert(first)
        dao.upsert(second)

        val results = dao.search("launch")
        assertEquals(1, results.size)
        assertEquals("Weekly team sync", results.first().subject)
        assertEquals(first.labels, results.first().labels)

        val lookup = dao.findBySourceAndExternalId("gmail", "B")
        assertNotNull(lookup)
        assertEquals(second.body, lookup?.body)

        val recent = dao.getRecent(limit = 1)
        assertEquals(1, recent.size)
        assertEquals(first.externalId, recent.first().externalId)
    }
}
