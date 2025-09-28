package com.example.agent_app.data.repo

import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.gmail.GmailApi
import com.example.agent_app.gmail.GmailMessage
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException

class GmailRepository(
    private val api: GmailApi,
    private val ingestRepository: IngestRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun syncRecentMessages(accessToken: String): GmailSyncResult = withContext(dispatcher) {
        if (accessToken.isBlank()) {
            return@withContext GmailSyncResult.MissingToken
        }
        try {
            val authorization = "Bearer $accessToken"
            val listResponse = api.listMessages(
                authorization = authorization,
                userId = "me",
                maxResults = 20,
            )
            if (listResponse.messages.isEmpty()) {
                return@withContext GmailSyncResult.Success(upsertedCount = 0)
            }
            var upserted = 0
            listResponse.messages.forEach { reference ->
                val message = api.getMessage(
                    authorization = authorization,
                    userId = "me",
                    messageId = reference.id,
                    format = "metadata",
                    metadataHeaders = listOf("Subject", "Date"),
                )
                val ingestItem = message.toIngestItem()
                ingestRepository.upsert(ingestItem)
                upserted += 1
            }
            GmailSyncResult.Success(upserted)
        } catch (exception: HttpException) {
            when (exception.code()) {
                401 -> GmailSyncResult.Unauthorized
                else -> GmailSyncResult.NetworkError(exception.message())
            }
        } catch (io: IOException) {
            GmailSyncResult.NetworkError(io.message ?: "IO error")
        } catch (throwable: Throwable) {
            GmailSyncResult.NetworkError(throwable.message ?: "Unknown error")
        }
    }
}

sealed class GmailSyncResult {
    data class Success(val upsertedCount: Int) : GmailSyncResult()
    data class NetworkError(val message: String) : GmailSyncResult()
    object Unauthorized : GmailSyncResult()
    object MissingToken : GmailSyncResult()
}

private val rfc1123Formatter: DateTimeFormatter =
    DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("UTC"))

private fun GmailMessage.toIngestItem(): IngestItem {
    val subject = payload?.headers?.firstOrNull { it.name.equals("Subject", ignoreCase = true) }?.value
    val dateHeader = payload?.headers?.firstOrNull { it.name.equals("Date", ignoreCase = true) }?.value
    val timestamp = parseInternalDate(internalDate, dateHeader)
    val metadata = JSONObject().apply {
        put("threadId", threadId)
        put("labelIds", JSONArray(labelIds ?: emptyList<String>()))
    }
    return IngestItem(
        id = id,
        source = SOURCE_GMAIL,
        type = TYPE_EMAIL,
        title = subject ?: snippet,
        body = snippet,
        timestamp = timestamp.toEpochMilli(),
        dueDate = null,
        confidence = null,
        metaJson = metadata.toString(),
    )
}

private fun parseInternalDate(internalDate: String?, dateHeader: String?): Instant {
    internalDate?.toLongOrNull()?.let { return Instant.ofEpochMilli(it) }
    if (!dateHeader.isNullOrBlank()) {
        try {
            return Instant.from(rfc1123Formatter.parse(dateHeader))
        } catch (_: DateTimeParseException) {
            // Fallback below
        }
    }
    return Instant.now()
}

private const val SOURCE_GMAIL = "gmail"
private const val TYPE_EMAIL = "email"
