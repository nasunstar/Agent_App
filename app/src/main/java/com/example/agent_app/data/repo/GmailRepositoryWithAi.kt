package com.example.agent_app.data.repo

import com.example.agent_app.ai.HuenDongMinAiAgent
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
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    
    suspend fun syncRecentMessages(accessToken: String): GmailSyncResult = withContext(dispatcher) {
        android.util.Log.d("GmailRepositoryWithAi", "Gmail 동기화 시작")
        
        if (accessToken.isBlank()) {
            android.util.Log.w("GmailRepositoryWithAi", "Access Token이 비어있음")
            return@withContext GmailSyncResult.MissingToken
        }
        
        try {
            val cleanToken = accessToken.trim().replace("\n", "").replace("\r", "")
            val authorization = "Bearer $cleanToken"
            
            android.util.Log.d("GmailRepositoryWithAi", "Gmail API 호출 시작")
            
            val listResponse = api.listMessages(
                authorization = authorization,
                userId = "me",
                maxResults = 20,
            )
            
            android.util.Log.d("GmailRepositoryWithAi", "Gmail 메시지 목록 조회 성공 - ${listResponse.messages.size}개")
            
            if (listResponse.messages.isEmpty()) {
                android.util.Log.d("GmailRepositoryWithAi", "메시지가 없음")
                return@withContext GmailSyncResult.Success(upsertedCount = 0)
            }
            
            var processed = 0
            
            listResponse.messages.forEach { reference ->
                android.util.Log.d("GmailRepositoryWithAi", "메시지 조회 중 - ID: ${reference.id}")
                
                val message = api.getMessage(
                    authorization = authorization,
                    userId = "me",
                    messageId = reference.id,
                    format = "full",
                    metadataHeaders = listOf("Subject", "Date", "From", "To")
                )
                
                // AI Agent를 통한 처리
                processMessageWithAi(message)
                processed++
            }
            
            android.util.Log.d("GmailRepositoryWithAi", "Gmail 동기화 완료 - 처리: $processed")
            GmailSyncResult.Success(processed)
            
        } catch (exception: HttpException) {
            android.util.Log.e("GmailRepositoryWithAi", "Gmail API HTTP 오류", exception)
            when (exception.code()) {
                401 -> GmailSyncResult.Unauthorized
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
     */
    private suspend fun processMessageWithAi(message: GmailMessage) {
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
            receivedTimestamp = currentTimestamp,  // 현재 시간 사용!
            originalEmailId = message.id
        )
        
        android.util.Log.d("GmailRepositoryWithAi", 
            "AI 처리 완료 - Type: ${result.type}, Confidence: ${result.confidence}")
    }
}
