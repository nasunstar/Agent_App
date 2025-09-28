package com.example.agent_app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.agent_app.data.db.AppDatabase
import com.example.agent_app.gmail.GmailApi
import com.example.agent_app.gmail.GmailHeader
import com.example.agent_app.gmail.GmailMessage
import com.example.agent_app.gmail.GmailMessageListResponse
import com.example.agent_app.gmail.GmailMessageReference
import com.example.agent_app.gmail.GmailPayload
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class GmailRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: GmailRepository
    private lateinit var fakeApi: FakeGmailApi

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeApi = FakeGmailApi()
        repository = GmailRepository(fakeApi, IngestRepository(database.ingestItemDao()))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun syncRecentMessages_insertsMessagesIntoDatabase() = runTest {
        val now = System.currentTimeMillis()
        val messages = listOf(
            gmailMessage(
                id = "m1",
                subject = "Welcome",
                snippet = "환영합니다",
                timestamp = now,
            ),
            gmailMessage(
                id = "m2",
                subject = "Invoice",
                snippet = "청구서를 확인하세요",
                timestamp = now - 1_000L,
            ),
        )
        fakeApi.messages = messages

        val result = repository.syncRecentMessages(accessToken = "token")

        assertTrue(result is GmailSyncResult.Success)
        val dao = database.ingestItemDao()
        val stored = dao.getById("m1")
        assertNotNull(stored)
        assertEquals("Welcome", stored?.title)
        assertEquals("gmail", stored?.source)
    }

    @Test
    fun syncRecentMessages_returnsUnauthorizedOn401() = runTest {
        fakeApi.throwUnauthorized = true

        val result = repository.syncRecentMessages(accessToken = "token")

        assertTrue(result is GmailSyncResult.Unauthorized)
    }

    private class FakeGmailApi : GmailApi {
        var messages: List<GmailMessage> = emptyList()
        var throwUnauthorized: Boolean = false

        override suspend fun listMessages(
            authorization: String,
            userId: String,
            maxResults: Int,
            query: String?,
            pageToken: String?,
            includeSpamTrash: Boolean,
        ): GmailMessageListResponse {
            if (throwUnauthorized) throw unauthorizedException()
            return GmailMessageListResponse(
                messages = messages.map { GmailMessageReference(id = it.id, threadId = it.threadId) },
            )
        }

        override suspend fun getMessage(
            authorization: String,
            userId: String,
            messageId: String,
            format: String,
            metadataHeaders: List<String>,
        ): GmailMessage {
            if (throwUnauthorized) throw unauthorizedException()
            return messages.first { it.id == messageId }
        }

        private fun unauthorizedException(): HttpException {
            val response = Response.error<String>(
                401,
                "{}".toResponseBody("application/json".toMediaType()),
            )
            return HttpException(response)
        }
    }

    private fun gmailMessage(
        id: String,
        subject: String,
        snippet: String,
        timestamp: Long,
    ): GmailMessage {
        val formatter = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("UTC"))
        return GmailMessage(
            id = id,
            threadId = "thread_$id",
            snippet = snippet,
            labelIds = listOf("INBOX"),
            internalDate = timestamp.toString(),
            payload = GmailPayload(
                headers = listOf(
                    GmailHeader(name = "Subject", value = subject),
                    GmailHeader(name = "Date", value = formatter.format(Instant.ofEpochMilli(timestamp))),
                ),
            ),
        )
    }
}
