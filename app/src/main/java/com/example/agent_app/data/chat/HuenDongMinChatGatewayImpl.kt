package com.example.agent_app.data.chat

import com.example.agent_app.BuildConfig
import com.example.agent_app.data.search.HybridSearchEngine
import com.example.agent_app.domain.chat.gateway.ChatGateway
import com.example.agent_app.domain.chat.model.ChatContextItem
import com.example.agent_app.domain.chat.model.ChatMessage
import com.example.agent_app.domain.chat.model.QueryFilters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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
 * AI 에이전트 "HuenDongMin" 기반 ChatGateway 구현
 * - AI가 시간 파싱, 필터 추출, 답변 생성을 모두 담당
 * - TimeResolver 의존성 제거
 */
class HuenDongMinChatGatewayImpl(
    private val hybridSearchEngine: HybridSearchEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ChatGateway {
    
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
     * AI를 통해 검색 필터를 추출하고 로컬 DB 검색
     */
    override suspend fun fetchContext(
        question: String,
        filters: QueryFilters,
        limit: Int
    ): List<ChatContextItem> = withContext(dispatcher) {
        
        val currentTimestamp = System.currentTimeMillis()
        
        android.util.Log.d("HuenDongMinChatGateway", "질문: $question")
        
        // AI에게 검색 필터 생성 요청 (TimeResolver 대체)
        val aiFilters = extractSearchFilters(question, currentTimestamp)
        
        android.util.Log.d("HuenDongMinChatGateway", "AI 필터: $aiFilters")
        
        // 로컬 DB 검색
        val searchResults = hybridSearchEngine.search(
            question = question,
            filters = aiFilters,
            limit = limit
        )
        
        android.util.Log.d("HuenDongMinChatGateway", "검색 결과: ${searchResults.size}개")
        
        searchResults
    }
    
    /**
     * AI를 통해 답변 생성
     */
    override suspend fun requestChatCompletion(messages: List<ChatMessage>): ChatMessage = withContext(dispatcher) {
        
        // messages에서 사용자 질문과 컨텍스트 정보 추출
        val userMessage = messages.lastOrNull { it.role == ChatMessage.Role.USER }
            ?: return@withContext ChatMessage(
                ChatMessage.Role.ASSISTANT,
                "질문을 이해할 수 없습니다."
            )
        
        android.util.Log.d("HuenDongMinChatGateway", "답변 생성 요청")
        
        try {
            val response = callOpenAiWithChatMessages(messages)
            ChatMessage(ChatMessage.Role.ASSISTANT, response)
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinChatGateway", "답변 생성 실패", e)
            ChatMessage(
                ChatMessage.Role.ASSISTANT,
                "죄송합니다. 답변을 생성하는 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    /**
     * AI를 통해 사용자 질문에서 검색 필터 추출
     */
    private suspend fun extractSearchFilters(
        userQuery: String,
        currentTimestamp: Long
    ): QueryFilters = withContext(Dispatchers.IO) {
        
        val currentDate = java.time.Instant.ofEpochMilli(currentTimestamp)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        // 요일 이름 가져오기 (한글)
        val dayOfWeekKorean = when (currentDate.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "월요일"
            java.time.DayOfWeek.TUESDAY -> "화요일"
            java.time.DayOfWeek.WEDNESDAY -> "수요일"
            java.time.DayOfWeek.THURSDAY -> "목요일"
            java.time.DayOfWeek.FRIDAY -> "금요일"
            java.time.DayOfWeek.SATURDAY -> "토요일"
            java.time.DayOfWeek.SUNDAY -> "일요일"
        }
        
        val systemPrompt = """
            당신은 사용자 질문을 분석하여 검색 필터를 생성하는 AI "HuenDongMin"입니다.
            
            ⚠️⚠️⚠️ 현재 시간 정보 (한국 시간 KST) ⚠️⚠️⚠️
            - 현재 연도: ${currentDate.year}년
            - 현재 월: ${currentDate.monthValue}월
            - 현재 일: ${currentDate.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            - Epoch ms: ${currentTimestamp}ms
            
            📅 날짜 계산 규칙 (단계별 처리):
            
            1단계: 기준 시점 결정
               ⚠️ 사용자 질문에서 명시적 날짜를 먼저 확인하세요:
               - "10월 16일에 약속 잡았어", "10월 16일 다음주 수요일" 등
               
               기준 시점 결정:
               - 질문에 특정 날짜가 **언급되었으면**: 그 날짜를 기준 시점으로 사용
               - 질문에 특정 날짜가 **없으면**: 현재 시간(${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일 $dayOfWeekKorean)을 기준으로 사용
            
            2단계: 상대적 표현 계산
               현재: ${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일 ($dayOfWeekKorean)
               
               A. 기본 표현 (기준 시점 기준):
                  - "오늘": 기준 날짜 00:00 ~ 23:59
                  - "내일": 기준 날짜 + 1일
                  - "모레": 기준 날짜 + 2일
               
               B. 주(week) 관련 표현 (기준 시점 기준):
                  ⚠️ 한국에서 "주"는 월요일~일요일을 기준으로 합니다.
                  
                  - "이번 주": 기준 시점이 속한 주의 월요일 00:00 ~ 일요일 23:59
                  - "다음 주" 또는 "다음주": 기준 시점 기준 다음 주 월요일 00:00 ~ 일요일 23:59
                  - "다음주 X요일": 기준 시점 기준 다음 주의 해당 요일
                  
                  🔍 예시:
                  - 질문: "다음주 수요일 일정 찾아줘" (현재: 10월 21일 화요일)
                    → 기준 시점: 현재 (10월 21일)
                    → 다음주 수요일: 10월 29일(수) ✅
                  
                  - 질문: "10월 16일에 담주 수요일 약속 잡았어" (현재: 10월 21일 화요일)
                    → 기준 시점: 10월 16일 (목요일)
                    → 담주 수요일: 10월 16일 기준 다음주 수요일 = 10월 22일(수) ✅
            
            3. 키워드 추출:
               - 사람 이름, 장소, 이벤트명 등 핵심 단어 추출
               - 예: "김철수", "회의실", "프로젝트 발표"
            
            4. 소스 추출:
               - "이메일에서" → "gmail"
               - "문자" 또는 "카톡" → "ocr"
               - 명시되지 않으면 null
            
            출력 형식 (순수 JSON만):
            {
              "start_time_millis": 1234567890123,
              "end_time_millis": 1234567890123,
              "keywords": ["키워드1", "키워드2"],
              "source": "gmail"
            }
            
            ⚠️ 중요:
            1. 모든 시간은 반드시 계산된 epoch milliseconds 숫자로 반환!
            2. 수식이나 계산식 포함 금지!
            3. 순수 JSON만 반환, 추가 설명 금지!
        """.trimIndent()
        
        val messages = listOf(
            AiChatMessage(role = "system", content = systemPrompt),
            AiChatMessage(role = "user", content = userQuery)
        )
        
        try {
            val response = callOpenAiInternal(messages)
            parseFiltersFromAiResponse(response)
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinChatGateway", "필터 추출 실패, 기본 필터 사용", e)
            // 실패 시 빈 필터 반환
            QueryFilters(
                startTimeMillis = null,
                endTimeMillis = null,
                keywords = emptyList(),
                source = null
            )
        }
    }
    
    /**
     * OpenAI API 호출 (ChatMessage용)
     */
    private suspend fun callOpenAiWithChatMessages(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val aiMessages = messages.map { msg ->
            AiChatMessage(
                role = when (msg.role) {
                    ChatMessage.Role.SYSTEM -> "system"
                    ChatMessage.Role.USER -> "user"
                    ChatMessage.Role.ASSISTANT -> "assistant"
                },
                content = msg.content
            )
        }
        callOpenAiInternal(aiMessages)
    }
    
    /**
     * OpenAI API 호출 (내부용)
     */
    private suspend fun callOpenAiInternal(messages: List<AiChatMessage>): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENAI_API_KEY
        require(apiKey.isNotBlank()) { "OpenAI API 키가 설정되지 않았습니다." }
        
        val request = AiChatRequest(
            model = "gpt-4o-mini",
            messages = messages,
            temperature = 0.3,
            maxTokens = 1000
        )
        
        val requestBody = json.encodeToString(AiChatRequest.serializer(), request)
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
            
            val chatResponse = json.decodeFromString(AiChatResponse.serializer(), responseBody)
            chatResponse.choices.firstOrNull()?.message?.content 
                ?: throw Exception("OpenAI 응답에 내용이 없습니다.")
        }
    }
    
    /**
     * AI 응답에서 QueryFilters 파싱
     */
    private fun parseFiltersFromAiResponse(response: String): QueryFilters {
        return try {
            val cleanedJson = response
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            
            val jsonObj = json.parseToJsonElement(cleanedJson).jsonObject
            
            QueryFilters(
                startTimeMillis = jsonObj["start_time_millis"]?.jsonPrimitive?.longOrNull,
                endTimeMillis = jsonObj["end_time_millis"]?.jsonPrimitive?.longOrNull,
                keywords = jsonObj["keywords"]?.jsonArray?.mapNotNull { 
                    it.jsonPrimitive.content 
                } ?: emptyList(),
                source = jsonObj["source"]?.jsonPrimitive?.content
            )
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinChatGateway", "필터 파싱 실패", e)
            QueryFilters()
        }
    }
}

// ===== 내부 데이터 클래스 =====

@Serializable
private data class AiChatRequest(
    val model: String,
    val messages: List<AiChatMessage>,
    val temperature: Double,
    @SerialName("max_tokens")
    val maxTokens: Int
)

@Serializable
private data class AiChatMessage(
    val role: String,
    val content: String
)

@Serializable
private data class AiChatResponse(
    val choices: List<AiChatChoice>
)

@Serializable
private data class AiChatChoice(
    val message: AiChatMessage
)

