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
        
        val systemPrompt = """
            당신은 사용자 질문을 분석하여 검색 필터를 생성하는 AI "HuenDongMin"입니다.
            
            현재 시간: ${java.time.Instant.ofEpochMilli(currentTimestamp)} (${currentTimestamp}ms)
            
            사용자 질문에서:
            1. 시간 표현 추출: "내일", "이번 주", "10월 19일" → start_time_millis, end_time_millis
            2. 키워드 추출: "김철수", "프로젝트", "회의" → keywords 배열
            3. 소스 추출: "이메일에서", "문자 온 거" → source ("gmail", "ocr" 등)
            
            출력 형식:
            {
              "start_time_millis": epoch_milliseconds | null,
              "end_time_millis": epoch_milliseconds | null,
              "keywords": ["키워드1", "키워드2"] | [],
              "source": "gmail" | "ocr" | null
            }
            
            ⚠️ 순수 JSON만 반환하세요. 추가 설명 없이 JSON만 출력하세요.
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

