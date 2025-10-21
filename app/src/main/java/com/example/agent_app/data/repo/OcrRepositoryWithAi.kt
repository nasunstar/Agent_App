package com.example.agent_app.data.repo

import com.example.agent_app.ai.HuenDongMinAiAgent
import com.example.agent_app.data.dao.EventDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * AI Agent를 사용한 OCR Repository
 * 
 * OCR 텍스트에서 일정을 추출하여 Event로 저장
 */
class OcrRepositoryWithAi(
    private val huenDongMinAiAgent: HuenDongMinAiAgent,
    private val eventDao: EventDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    
    /**
     * OCR 텍스트에서 일정 추출 및 저장
     */
    suspend fun processOcrText(
        ocrText: String,
        source: String = "ocr_share"
    ): OcrProcessingResult = withContext(dispatcher) {
        
        require(ocrText.isNotBlank()) { "OCR 텍스트가 비어 있습니다." }
        
        val originalOcrId = "ocr-${UUID.randomUUID()}"
        
        // 현재 시간 기준으로 AI가 일정을 해석 (한국 시간대)
        val currentTimestamp = System.currentTimeMillis()
        val kstTime = java.time.Instant.ofEpochMilli(currentTimestamp)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        android.util.Log.d("OcrRepositoryWithAi", "=================================")
        android.util.Log.d("OcrRepositoryWithAi", "OCR 처리 시작 - ID: $originalOcrId")
        android.util.Log.d("OcrRepositoryWithAi", "📱 휴대폰 현재 시간 (ms): $currentTimestamp")
        android.util.Log.d("OcrRepositoryWithAi", "📅 한국 시간(KST): $kstTime")
        android.util.Log.d("OcrRepositoryWithAi", "📄 OCR 텍스트 길이: ${ocrText.length}자")
        android.util.Log.d("OcrRepositoryWithAi", "📝 OCR 텍스트 미리보기: ${ocrText.take(100)}...")
        android.util.Log.d("OcrRepositoryWithAi", "⚠️  AI에게 전달할 시간: $currentTimestamp (현재 시간!)")
        
        // HuenDongMinAiAgent를 통한 처리 (Tool: createEventFromImage)
        val result = huenDongMinAiAgent.createEventFromImage(
            ocrText = ocrText,
            currentTimestamp = currentTimestamp,
            originalOcrId = originalOcrId
        )
        
        android.util.Log.d("OcrRepositoryWithAi", 
            "OCR 처리 완료 - Type: ${result.type}, Confidence: ${result.confidence}")
        
        // 저장된 이벤트 조회
        val savedEvent = if (result.type == "event") {
            eventDao.getBySourceId(originalOcrId).firstOrNull()
        } else {
            null
        }
        
        android.util.Log.d("OcrRepositoryWithAi", 
            "저장된 이벤트 조회: ${savedEvent?.title} (ID: ${savedEvent?.id})")
        
        OcrProcessingResult(
            success = true,
            eventType = result.type,
            confidence = result.confidence,
            ocrId = originalOcrId,
            message = "OCR 텍스트가 성공적으로 처리되었습니다.",
            eventId = savedEvent?.id,
            eventTitle = savedEvent?.title,
            startAt = savedEvent?.startAt,
            endAt = savedEvent?.endAt,
            location = savedEvent?.location
        )
    }
}

/**
 * OCR 처리 결과
 */
data class OcrProcessingResult(
    val success: Boolean,
    val eventType: String,
    val confidence: Double,
    val ocrId: String,
    val message: String,
    val eventId: Long? = null,
    val eventTitle: String? = null,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val location: String? = null
)

