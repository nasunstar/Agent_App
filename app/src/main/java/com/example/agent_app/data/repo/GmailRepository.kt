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
    private val classifiedDataRepository: ClassifiedDataRepository? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun syncRecentMessages(accessToken: String): GmailSyncResult = withContext(dispatcher) {
        android.util.Log.d("GmailRepository", "Gmail 동기화 시작 - Access Token: ${accessToken.take(20)}...")
        
        if (accessToken.isBlank()) {
            android.util.Log.w("GmailRepository", "Access Token이 비어있음")
            return@withContext GmailSyncResult.MissingToken
        }
        try {
            // 토큰에서 개행 문자와 공백 제거
            val cleanToken = accessToken.trim().replace("\n", "").replace("\r", "")
            val authorization = "Bearer $cleanToken"
            android.util.Log.d("GmailRepository", "Gmail API 호출 시작 - Authorization: ${authorization.take(30)}...")
            android.util.Log.d("GmailRepository", "정리된 토큰 길이: ${cleanToken.length}")
            
            val listResponse = api.listMessages(
                authorization = authorization,
                userId = "me",
                maxResults = 20,
            )
            
            android.util.Log.d("GmailRepository", "Gmail 메시지 목록 조회 성공 - 메시지 수: ${listResponse.messages.size}")
            if (listResponse.messages.isEmpty()) {
                android.util.Log.d("GmailRepository", "메시지가 없음 - 동기화 완료")
                return@withContext GmailSyncResult.Success(upsertedCount = 0)
            }
            var upserted = 0
            android.util.Log.d("GmailRepository", "메시지 상세 정보 조회 시작 - 총 ${listResponse.messages.size}개")
            
            listResponse.messages.forEach { reference ->
                android.util.Log.d("GmailRepository", "메시지 조회 중 - ID: ${reference.id}")
                val message = api.getMessage(
                    authorization = authorization,
                    userId = "me",
                    messageId = reference.id,
                    format = "full",
                    metadataHeaders = listOf("Subject", "Date", "From", "To", "Cc", "Bcc"),
                )
                
                // 정규화된 분류 저장 (ClassifiedDataRepository가 있는 경우)
                if (classifiedDataRepository != null) {
                    val subject = message.payload?.headers?.firstOrNull { 
                        it.name.equals("Subject", ignoreCase = true) 
                    }?.value
                    val from = message.payload?.headers?.firstOrNull { 
                        it.name.equals("From", ignoreCase = true) 
                    }?.value
                    val to = message.payload?.headers?.firstOrNull { 
                        it.name.equals("To", ignoreCase = true) 
                    }?.value
                    
                    android.util.Log.d("GmailRepository", "메시지 정보 - 제목: $subject, 발신자: $from")
                    
                    // 이메일 내용 (snippet 사용)
                    val body = message.snippet ?: ""
                    
                    // 발신자 정보를 포함한 전체 내용으로 분류
                    val fullContent = buildString {
                        if (from != null) append("발신자: $from\n")
                        if (to != null) append("수신자: $to\n")
                        if (subject != null) append("제목: $subject\n")
                        append("내용: $body")
                    }
                    
                    // 이메일 수신 시간 추출
                    val dateHeader = message.payload?.headers?.firstOrNull { 
                        it.name.equals("Date", ignoreCase = true) 
                    }?.value
                    val emailTimestamp = parseInternalDate(message.internalDate, dateHeader).toEpochMilli()
                    
                    android.util.Log.d("GmailRepository", "AI 분류 시작 - 메시지 ID: ${message.id}")
                    classifiedDataRepository.processAndStoreEmail(
                        subject = subject,
                        body = fullContent,
                        source = SOURCE_GMAIL,
                        originalId = message.id,
                        timestamp = emailTimestamp
                    )
                    android.util.Log.d("GmailRepository", "AI 분류 완료 - 메시지 ID: ${message.id}")
                } else {
                    // 기존 방식: IngestItem에만 저장
                    android.util.Log.d("GmailRepository", "IngestItem 저장 - 메시지 ID: ${message.id}")
                    val ingestItem = message.toIngestItem()
                    ingestRepository.upsert(ingestItem)
                }
                upserted += 1
            }
            android.util.Log.d("GmailRepository", "Gmail 동기화 완료 - 처리된 메시지 수: $upserted")
            GmailSyncResult.Success(upserted)
        } catch (exception: HttpException) {
            android.util.Log.e("GmailRepository", "Gmail API HTTP 오류", exception)
            when (exception.code()) {
                401 -> {
                    android.util.Log.e("GmailRepository", "인증 실패 (401) - 토큰이 만료되었거나 권한이 없음")
                    android.util.Log.e("GmailRepository", "오류 응답 본문: ${exception.response()?.errorBody()?.string()}")
                    GmailSyncResult.Unauthorized
                }
                403 -> {
                    android.util.Log.e("GmailRepository", "권한 부족 (403) - Gmail API 접근 권한이 없음")
                    android.util.Log.e("GmailRepository", "오류 응답 본문: ${exception.response()?.errorBody()?.string()}")
                    GmailSyncResult.NetworkError("Gmail API 접근 권한이 없습니다. Google Cloud Console에서 Gmail API를 활성화해주세요.")
                }
                else -> {
                    android.util.Log.e("GmailRepository", "HTTP 오류 ${exception.code()}: ${exception.message()}")
                    android.util.Log.e("GmailRepository", "오류 응답 본문: ${exception.response()?.errorBody()?.string()}")
                    GmailSyncResult.NetworkError(exception.message())
                }
            }
        } catch (io: IOException) {
            android.util.Log.e("GmailRepository", "네트워크 IO 오류", io)
            GmailSyncResult.NetworkError(io.message ?: "IO error")
        } catch (throwable: Throwable) {
            android.util.Log.e("GmailRepository", "예상치 못한 오류", throwable)
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
