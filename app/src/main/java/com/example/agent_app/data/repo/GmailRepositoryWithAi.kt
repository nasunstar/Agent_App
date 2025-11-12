package com.example.agent_app.data.repo

import com.example.agent_app.ai.HuenDongMinAiAgent
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.gmail.GmailApi
import com.example.agent_app.gmail.GmailBodyExtractor
import com.example.agent_app.gmail.GmailMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * AI Agent를 사용한 Gmail Repository
 * 
 * TimeResolver 등 기존 시간 파싱 로직을 제거하고,
 * HuenDongMinAiAgent가 모든 처리를 담당하도록 구성
 */
class GmailRepositoryWithAi(
    private val api: GmailApi,
    private val huenDongMinAiAgent: HuenDongMinAiAgent,
    private val ingestRepository: com.example.agent_app.data.repo.IngestRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    
    suspend fun syncRecentMessages(
        accessToken: String,
        sinceTimestamp: Long = 0L,
        onProgress: ((Float, String) -> Unit)? = null, // 진행률 콜백 (progress, message)
    ): GmailSyncResult = withContext(dispatcher) {
        android.util.Log.d("GmailRepositoryWithAi", "Gmail 동기화 시작 (sinceTimestamp: $sinceTimestamp)")
        
        if (accessToken.isBlank()) {
            android.util.Log.w("GmailRepositoryWithAi", "Access Token이 비어있음")
            return@withContext GmailSyncResult.MissingToken
        }
        
        try {
            val cleanToken = accessToken.trim().replace("\n", "").replace("\r", "")
            val authorization = "Bearer $cleanToken"
            
            android.util.Log.d("GmailRepositoryWithAi", "Gmail API 호출 시작")
            
            // 시간 범위 필터링을 위한 쿼리 생성
            // Gmail API의 after: 쿼리는 날짜 형식(YYYY/MM/DD) 또는 Unix timestamp(초)를 받습니다
            val query = if (sinceTimestamp > 0L) {
                // Unix timestamp를 날짜 형식으로 변환
                val date = java.time.Instant.ofEpochMilli(sinceTimestamp)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                    .toLocalDate()
                "after:${date.year}/${date.monthValue}/${date.dayOfMonth}"
            } else {
                null
            }
            
            val listResponse = api.listMessages(
                authorization = authorization,
                userId = "me",
                maxResults = 50, // 시간 범위 지정 시 더 많은 메시지 조회 가능
                query = query,
            )
            
            android.util.Log.d("GmailRepositoryWithAi", "Gmail 메시지 목록 조회 성공 - ${listResponse.messages.size}개")
            
            if (listResponse.messages.isEmpty()) {
                android.util.Log.d("GmailRepositoryWithAi", "메시지가 없음")
                return@withContext GmailSyncResult.Success(upsertedCount = 0)
            }
            
            var processed = 0
            var eventCount = 0
            val startTimestamp = System.currentTimeMillis()
            val totalMessages = listResponse.messages.size
            
            // 초기 진행률 업데이트
            onProgress?.invoke(0.1f, "메시지 목록 조회 완료 (${totalMessages}개)")
            
            listResponse.messages.forEachIndexed { index, reference ->
                try {
                    android.util.Log.d("GmailRepositoryWithAi", "메시지 조회 중 - ID: ${reference.id}")
                    
                    val message = api.getMessage(
                        authorization = authorization,
                        userId = "me",
                        messageId = reference.id,
                        format = "full",
                        metadataHeaders = listOf("Subject", "Date", "From", "To")
                    )
                    
                    // 시간 필터링: internalDate가 sinceTimestamp 이후인지 확인
                    val messageTimestamp = message.internalDate?.toLongOrNull() ?: 0L
                    if (sinceTimestamp > 0L && messageTimestamp < sinceTimestamp) {
                        android.util.Log.d("GmailRepositoryWithAi", "메시지가 시간 범위 밖: ${messageTimestamp} < ${sinceTimestamp}")
                        return@forEachIndexed
                    }
                    
                    // 진행률 업데이트 (0.1 ~ 0.9 범위)
                    val progress = 0.1f + (index + 1).toFloat() / totalMessages * 0.8f
                    onProgress?.invoke(progress, "메시지 처리 중 (${index + 1}/${totalMessages})")
                    
                    // AI Agent를 통한 처리
                    val hasEvent = processMessageWithAi(message)
                    if (hasEvent) {
                        eventCount++
                    }
                    processed++
                } catch (e: Exception) {
                    android.util.Log.e("GmailRepositoryWithAi", "메시지 처리 중 오류 발생 - ID: ${reference.id}", e)
                    // 개별 메시지 처리 실패해도 계속 진행
                    // 최소한 메시지 정보만이라도 저장 시도
                    try {
                        val subject = "처리 실패한 메시지"
                        val ingestItem = IngestItem(
                            id = reference.id,
                            source = "gmail",
                            type = "note",
                            title = subject,
                            body = "메시지 처리 중 오류가 발생했습니다: ${e.message}",
                            timestamp = System.currentTimeMillis(),
                            dueDate = null,
                            confidence = null,
                            metaJson = null
                        )
                        ingestRepository.upsert(ingestItem)
                        android.util.Log.d("GmailRepositoryWithAi", "오류 발생 메시지를 기본 IngestItem으로 저장 완료 - ID: ${reference.id}")
                        processed++
                    } catch (saveError: Exception) {
                        android.util.Log.e("GmailRepositoryWithAi", "오류 발생 메시지 저장도 실패 - ID: ${reference.id}", saveError)
                    }
                }
            }
            
            val endTimestamp = System.currentTimeMillis()
            android.util.Log.d("GmailRepositoryWithAi", "Gmail 동기화 완료 - 처리: $processed, 일정: $eventCount")
            
            // 완료 진행률 업데이트
            onProgress?.invoke(1.0f, "동기화 완료 (${processed}개 처리, 일정 ${eventCount}개 추출)")
            
            GmailSyncResult.Success(
                upsertedCount = processed,
                eventCount = eventCount,
                startTimestamp = startTimestamp,
                endTimestamp = endTimestamp,
            )
            
        } catch (exception: HttpException) {
            android.util.Log.e("GmailRepositoryWithAi", "Gmail API HTTP 오류", exception)
            when (exception.code()) {
                401 -> {
                    android.util.Log.w("GmailRepositoryWithAi", "Access Token이 만료되었거나 유효하지 않습니다 (401 Unauthorized)")
                    GmailSyncResult.Unauthorized
                }
                403 -> GmailSyncResult.NetworkError("Gmail API 접근 권한이 없습니다.")
                else -> GmailSyncResult.NetworkError(exception.message())
            }
        } catch (io: IOException) {
            android.util.Log.e("GmailRepositoryWithAi", "네트워크 IO 오류", io)
            GmailSyncResult.NetworkError(io.message ?: "IO error")
        } catch (throwable: Throwable) {
            android.util.Log.e("GmailRepositoryWithAi", "예상치 못한 오류", throwable)
            GmailSyncResult.NetworkError(throwable.message ?: "Unknown error")
        }
    }
    
    /**
     * AI Agent를 통한 메시지 처리
     * 모든 메시지를 IngestItem으로 저장하고, 일정이 있으면 Event도 저장
     * @return 일정이 추출되었는지 여부
     */
    private suspend fun processMessageWithAi(message: GmailMessage): Boolean {
        try {
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
            
            // 전체 이메일 본문 추출
            val fullBody = GmailBodyExtractor.extractBody(message)
            
            // 발신자 정보를 포함한 전체 내용
            val enrichedBody = buildString {
                if (from != null) append("발신자: $from\n")
                if (to != null) append("수신자: $to\n\n")
                append(fullBody)
            }
            
            // 현재 시간 기준으로 AI가 일정을 해석 (한국 시간대)
            val currentTimestamp = System.currentTimeMillis()
            val kstTime = java.time.Instant.ofEpochMilli(currentTimestamp)
                .atZone(java.time.ZoneId.of("Asia/Seoul"))
            
            // 원본 이메일 수신 시간 (보관용)
            val originalReceivedTimestamp = message.internalDate?.toLongOrNull() ?: currentTimestamp
            
            android.util.Log.d("GmailRepositoryWithAi", "=================================")
            android.util.Log.d("GmailRepositoryWithAi", "AI Agent 처리 시작 - 제목: $subject")
            android.util.Log.d("GmailRepositoryWithAi", "📱 휴대폰 현재 시간 (ms): $currentTimestamp")
            android.util.Log.d("GmailRepositoryWithAi", "📅 한국 시간(KST): $kstTime")
            android.util.Log.d("GmailRepositoryWithAi", "📧 원본 이메일 수신 시간: ${java.time.Instant.ofEpochMilli(originalReceivedTimestamp)}")
            android.util.Log.d("GmailRepositoryWithAi", "⚠️  AI에게 전달할 시간: $currentTimestamp (현재 시간!)")
            
            // HuenDongMinAiAgent를 통한 처리 (Tool: processGmailForEvent)
            // ⚠️ 중요: currentTimestamp를 전달하여 AI가 현재 시간 기준으로 일정 해석
            val result = huenDongMinAiAgent.processGmailForEvent(
                emailSubject = subject,
                emailBody = enrichedBody,
                receivedTimestamp = originalReceivedTimestamp,  // 원본 수신 시간 사용
                originalEmailId = message.id
            )
            
            android.util.Log.d("GmailRepositoryWithAi", 
                "AI 처리 완료 - Type: ${result.type}, Confidence: ${result.confidence}")
            
            // HuenDongMinAiAgent.processGmailForEvent에서 이미 모든 메시지를 IngestItem으로 저장하므로
            // 여기서는 추가 작업이 필요 없음
            
            // 일정이 추출되었는지 확인
            return result.events.isNotEmpty()
        } catch (e: Exception) {
            android.util.Log.e("GmailRepositoryWithAi", "processMessageWithAi 중 오류 발생 - 메시지 ID: ${message.id}", e)
            
            // AI 처리 실패 시에도 최소한 메시지 정보는 저장
            try {
                val subject = message.payload?.headers?.firstOrNull { 
                    it.name.equals("Subject", ignoreCase = true) 
                }?.value ?: "제목 없음"
                
                val fullBody = try {
                    GmailBodyExtractor.extractBody(message)
                } catch (bodyError: Exception) {
                    "본문 추출 실패: ${bodyError.message}"
                }
                
                val originalReceivedTimestamp = message.internalDate?.toLongOrNull() ?: System.currentTimeMillis()
                
                val ingestItem = IngestItem(
                    id = message.id,
                    source = "gmail",
                    type = "note",
                    title = subject,
                    body = fullBody,
                    timestamp = originalReceivedTimestamp,
                    dueDate = null,
                    confidence = null,
                    metaJson = null
                )
                ingestRepository.upsert(ingestItem)
                android.util.Log.d("GmailRepositoryWithAi", "AI 처리 실패 메시지를 기본 IngestItem으로 저장 완료 - ID: ${message.id}")
            } catch (saveError: Exception) {
                android.util.Log.e("GmailRepositoryWithAi", "오류 발생 메시지 저장도 실패 - ID: ${message.id}", saveError)
            }
            
            // 일정 추출 실패
            return false
        }
    }
}
