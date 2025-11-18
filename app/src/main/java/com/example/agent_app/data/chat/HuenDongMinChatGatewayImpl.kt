package com.example.agent_app.data.chat

import com.example.agent_app.BuildConfig
import com.example.agent_app.ai.HuenDongMinAiAgent
import com.example.agent_app.data.dao.EventDao
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.search.HybridSearchEngine
import com.example.agent_app.domain.chat.gateway.ChatGateway
import com.example.agent_app.domain.chat.model.ChatContextItem
import com.example.agent_app.domain.chat.model.ChatMessage
import com.example.agent_app.domain.chat.model.QueryFilters
import com.example.agent_app.service.EventNotificationService
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
    private val eventDao: EventDao,
    private val huenDongMinAiAgent: HuenDongMinAiAgent,
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
            // 일정 생성 의도 감지
            val questionText = userMessage.content
            val shouldCreateEvent = detectEventCreationIntent(questionText)
            
            if (shouldCreateEvent) {
                android.util.Log.d("HuenDongMinChatGateway", "일정 생성 의도 감지됨")
                val eventCreationResult = tryCreateEventFromQuestion(questionText, messages)
                if (eventCreationResult != null) {
                    // 일정 생성 성공 시 답변에 포함
                    val response = callOpenAiWithChatMessages(messages)
                    val enhancedResponse = buildString {
                        appendLine(response)
                        appendLine()
                        appendLine("✅ 일정이 생성되었습니다!")
                        appendLine("📅 제목: ${eventCreationResult.title}")
                        eventCreationResult.startAt?.let {
                            val dateTime = java.time.Instant.ofEpochMilli(it)
                                .atZone(java.time.ZoneId.of("Asia/Seoul"))
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm"))
                            appendLine("🕐 시간: $dateTime")
                        }
                        eventCreationResult.location?.let {
                            appendLine("📍 장소: $it")
                        }
                    }
                    return@withContext ChatMessage(ChatMessage.Role.ASSISTANT, enhancedResponse)
                }
            }
            
            // 일반 답변 생성
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
     * 사용자 질문에서 일정 생성 의도 감지
     */
    private fun detectEventCreationIntent(question: String): Boolean {
        val lowerQuestion = question.lowercase()
        val creationKeywords = listOf(
            "약속 잡아줘", "약속 잡아", "일정 잡아줘", "일정 잡아", "일정 만들어줘", "일정 만들어",
            "일정 추가해줘", "일정 추가해", "스케줄 잡아줘", "스케줄 잡아",
            "예약해줘", "예약해", "잡아줘", "잡아"
        )
        return creationKeywords.any { lowerQuestion.contains(it) }
    }
    
    /**
     * 사용자 질문에서 일정 정보 추출 및 생성
     */
    private suspend fun tryCreateEventFromQuestion(
        question: String,
        conversationHistory: List<ChatMessage>
    ): Event? = withContext(dispatcher) {
        try {
            val currentTimestamp = System.currentTimeMillis()
            val currentDate = java.time.Instant.ofEpochMilli(currentTimestamp)
                .atZone(java.time.ZoneId.of("Asia/Seoul"))
            
            val dayOfWeekKorean = when (currentDate.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "월요일"
                java.time.DayOfWeek.TUESDAY -> "화요일"
                java.time.DayOfWeek.WEDNESDAY -> "수요일"
                java.time.DayOfWeek.THURSDAY -> "목요일"
                java.time.DayOfWeek.FRIDAY -> "금요일"
                java.time.DayOfWeek.SATURDAY -> "토요일"
                java.time.DayOfWeek.SUNDAY -> "일요일"
            }
            
            // 이전 대화에서 참석자 정보 추출 (예: "친구", "김철수" 등)
            val participants = extractParticipantsFromHistory(conversationHistory, question)
            
            val systemPrompt = """
                당신은 사용자의 자연어 명령에서 일정 정보를 추출하는 AI입니다.
                
                ⚠️⚠️⚠️ 현재 시간 정보 (한국 표준시 KST, Asia/Seoul, UTC+9) ⚠️⚠️⚠️
                - 현재 연도: ${currentDate.year}년
                - 현재 월: ${currentDate.monthValue}월
                - 현재 일: ${currentDate.dayOfMonth}일
                - 현재 요일: $dayOfWeekKorean
                - 현재 Epoch ms: ${currentTimestamp}ms
                
                📋 **일정 정보 추출 규칙:**
                1. 날짜/시간: "다음주 수요일", "내일 오후 3시", "10월 30일 14시" 등을 epoch milliseconds로 변환
                2. 제목: "친구랑 약속", "회의", "점심 약속" 등에서 추출
                3. 참석자: "친구", "김철수", "팀원들" 등에서 추출
                4. 장소: "카페", "회의실", "식당" 등에서 추출 (없으면 null)
                
                🔴🔴🔴 날짜 계산 규칙 🔴🔴🔴
                - "다음주 수요일" → 현재 기준 다음 주 수요일
                - "내일" → 현재 기준 다음날
                - "모레" → 현재 기준 2일 후
                - "10월 30일" → ${currentDate.year}년 10월 30일
                - 시간이 없으면 14:00 (오후 2시)를 기본값으로 사용
                
                출력 형식 (순수 JSON만):
                {
                  "shouldCreate": true,
                  "title": "일정 제목",
                  "startAt": 1234567890123,
                  "endAt": 1234567890123,
                  "location": "장소 또는 null",
                  "body": "일정 설명",
                  "type": "약속"
                }
                
                일정 생성 의도가 없으면:
                {
                  "shouldCreate": false
                }
            """.trimIndent()
            
            val userPrompt = """
                다음 사용자 질문에서 일정 정보를 추출하세요:
                
                질문: $question
                
                ${if (participants.isNotEmpty()) "참석자 정보: ${participants.joinToString(", ")}\n" else ""}
            """.trimIndent()
            
            val messages = listOf(
                AiChatMessage(role = "system", content = systemPrompt),
                AiChatMessage(role = "user", content = userPrompt)
            )
            
            val response = callOpenAiInternal(messages)
            val eventData = parseEventCreationResponse(response)
            
            if (eventData["shouldCreate"]?.jsonPrimitive?.content == "true") {
                val title = eventData["title"]?.jsonPrimitive?.content ?: "약속"
                val startAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
                val endAt = eventData["endAt"]?.jsonPrimitive?.content?.toLongOrNull()
                val location = eventData["location"]?.jsonPrimitive?.content
                val body = eventData["body"]?.jsonPrimitive?.content
                val typeName = eventData["type"]?.jsonPrimitive?.content ?: "약속"
                
                if (startAt != null) {
                    // EventType 가져오기 또는 생성
                    val eventType = huenDongMinAiAgent.getOrCreateEventType(typeName)
                    
                    val event = Event(
                        userId = 1L,
                        typeId = eventType.id,
                        title = title,
                        body = body,
                        startAt = startAt,
                        endAt = endAt ?: startAt + (60 * 60 * 1000), // 기본 1시간
                        location = location,
                        status = "pending",
                        sourceType = "chat",
                        sourceId = "chat-${System.currentTimeMillis()}"
                    )
                    
                    val eventId = eventDao.upsert(event)
                    val savedEvent = event.copy(id = if (eventId == 0L) event.id else eventId)
                    
                    // 알림 스케줄링
                    try {
                        EventNotificationService.scheduleNotificationForEvent(savedEvent, eventDao)
                    } catch (e: Exception) {
                        android.util.Log.e("HuenDongMinChatGateway", "알림 스케줄링 실패", e)
                    }
                    
                    android.util.Log.d("HuenDongMinChatGateway", "일정 생성 완료: ${savedEvent.title}, ID: ${savedEvent.id}")
                    return@withContext savedEvent
                }
            }
            
            null
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinChatGateway", "일정 생성 실패", e)
            null
        }
    }
    
    /**
     * 이전 대화에서 참석자 정보 추출
     */
    private fun extractParticipantsFromHistory(
        conversationHistory: List<ChatMessage>,
        currentQuestion: String
    ): List<String> {
        val participants = mutableListOf<String>()
        val allText = (conversationHistory.map { it.content } + currentQuestion).joinToString(" ")
        
        // 일반적인 참석자 패턴
        val patterns = listOf(
            Regex("친구"),
            Regex("([가-힣]+)랑"),
            Regex("([가-힣]+)와"),
            Regex("([가-힣]+)과"),
            Regex("([가-힣]+)님"),
        )
        
        patterns.forEach { pattern ->
            pattern.findAll(allText).forEach { match ->
                val participant = match.groupValues.getOrNull(1) ?: match.value
                if (participant.isNotBlank() && participant !in participants) {
                    participants.add(participant)
                }
            }
        }
        
        return participants
    }
    
    /**
     * AI 응답에서 일정 생성 정보 파싱
     */
    private fun parseEventCreationResponse(response: String): Map<String, kotlinx.serialization.json.JsonElement> {
        return try {
            val cleanedJson = response
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            
            val jsonObj = json.parseToJsonElement(cleanedJson).jsonObject
            jsonObj.toMap()
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinChatGateway", "일정 정보 파싱 실패", e)
            emptyMap()
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
            
            ⚠️⚠️⚠️ 현재 시간 정보 (한국 표준시 KST, Asia/Seoul, UTC+9) ⚠️⚠️⚠️
            - 현재 연도: ${currentDate.year}년
            - 현재 월: ${currentDate.monthValue}월
            - 현재 일: ${currentDate.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            - Epoch ms: ${currentTimestamp}ms (한국 시간 기준)
            
            📅 날짜 계산 규칙 (단계별 처리, 현재: ${currentDate.year}년!):
            
            ⚠️⚠️⚠️ 최우선: 명시적 날짜는 ${currentDate.year}년 기준입니다! ⚠️⚠️⚠️
            
            명시적 날짜 인식:
            - "9월 30일" → ${currentDate.year}년 9월 30일 (2024년이 아님!)
            - "10월 16일" → ${currentDate.year}년 10월 16일
            - "12월 25일" → ${currentDate.year}년 12월 25일
            
            1단계: 기준 시점 결정
               ⚠️ 사용자 질문에서 명시적 날짜를 먼저 확인하세요:
               - "10월 16일에 약속 잡았어", "10월 16일 다음주 수요일" 등
               
               기준 시점 결정:
               - 질문에 특정 날짜가 **언급되었으면**: 그 날짜를 기준 시점으로 사용
               - 질문에 특정 날짜가 **없으면**: 현재 시간(${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일 $dayOfWeekKorean)을 기준으로 사용
            
            2단계: 상대적 표현 계산
               현재: ${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일 ($dayOfWeekKorean)
               
               A. 기본 표현 (기준 시점 기준):
                  ⚠️ 날짜 범위는 반드시 하루 전체를 포함하세요:
                  - "오늘": start = 오늘 00:00:00, end = 오늘 23:59:59
                  - "내일": start = 내일 00:00:00, end = 내일 23:59:59
                  - "모레": start = 모레 00:00:00, end = 모레 23:59:59
                  - "10월 30일": start = 10월 30일 00:00:00, end = 10월 30일 23:59:59
               
               B. 주(week) 관련 표현 (기준 시점 기준):
                  ⚠️ 한국에서 "주"는 월요일~일요일을 기준으로 합니다.
                  
                  - "이번 주": start = 이번 주 월요일 00:00:00, end = 이번 주 일요일 23:59:59
                  - "다음 주" 또는 "다음주": start = 다음 주 월요일 00:00:00, end = 다음 주 일요일 23:59:59
                  - "다음주 수요일": start = 다음주 수요일 00:00:00, end = 다음주 수요일 23:59:59
                  
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
            
            🎯 **완전한 실전 예시 (현재: ${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일):**
            
            질문: "9월 30일에 무슨 일정이 있지?"
            
            **사고 과정:**
            1. "9월 30일" 발견 → ${currentDate.year}년 9월 30일
            2. 하루 전체 범위: 00:00:00 ~ 23:59:59
            3. Epoch 계산:
               - start: ${currentDate.year}년 9월 30일 00:00:00 = 1759190400000
               - end: ${currentDate.year}년 9월 30일 23:59:59 = 1759276799999
            
            **JSON 출력:**
            ```json
            {
              "start_time_millis": 1759190400000,
              "end_time_millis": 1759276799999,
              "keywords": [],
              "source": null
            }
            ```
            
            ⛔ **절대 금지:**
            - ❌ "9월 30일"을 2024년으로 계산
            - ❌ "9월 30일"을 1760000000000으로 계산
            - ✅ "9월 30일" = ${currentDate.year}년 9월 30일 = 1759190400000
            
            출력 형식 (순수 JSON만):
            {
              "start_time_millis": 1234567890123,
              "end_time_millis": 1234567890123,
              "keywords": ["키워드1", "키워드2"],
              "source": "gmail"
            }
            
            ⚠️⚠️⚠️ 중요 규칙 (현재는 ${currentDate.year}년입니다!):
            1. 모든 시간은 한국 표준시(KST, UTC+9) 기준으로 계산하세요!
               - epoch milliseconds는 한국 시간으로 변환한 값입니다
               - 예시 (반드시 ${currentDate.year}년 기준으로 계산!):
                 * ${currentDate.year}년 9월 30일 00:00:00 (KST) = 1759190400000
                 * ${currentDate.year}년 9월 30일 23:59:59 (KST) = 1759276799999
                 * ${currentDate.year}년 10월 28일 15:00:00 (KST) = 1761631200000
            
            2. 날짜 범위는 반드시 하루 전체(00:00:00 ~ 23:59:59)를 포함하세요!
               ✅ 좋은 예:
               - "오늘" → start: 오늘 00:00:00, end: 오늘 23:59:59
               - "내일" → start: 내일 00:00:00, end: 내일 23:59:59
               - "다음주 수요일" → start: 다음주 수요일 00:00:00, end: 다음주 수요일 23:59:59
               
               ❌ 나쁜 예:
               - "오늘" → start: 지금 현재 시각 (이렇게 하지 마세요!)
            
            3. 구체적 시간이 명시된 경우에만 해당 시간을 사용하세요:
               - "오늘 오후 3시" → start: 오늘 15:00:00, end: 오늘 15:59:59
               - "내일 오전 10시" → start: 내일 10:00:00, end: 내일 10:59:59
            
            4. 모든 시간은 반드시 계산된 epoch milliseconds 숫자로 반환!
               - 수식이나 계산식 포함 금지!
            
            5. 순수 JSON만 반환, 추가 설명 금지!
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
                // 에러 응답 JSON 파싱 시도
                val errorMessage = try {
                    val errorJson = Json.parseToJsonElement(responseBody).jsonObject
                    val errorObj = errorJson["error"]?.jsonObject
                    val message = errorObj?.get("message")?.jsonPrimitive?.content
                    
                    when (response.code) {
                        429 -> {
                            if (message?.contains("quota", ignoreCase = true) == true) {
                                "OpenAI API 할당량을 초과했습니다. 계정의 요금제와 결제 정보를 확인해주세요.\n\n자세한 내용은 다음 문서를 참고하세요:\nhttps://platform.openai.com/docs/guides/rate-limits"
                            } else {
                                "OpenAI API 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."
                            }
                        }
                        401 -> "OpenAI API 키가 유효하지 않습니다. API 키를 확인해주세요."
                        403 -> "OpenAI API 접근이 거부되었습니다. 권한을 확인해주세요."
                        500, 502, 503, 504 -> "OpenAI 서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요."
                        else -> message ?: "OpenAI API 오류: ${response.code}"
                    }
                } catch (e: Exception) {
                    // JSON 파싱 실패 시 기본 메시지 사용
                    when (response.code) {
                        429 -> "OpenAI API 할당량을 초과했습니다. 계정의 요금제와 결제 정보를 확인해주세요."
                        else -> "OpenAI API 오류: ${response.code} - ${responseBody.take(200)}"
                    }
                }
                
                throw Exception(errorMessage)
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

            // 1) 기본 파싱
            var start = jsonObj["start_time_millis"]?.jsonPrimitive?.longOrNull
            var end = jsonObj["end_time_millis"]?.jsonPrimitive?.longOrNull
            val keywords = jsonObj["keywords"]?.jsonArray?.mapNotNull { 
                it.jsonPrimitive.content 
            } ?: emptyList()
            val source = jsonObj["source"]?.jsonPrimitive?.content

            // 2) KST(Asia/Seoul) 기준 하루 범위 보정
            // AI가 UTC 기준으로 반환했을 가능성에 대비해,
            // 동일한 KST 날짜의 00:00:00 ~ 23:59:59로 정규화
            if (start != null && end != null) {
                val zone = java.time.ZoneId.of("Asia/Seoul")
                val startZdt = java.time.Instant.ofEpochMilli(start).atZone(zone)
                val endZdt = java.time.Instant.ofEpochMilli(end).atZone(zone)

                // 날짜만 유지하고 KST 자정/말초로 고정
                val normalizedStart = startZdt
                    .withHour(0).withMinute(0).withSecond(0).withNano(0)
                    .toInstant().toEpochMilli()
                val normalizedEnd = endZdt
                    .withHour(23).withMinute(59).withSecond(59).withNano(999_000_000)
                    .toInstant().toEpochMilli()

                // 역전 방지
                if (normalizedEnd >= normalizedStart) {
                    start = normalizedStart
                    end = normalizedEnd
                }
            }

            QueryFilters(
                startTimeMillis = start,
                endTimeMillis = end,
                keywords = keywords,
                source = source
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

