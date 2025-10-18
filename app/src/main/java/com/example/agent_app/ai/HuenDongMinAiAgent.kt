package com.example.agent_app.ai

import com.example.agent_app.BuildConfig
import com.example.agent_app.data.dao.EventDao
import com.example.agent_app.data.dao.EventTypeDao
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.entity.EventType
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.data.repo.IngestRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * AI 에이전트 "HuenDongMin" - Tool을 사용하여 Gmail, OCR, Chatbot 처리
 * 
 * TimeResolver 등 기존 시간 계산 로직을 대체하고, 
 * 모든 처리를 AI가 직접 수행하도록 구성
 */
class HuenDongMinAiAgent(
    private val eventDao: EventDao,
    private val eventTypeDao: EventTypeDao,
    private val ingestRepository: IngestRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    /**
     * Gmail 메일에서 일정 추출 (Tool: processGmailForEvent)
     */
    suspend fun processGmailForEvent(
        emailSubject: String?,
        emailBody: String?,
        receivedTimestamp: Long,
        originalEmailId: String
    ): AiProcessingResult = withContext(dispatcher) {
        
        android.util.Log.d("HuenDongMinAiAgent", "Gmail 처리 시작 - ID: $originalEmailId")
        
        val currentDate = java.time.Instant.ofEpochMilli(receivedTimestamp)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        val systemPrompt = """
            당신은 사용자의 개인 데이터를 지능적으로 관리하는 AI 비서 "HuenDongMin"입니다.
            
            ⚠️⚠️⚠️ 절대적으로 중요: 현재 시간 기준 (한국 시간 KST) ⚠️⚠️⚠️
            - 현재 연도: ${currentDate.year}년
            - 현재 월: ${currentDate.monthValue}월
            - 현재 일: ${currentDate.dayOfMonth}일
            - 전체 시간: $currentDate
            - Epoch ms: ${receivedTimestamp}ms
            
            핵심 원칙:
            1. 시간 인식: 위에 제공된 현재 시간(${receivedTimestamp}ms)을 기준으로 모든 상대 시간을 절대 시간(Epoch ms)으로 변환
            2. 데이터 이원화: 원본은 IngestItem에, 구조화된 정보는 Event에 저장
            3. 명확한 근거: 입력 텍스트에 명확한 근거가 있어야 함
            4. ⚠️ 연도 추론 규칙:
               - 연도가 명시되지 않은 날짜를 만나면:
                 * 해당 월이 현재 월(${currentDate.monthValue})보다 작으면 → 현재 연도(${currentDate.year}) + 1
                 * 해당 월이 현재 월(${currentDate.monthValue})보다 크거나 같으면 → 현재 연도(${currentDate.year})
               - 상대적 표현("내일", "다음 주")은 항상 현재 시간(${receivedTimestamp}ms)을 기준으로 계산
            
            ⚠️ 반드시 현재 연도(${currentDate.year})를 기준으로 판단하세요. 과거 연도를 반환하지 마세요!
        """.trimIndent()
        
        val userPrompt = """
            다음 Gmail 메일을 분석하여 약속/일정이 있는지 확인하고, 있다면 구조화된 JSON으로 반환하세요.
            
            제목: ${emailSubject ?: "(없음)"}
            
            본문:
            ${emailBody ?: ""}
            
            📅 현재 기준 시간:
            - 연도: ${currentDate.year}년
            - 월: ${currentDate.monthValue}월
            - 일: ${currentDate.dayOfMonth}일
            - Epoch ms: ${receivedTimestamp}ms
            
            ⚠️ 처리 규칙:
            1. 메일 내용에서 날짜/시간을 추출하여 위의 현재 시간을 기준으로 절대 시간(epoch milliseconds)으로 변환
            2. 연도가 없는 날짜는 현재 연도(${currentDate.year})와 현재 월(${currentDate.monthValue})을 기준으로 판단
            3. 상대적 표현("내일", "다음 주")은 현재 날짜(${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일)를 기준으로 계산
            4. 반드시 현재 시간(${receivedTimestamp}ms)보다 미래 시간으로 변환하세요
            
            출력 형식 (순수 JSON만):
            {
              "type": "event" | "contact" | "note",
              "confidence": 0.0 ~ 1.0,
              "extractedData": {
                "title": "일정 제목",
                "startAt": epoch_milliseconds (Long),
                "endAt": epoch_milliseconds | null,
                "location": "장소" | null,
                "type": "이벤트 타입" | null,
                "body": "요약"
              }
            }
        """.trimIndent()
        
        val messages = listOf(
            AiMessage(role = "system", content = systemPrompt),
            AiMessage(role = "user", content = userPrompt)
        )
        
        val response = callOpenAi(messages)
        val result = parseAiResponse(response)
        
        android.util.Log.d("HuenDongMinAiAgent", "=== AI 응답 분석 ===")
        android.util.Log.d("HuenDongMinAiAgent", "Type: ${result.type}, Confidence: ${result.confidence}")
        
        // IngestItem 저장 (원본 보관)
        val ingestItem = IngestItem(
            id = originalEmailId,
            source = "gmail",
            type = result.type,
            title = emailSubject,
            body = emailBody,
            timestamp = receivedTimestamp,
            dueDate = result.extractedData["startAt"]?.jsonPrimitive?.longOrNull,
            confidence = result.confidence,
            metaJson = null
        )
        ingestRepository.upsert(ingestItem)
        
        // Event 저장 (일정인 경우)
        if (result.type == "event") {
            val originalStartAt = result.extractedData["startAt"]?.jsonPrimitive?.longOrNull
            android.util.Log.d("HuenDongMinAiAgent", "AI 추출 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
            
            // 과거 날짜 보정: AI가 과거 날짜를 반환하면 자동으로 1년 추가
            val correctedData = correctPastDate(result.extractedData, receivedTimestamp)
            val correctedStartAt = correctedData["startAt"]?.jsonPrimitive?.longOrNull
            android.util.Log.d("HuenDongMinAiAgent", "보정 후 시간: ${correctedStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
            
            val event = createEventFromAiData(correctedData, originalEmailId, "gmail")
            eventDao.upsert(event)
            android.util.Log.d("HuenDongMinAiAgent", "Event 저장 완료 - ${event.title}, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
        }
        
        result
    }
    
    /**
     * OCR 텍스트에서 일정 추출 (Tool: createEventFromImage)
     */
    suspend fun createEventFromImage(
        ocrText: String,
        currentTimestamp: Long,
        originalOcrId: String
    ): AiProcessingResult = withContext(dispatcher) {
        
        android.util.Log.d("HuenDongMinAiAgent", "=== OCR 처리 시작 ===")
        android.util.Log.d("HuenDongMinAiAgent", "OCR ID: $originalOcrId")
        
        val currentDate = java.time.Instant.ofEpochMilli(currentTimestamp)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        android.util.Log.d("HuenDongMinAiAgent", "📱 현재 시간(ms): $currentTimestamp")
        android.util.Log.d("HuenDongMinAiAgent", "📅 현재 날짜: ${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일")
        android.util.Log.d("HuenDongMinAiAgent", "⚠️ AI에게 전달: ${currentDate.year}년 ${currentDate.monthValue}월을 기준으로 해석하라고 명령!")
        
        val systemPrompt = """
            당신은 이미지에서 일정을 추출하는 AI 비서 "HuenDongMin"입니다.
            
            ⚠️⚠️⚠️ 절대적으로 중요: 현재 시간 기준 (한국 시간 KST) ⚠️⚠️⚠️
            - 현재 연도: ${currentDate.year}년
            - 현재 월: ${currentDate.monthValue}월
            - 현재 일: ${currentDate.dayOfMonth}일
            - 전체 시간: $currentDate
            - Epoch ms: ${currentTimestamp}ms
            
            특별 지침:
            1. 한글 OCR 오인식 대응: "모레 오 T 3 시" → "모레 오후 3시" 등 문맥으로 파악
            2. 시간 변환: 위에 제공된 현재 시간(${currentTimestamp}ms)을 기준으로 상대 시간을 절대 시간(epoch milliseconds)으로 변환
            3. 구조 인식: 표, 대화창, 일정표 등의 구조를 파악하여 정보 추출
            4. ⚠️ 연도 추론 규칙:
               - 연도가 명시되지 않은 날짜(예: "9월 30일")를 만나면:
                 * 해당 월이 현재 월(${currentDate.monthValue})보다 작으면 → 현재 연도(${currentDate.year}) + 1
                 * 해당 월이 현재 월(${currentDate.monthValue})보다 크거나 같으면 → 현재 연도(${currentDate.year})
               - 상대적 표현("내일", "모레", "다음주")은 항상 현재 시간(${currentTimestamp}ms)을 기준으로 계산
            
            ⚠️ 반드시 현재 연도(${currentDate.year})를 기준으로 판단하세요. 과거 연도를 반환하지 마세요!
        """.trimIndent()
        
        val userPrompt = """
            다음 OCR 텍스트에서 일정 정보를 추출하세요.
            
            OCR 텍스트:
            ${ocrText}
            
            📅 현재 기준 시간:
            - 연도: ${currentDate.year}년
            - 월: ${currentDate.monthValue}월
            - 일: ${currentDate.dayOfMonth}일
            - Epoch ms: ${currentTimestamp}ms
            
            ⚠️ 처리 규칙:
            1. 텍스트에서 날짜/시간을 추출하여 위의 현재 시간을 기준으로 절대 시간(epoch milliseconds)으로 변환
            2. 연도가 없는 날짜는 현재 연도(${currentDate.year})와 현재 월(${currentDate.monthValue})을 기준으로 판단
            3. 상대적 표현("내일", "모레")은 현재 날짜(${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일)를 기준으로 계산
            4. 반드시 현재 시간(${currentTimestamp}ms)보다 미래 시간으로 변환하세요
            
            출력 형식 (순수 JSON만):
            {
              "type": "event",
              "confidence": 0.0 ~ 1.0,
              "extractedData": {
                "title": "일정 제목",
                "startAt": epoch_milliseconds (Long),
                "endAt": epoch_milliseconds | null,
                "location": "장소" | null,
                "type": "이벤트 타입" | null,
                "body": "원본 OCR 텍스트 전체"
              }
            }
        """.trimIndent()
        
        val messages = listOf(
            AiMessage(role = "system", content = systemPrompt),
            AiMessage(role = "user", content = userPrompt)
        )
        
        val response = callOpenAi(messages)
        val result = parseAiResponse(response)
        
        android.util.Log.d("HuenDongMinAiAgent", "=== OCR AI 응답 분석 ===")
        android.util.Log.d("HuenDongMinAiAgent", "Type: ${result.type}, Confidence: ${result.confidence}")
        
        // IngestItem 저장
        val ingestItem = IngestItem(
            id = originalOcrId,
            source = "ocr",
            type = result.type,
            title = result.extractedData["title"]?.jsonPrimitive?.content,
            body = ocrText,
            timestamp = currentTimestamp,
            dueDate = result.extractedData["startAt"]?.jsonPrimitive?.longOrNull,
            confidence = result.confidence,
            metaJson = null
        )
        ingestRepository.upsert(ingestItem)
        
        // Event 저장
        if (result.type == "event") {
            val originalStartAt = result.extractedData["startAt"]?.jsonPrimitive?.longOrNull
            android.util.Log.d("HuenDongMinAiAgent", "OCR AI 추출 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
            
            // 과거 날짜 보정
            val correctedData = correctPastDate(result.extractedData, currentTimestamp)
            val correctedStartAt = correctedData["startAt"]?.jsonPrimitive?.longOrNull
            android.util.Log.d("HuenDongMinAiAgent", "OCR 보정 후 시간: ${correctedStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
            
            val event = createEventFromAiData(correctedData, originalOcrId, "ocr")
            eventDao.upsert(event)
            android.util.Log.d("HuenDongMinAiAgent", "OCR Event 저장 완료 - ${event.title}, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
        }
        
        result
    }
    
    /**
     * AI 응답에서 Event 엔티티 생성
     */
    private suspend fun createEventFromAiData(
        extractedData: Map<String, JsonElement?>,
        sourceId: String,
        sourceType: String
    ): Event {
        val typeName = extractedData["type"]?.jsonPrimitive?.content ?: "일반"
        val eventType = getOrCreateEventType(typeName)
        
        return Event(
            userId = 1L,
            typeId = eventType.id,
            title = extractedData["title"]?.jsonPrimitive?.content ?: "제목 없음",
            body = extractedData["body"]?.jsonPrimitive?.content,
            startAt = extractedData["startAt"]?.jsonPrimitive?.longOrNull,
            endAt = extractedData["endAt"]?.jsonPrimitive?.longOrNull,
            location = extractedData["location"]?.jsonPrimitive?.content,
            status = "pending",
            sourceType = sourceType,
            sourceId = sourceId
        )
    }
    
    private suspend fun getOrCreateEventType(typeName: String): EventType {
        val existing = eventTypeDao.getByName(typeName)
        if (existing != null) return existing
        
        val newType = EventType(typeName = typeName)
        val id = eventTypeDao.upsert(newType)
        return newType.copy(id = id)
    }
    
    /**
     * OpenAI API 호출
     */
    private suspend fun callOpenAi(messages: List<AiMessage>): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENAI_API_KEY
        require(apiKey.isNotBlank()) { "OpenAI API 키가 설정되지 않았습니다." }
        
        val request = OpenAiRequest(
            model = "gpt-4o-mini",
            messages = messages,
            temperature = 0.3,
            maxTokens = 1000
        )
        
        val requestBody = json.encodeToString(OpenAiRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())
        
        val httpRequest = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        client.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string() 
                ?: throw Exception("Empty response from OpenAI")
            
            if (!response.isSuccessful) {
                throw Exception("OpenAI API 오류: ${response.code} - $responseBody")
            }
            
            val openAiResponse = json.decodeFromString(OpenAiResponse.serializer(), responseBody)
            openAiResponse.choices.firstOrNull()?.message?.content 
                ?: throw Exception("OpenAI 응답에 내용이 없습니다.")
        }
    }
    
    /**
     * AI 응답 파싱
     */
    private fun parseAiResponse(response: String): AiProcessingResult {
        return try {
            // JSON 추출 (마크다운 코드 블록 제거)
            val cleanedJson = response
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            
            val jsonObj = json.parseToJsonElement(cleanedJson).jsonObject
            
            val type = jsonObj["type"]?.jsonPrimitive?.content ?: "note"
            val confidence = jsonObj["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.5
            val extractedData = jsonObj["extractedData"]?.jsonObject?.toMap() ?: emptyMap()
            
            AiProcessingResult(type, confidence, extractedData)
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinAiAgent", "AI 응답 파싱 실패", e)
            AiProcessingResult(
                type = "note",
                confidence = 0.0,
                extractedData = mapOf("body" to json.parseToJsonElement("\"파싱 실패: ${e.message}\""))
            )
        }
    }
}

// ===== 데이터 클래스 =====

@Serializable
data class OpenAiRequest(
    val model: String,
    val messages: List<AiMessage>,
    val temperature: Double,
    @SerialName("max_tokens")
    val maxTokens: Int
)

@Serializable
data class AiMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAiResponse(
    val choices: List<OpenAiChoice>
)

@Serializable
data class OpenAiChoice(
    val message: AiMessage
)

/**
 * 과거 날짜 보정 함수
 * 
 * AI가 과거 날짜를 반환한 경우, 자동으로 연도를 조정합니다.
 * 예: 현재 2025년 10월인데 "9월 30일" → 2026년 9월 30일로 보정
 */
private fun correctPastDate(
    extractedData: Map<String, JsonElement?>,
    referenceTimestamp: Long
): Map<String, JsonElement?> {
    val startAt = extractedData["startAt"]?.jsonPrimitive?.longOrNull ?: return extractedData
    
    val currentDate = java.time.Instant.ofEpochMilli(referenceTimestamp)
        .atZone(java.time.ZoneId.of("Asia/Seoul"))
    val eventDate = java.time.Instant.ofEpochMilli(startAt)
        .atZone(java.time.ZoneId.of("Asia/Seoul"))
    
    android.util.Log.d("HuenDongMinAiAgent", "🔍 correctPastDate 실행")
    android.util.Log.d("HuenDongMinAiAgent", "  기준 시간(ms): $referenceTimestamp")
    android.util.Log.d("HuenDongMinAiAgent", "  AI 추출 시간(ms): $startAt")
    android.util.Log.d("HuenDongMinAiAgent", "  현재: ${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일")
    android.util.Log.d("HuenDongMinAiAgent", "  AI 추출: ${eventDate.year}년 ${eventDate.monthValue}월 ${eventDate.dayOfMonth}일")
    
    // 과거인지 확인 (30일 이상 이전)
    val thirtyDaysInMs = 30L * 24 * 60 * 60 * 1000
    val timeDiff = startAt - referenceTimestamp
    android.util.Log.d("HuenDongMinAiAgent", "  시간 차이: ${timeDiff / (24 * 60 * 60 * 1000)}일")
    
    if (startAt < referenceTimestamp - thirtyDaysInMs) {
        android.util.Log.d("HuenDongMinAiAgent", "  ⚠️ 30일 이상 과거 → 보정 필요!")
        // 월/일은 유지하면서 연도만 조정
        val targetMonth = eventDate.monthValue
        val currentMonth = currentDate.monthValue
        
        // 해당 월이 현재 월보다 이전이면 다음 해, 이후면 올해
        val targetYear = if (targetMonth < currentMonth) {
            currentDate.year + 1
        } else {
            currentDate.year
        }
        
        // 새로운 날짜 생성 (월/일/시간 유지, 연도만 변경)
        val correctedDate = eventDate.withYear(targetYear)
        val correctedStartAt = correctedDate.toInstant().toEpochMilli()
        
        val correctedEndAt = extractedData["endAt"]?.jsonPrimitive?.longOrNull?.let { endAt ->
            val endDate = java.time.Instant.ofEpochMilli(endAt)
                .atZone(java.time.ZoneId.of("Asia/Seoul"))
            endDate.withYear(targetYear).toInstant().toEpochMilli()
        }
        
        android.util.Log.d("HuenDongMinAiAgent", 
            "✅ 보정 완료: ${eventDate.year}년 → ${targetYear}년")
        android.util.Log.d("HuenDongMinAiAgent", 
            "  최종: ${correctedDate.year}년 ${correctedDate.monthValue}월 ${correctedDate.dayOfMonth}일 ${correctedDate.hour}:${correctedDate.minute}")
        
        return extractedData.toMutableMap().apply {
            this["startAt"] = JsonPrimitive(correctedStartAt)
            if (correctedEndAt != null) {
                this["endAt"] = JsonPrimitive(correctedEndAt)
            }
        }
    }
    
    android.util.Log.d("HuenDongMinAiAgent", "  ⏭️  보정 불필요 (미래 날짜 또는 30일 이내)")
    android.util.Log.d("HuenDongMinAiAgent", "  최종 결과: ${eventDate.year}년 ${eventDate.monthValue}월 ${eventDate.dayOfMonth}일")
    return extractedData
}

/**
 * AI 처리 결과
 */
data class AiProcessingResult(
    val type: String,  // "event", "contact", "note"
    val confidence: Double,
    val extractedData: Map<String, JsonElement?>
)

