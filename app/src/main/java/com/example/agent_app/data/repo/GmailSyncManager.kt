package com.example.agent_app.data.repo

import android.content.Context
import android.util.Log
import com.example.agent_app.data.dao.IngestItemDao
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.gmail.GmailApi
import com.example.agent_app.gmail.GmailMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Gmail 실시간 동기화 관리자
 * 중복 방지, 증분 동기화, 실시간 수집 기능 제공
 */
class GmailSyncManager(
    private val context: Context,
    private val gmailApi: GmailApi,
    private val ingestItemDao: IngestItemDao,
    private val classifiedDataRepository: ClassifiedDataRepository?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    
    private val _syncState = MutableStateFlow(GmailSyncState())
    val syncState: StateFlow<GmailSyncState> = _syncState.asStateFlow()
    
    // 마지막 동기화 시간을 저장하는 SharedPreferences 키
    private val prefs = context.getSharedPreferences("gmail_sync", Context.MODE_PRIVATE)
    private val lastSyncTimeKey = "last_sync_time"
    private val lastMessageIdKey = "last_message_id"
    
    /**
     * 실시간 Gmail 동기화 (중복 방지 포함)
     */
    suspend fun syncIncremental(accessToken: String): GmailSyncResult = withContext(dispatcher) {
        Log.d("GmailSyncManager", "증분 동기화 시작")
        
        if (accessToken.isBlank()) {
            return@withContext GmailSyncResult.MissingToken
        }
        
        try {
            _syncState.value = _syncState.value.copy(isSyncing = true, error = null)
            
            val cleanToken = accessToken.trim().replace("\n", "").replace("\r", "")
            val authorization = "Bearer $cleanToken"
            
            // 마지막 동기화 시간과 메시지 ID 가져오기
            val lastSyncTime = prefs.getLong(lastSyncTimeKey, 0L)
            val lastMessageId = prefs.getString(lastMessageIdKey, null)
            
            Log.d("GmailSyncManager", "마지막 동기화: ${java.util.Date(lastSyncTime)}")
            
            // 새로운 메시지만 가져오기 (after 파라미터 사용)
            val query = if (lastSyncTime > 0) {
                "after:${lastSyncTime / 1000}" // Gmail API는 Unix timestamp 사용
            } else {
                null // 첫 동기화
            }
            
            val listResponse = gmailApi.listMessages(
                authorization = authorization,
                userId = "me",
                maxResults = 50, // 더 많은 메시지 처리
                query = query,
            )
            
            Log.d("GmailSyncManager", "새 메시지 수: ${listResponse.messages.size}")
            
            if (listResponse.messages.isEmpty()) {
                _syncState.value = _syncState.value.copy(isSyncing = false)
                return@withContext GmailSyncResult.Success(upsertedCount = 0)
            }
            
            var newMessagesCount = 0
            var duplicateCount = 0
            var currentTime = System.currentTimeMillis()
            
            // 메시지를 시간순으로 정렬 (오래된 것부터)
            val sortedMessages = listResponse.messages.sortedBy { it.id }
            
            for (reference in sortedMessages) {
                // 중복 체크
                val existingItem = ingestItemDao.getById(reference.id)
                if (existingItem != null) {
                    duplicateCount++
                    Log.d("GmailSyncManager", "중복 메시지 건너뛰기: ${reference.id}")
                    continue
                }
                
                // 마지막 메시지 ID 이후의 메시지만 처리
                if (lastMessageId != null && reference.id <= lastMessageId) {
                    duplicateCount++
                    continue
                }
                
                try {
                    val message = gmailApi.getMessage(
                        authorization = authorization,
                        userId = "me",
                        messageId = reference.id,
                        format = "full",
                        metadataHeaders = listOf("Subject", "Date", "From", "To", "Cc", "Bcc"),
                    )
                    
                    // 메시지 처리 및 저장
                    val processed = processAndStoreMessage(message, reference.id)
                    if (processed) {
                        newMessagesCount++
                        // 마지막 처리된 메시지 ID 업데이트
                        prefs.edit().putString(lastMessageIdKey, reference.id).apply()
                    }
                    
                } catch (e: Exception) {
                    Log.e("GmailSyncManager", "메시지 처리 실패: ${reference.id}", e)
                }
            }
            
            // 마지막 동기화 시간 업데이트
            prefs.edit().putLong(lastSyncTimeKey, currentTime).apply()
            
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncTime = currentTime,
                totalMessages = _syncState.value.totalMessages + newMessagesCount
            )
            
            Log.d("GmailSyncManager", "동기화 완료 - 새 메시지: $newMessagesCount, 중복: $duplicateCount")
            GmailSyncResult.Success(upsertedCount = newMessagesCount)
            
        } catch (e: Exception) {
            Log.e("GmailSyncManager", "동기화 실패", e)
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                error = e.message
            )
            GmailSyncResult.NetworkError(e.message ?: "알 수 없는 오류")
        }
    }
    
    /**
     * 전체 동기화 (초기 설정용)
     */
    suspend fun syncFull(accessToken: String): GmailSyncResult = withContext(dispatcher) {
        Log.d("GmailSyncManager", "전체 동기화 시작")
        
        // 기존 동기화 정보 초기화
        prefs.edit().clear().apply()
        
        return@withContext syncIncremental(accessToken)
    }
    
    /**
     * 메시지 처리 및 저장
     */
    private suspend fun processAndStoreMessage(message: GmailMessage, messageId: String): Boolean {
        return try {
            val subject = message.payload?.headers?.firstOrNull { 
                it.name.equals("Subject", ignoreCase = true) 
            }?.value
            
            val from = message.payload?.headers?.firstOrNull { 
                it.name.equals("From", ignoreCase = true) 
            }?.value
            
            val to = message.payload?.headers?.firstOrNull { 
                it.name.equals("To", ignoreCase = true) 
            }?.value
            
            val dateHeader = message.payload?.headers?.firstOrNull { 
                it.name.equals("Date", ignoreCase = true) 
            }?.value
            
            val emailTimestamp = parseGmailDate(message.internalDate, dateHeader).toEpochMilli()
            
            // 전체 이메일 본문 추출 (snippet 대신)
            val body = com.example.agent_app.gmail.GmailBodyExtractor.extractBody(message)
            val fullContent = buildString {
                if (from != null) append("발신자: $from\n")
                if (to != null) append("수신자: $to\n")
                if (subject != null) append("제목: $subject\n")
                append("내용: $body")
            }
            
            // ClassifiedDataRepository를 통한 분류 및 저장
            classifiedDataRepository?.let { repo ->
                repo.processAndStoreEmail(
                    subject = subject,
                    body = fullContent,
                    source = "gmail",
                    originalId = messageId,
                    timestamp = emailTimestamp
                )
            } ?: run {
                // 폴백: IngestItem에만 저장
                val ingestItem = convertToIngestItem(message)
                ingestItemDao.upsert(ingestItem)
            }
            
            true
        } catch (e: Exception) {
            Log.e("GmailSyncManager", "메시지 저장 실패: $messageId", e)
            false
        }
    }
    
    /**
     * 동기화 상태 초기화
     */
    fun resetSyncState() {
        prefs.edit().clear().apply()
        _syncState.value = GmailSyncState()
    }
    
    /**
     * 마지막 동기화 시간 가져오기
     */
    fun getLastSyncTime(): Long = prefs.getLong(lastSyncTimeKey, 0L)
    
    /**
     * 동기화 상태 가져오기
     */
    fun getSyncState(): GmailSyncState = _syncState.value
    
    /**
     * GmailMessage를 IngestItem으로 변환
     */
    private fun convertToIngestItem(message: GmailMessage): IngestItem {
        val subject = message.payload?.headers?.firstOrNull { 
            it.name.equals("Subject", ignoreCase = true) 
        }?.value
        val dateHeader = message.payload?.headers?.firstOrNull { 
            it.name.equals("Date", ignoreCase = true) 
        }?.value
        val timestamp = parseGmailDate(message.internalDate, dateHeader)
        
        // 전체 이메일 본문 추출 (snippet 대신)
        val fullBody = com.example.agent_app.gmail.GmailBodyExtractor.extractBody(message)
        
        return IngestItem(
            id = message.id,
            source = "gmail",
            type = "email",
            title = subject ?: message.snippet,
            body = fullBody,  // 전체 본문 저장
            timestamp = timestamp.toEpochMilli(),
            dueDate = null,
            confidence = null,
            metaJson = null,
        )
    }
    
    /**
     * Gmail API의 internalDate를 파싱하는 헬퍼 함수
     */
    private fun parseGmailDate(internalDate: String?, dateHeader: String?): java.time.Instant {
        return when {
            internalDate != null -> {
                internalDate.toLongOrNull()?.let { 
                    java.time.Instant.ofEpochMilli(it) 
                } ?: java.time.Instant.now()
            }
            dateHeader != null -> {
                try {
                    java.time.ZonedDateTime.parse(dateHeader, 
                        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                    ).toInstant()
                } catch (e: Exception) {
                    java.time.Instant.now()
                }
            }
            else -> java.time.Instant.now()
        }
    }
    
    companion object {
        private const val TAG = "GmailSyncManager"
    }
}

/**
 * Gmail 동기화 상태
 */
data class GmailSyncState(
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0L,
    val totalMessages: Int = 0,
    val error: String? = null,
)

