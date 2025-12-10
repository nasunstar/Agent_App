package com.example.agent_app.data.chat

import com.example.agent_app.BuildConfig
import com.example.agent_app.ai.HuenDongMinAiAgent
import com.example.agent_app.data.dao.EventDao
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.search.HybridSearchEngine
import com.example.agent_app.domain.chat.gateway.ChatGateway
import com.example.agent_app.domain.chat.model.ChatAttachment
import com.example.agent_app.domain.chat.model.ChatContextItem
import com.example.agent_app.domain.chat.model.ChatMessage
import com.example.agent_app.domain.chat.model.QueryFilters
import com.example.agent_app.service.EventNotificationService
import com.example.agent_app.ai.EventTimeParser
import com.example.agent_app.ai.ResolveContext
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
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
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
        val adjustedFilters = adjustWeekFiltersIfNeeded(question, currentTimestamp, aiFilters)
        
        // 필터 상세 로깅
        adjustedFilters.startTimeMillis?.let { start ->
            val startDate = java.time.Instant.ofEpochMilli(start)
                .atZone(java.time.ZoneId.of("Asia/Seoul"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            android.util.Log.d("HuenDongMinChatGateway", "필터 시작 시간: $startDate ($start)")
        } ?: android.util.Log.d("HuenDongMinChatGateway", "필터 시작 시간: null")
        
        adjustedFilters.endTimeMillis?.let { end ->
            val endDate = java.time.Instant.ofEpochMilli(end)
                .atZone(java.time.ZoneId.of("Asia/Seoul"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            android.util.Log.d("HuenDongMinChatGateway", "필터 종료 시간: $endDate ($end)")
        } ?: android.util.Log.d("HuenDongMinChatGateway", "필터 종료 시간: null")
        
        android.util.Log.d("HuenDongMinChatGateway", "AI 필터 키워드: ${adjustedFilters.keywords}")
        android.util.Log.d("HuenDongMinChatGateway", "AI 필터 소스: ${adjustedFilters.source}")
        
        // 로컬 DB 검색
        val searchResults = hybridSearchEngine.search(
            question = question,
            filters = adjustedFilters,
            limit = limit
        )
        
        android.util.Log.d("HuenDongMinChatGateway", "검색 결과: ${searchResults.size}개")
        searchResults.forEachIndexed { index, item ->
            android.util.Log.d("HuenDongMinChatGateway", "결과 ${index + 1}: ${item.title} (source: ${item.source}, relevance: ${item.relevance})")
        }
        
        searchResults
    }
    
    /**
     * AI를 통해 답변 생성
     */
    override suspend fun requestChatCompletion(
        messages: List<ChatMessage>,
        context: List<ChatContextItem>
    ): ChatMessage = withContext(dispatcher) {
        
        // messages에서 사용자 질문과 컨텍스트 정보 추출
        val userMessage = messages.lastOrNull { it.role == ChatMessage.Role.USER }
            ?: return@withContext ChatMessage(
                ChatMessage.Role.ASSISTANT,
                "질문을 이해할 수 없습니다."
            )
        
        android.util.Log.d("HuenDongMinChatGateway", "답변 생성 요청")
        
        try {
            // 일정 생성 의도 감지 (자연어 패턴 포함)
            val questionText = userMessage.content
            val shouldCreateEvent = detectEventCreationIntent(questionText)
            
            if (shouldCreateEvent) {
                android.util.Log.d("HuenDongMinChatGateway", "일정 생성 의도 감지됨: $questionText")
                val eventCreationResult = tryCreateEventFromQuestion(questionText, messages)
                if (eventCreationResult != null) {
                    // 일정 생성 성공 시 답변 생성 (간단한 텍스트 + attachment에 Event 포함)
                    val enhancedResponse = "✅ 일정을 생성했어요!\n\n아래 카드에서 세부 내용을 확인하실 수 있어요."
                    val attachment = ChatAttachment.EventPreview(eventCreationResult)
                    return@withContext ChatMessage(
                        ChatMessage.Role.ASSISTANT, 
                        enhancedResponse,
                        attachment = attachment
                    )
                } else {
                    // 일정 생성 실패 시 일반 답변 생성
                    android.util.Log.w("HuenDongMinChatGateway", "일정 생성 실패, 일반 답변 생성")
                }
            }
            
            // 일반 답변 생성
            val response = callOpenAiWithChatMessages(messages)
            ChatMessage(ChatMessage.Role.ASSISTANT, response)
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinChatGateway", "답변 생성 실패", e)
            val errorMessage = when {
                e.message?.contains("API 키") == true -> "OpenAI API 키가 설정되지 않았어요. 설정에서 API 키를 입력해주세요."
                e.message?.contains("401") == true -> "OpenAI API 인증에 실패했어요. API 키를 확인해주세요."
                e.message?.contains("429") == true -> "API 사용량이 초과되었어요. 잠시 후 다시 시도해주세요."
                e.message?.contains("network", ignoreCase = true) == true -> "네트워크 연결을 확인해주세요."
                else -> "제가 답변을 생성하는 중 문제가 발생했어요. 다시 시도해주세요."
            }
            ChatMessage(ChatMessage.Role.ASSISTANT, errorMessage)
        }
    }
    
    /**
     * 사용자 질문에서 일정 생성 의도 감지
     * 명시적 키워드 또는 시간/날짜 표현이 포함된 자연어 문장 감지
     */
    private fun detectEventCreationIntent(question: String): Boolean {
        val lowerQuestion = question.lowercase()
        
        // 1. 명시적 일정 생성 키워드
        val explicitKeywords = listOf(
            "약속 잡아줘", "약속 잡아", "일정 잡아줘", "일정 잡아", "일정 만들어줘", "일정 만들어",
            "일정 추가해줘", "일정 추가해", "스케줄 잡아줘", "스케줄 잡아",
            "예약해줘", "예약해", "잡아줘", "잡아", "일정 등록", "일정 등록해줘"
        )
        if (explicitKeywords.any { lowerQuestion.contains(it) }) {
            return true
        }
        
        // 2. 시간/날짜 표현 + 일정 관련 단어 조합 감지
        val timeExpressions = listOf(
            "일뒤", "일 후", "일뒤에", "일 후에",
            "일전", "일 전", "일전에", "일 전에",
            "내일", "모레", "글피", "다음주", "다음 주", "담주",
            "오늘", "오후", "오전", "아침", "점심", "저녁",
            "시", "분", "월", "일", "요일",
            "다음주", "이번주", "저번주"
        )
        
        val eventKeywords = listOf(
            "약속", "일정", "회의", "미팅", "만남", "만나", "만날",
            "점심", "저녁", "식사", "카페", "영화", "약속있", "일정있",
            "스케줄", "예약", "방문", "출장", "행사", "모임",
            "있어", "있음", "있습니다", "있어요"  // "3일뒤에 약속 있어" 같은 표현
        )
        
        val hasTimeExpression = timeExpressions.any { lowerQuestion.contains(it) }
        val hasEventKeyword = eventKeywords.any { lowerQuestion.contains(it) }
        
        // 시간 표현과 일정 관련 단어가 모두 있으면 일정 생성 의도로 판단
        // 단, 질문 형식("언제", "어디서", "뭐해")은 제외
        val questionWords = listOf("언제", "어디서", "어디", "뭐해", "뭐하", "무엇", "알려줘", "알려", "찾아줘", "찾아")
        val isQuestion = questionWords.any { lowerQuestion.contains(it) }
        
        return hasTimeExpression && hasEventKeyword && !isQuestion
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
                - 현재 시간: ${currentDate.hour}시 ${currentDate.minute}분
                - 현재 Epoch ms (nowEpochMs): ${currentTimestamp}ms
                
                📋 **일정 정보 추출 규칙:**
                1. 날짜/시간: 다양한 표현을 epoch milliseconds로 변환합니다.
                   - ⚠️⚠️⚠️ 매우 중요: epoch milliseconds는 KST(한국 표준시, Asia/Seoul, UTC+9) 기준으로 계산합니다!
                   - 사용자가 구어체, 줄임말, 모호한 표현을 사용해도 최대한 정확하게 해석해야 합니다.
                   - 아래 [자연어 시간 표현 해석 규칙]을 반드시 따릅니다.
                   - 시간이 명시되지 않은 경우 기본값 14:00(오후 2시)을 사용합니다.
                   - ⚠️ 시간대 변환 주의: UTC가 아닌 KST 기준으로 epoch milliseconds를 계산하세요!
                   
                2. 제목: 일정의 핵심 내용만 자연스럽게 추출합니다.
                   - "종민이랑 점심약속" → "종민과 점심 약속"
                   - "담주 수욜 저녁에 팀 회의" → "팀 회의"
                   - "회의 있어" → "회의"
                   
                3. 참석자: 사람 이름이나 관계 표현을 추출합니다.
                   - "종민이랑" → ["종민"]
                   - "팀원들이랑" → ["팀원들"]
                   - 참석자 정보는 body에 포함하거나 title에 포함합니다.
                   
                4. 장소: 장소 정보가 있으면 추출합니다.
                   - "카페에서 보자" → "카페"
                   - "강남역 근처에서" → "강남역 근처"
                   - 없으면 null
                   
                5. 일정 유형(type): "약속", "회의", "식사", "통화", "개인" 등으로 간단히 분류합니다.
                
                ⏰ **자연어 시간 표현 해석 규칙:**
                
                ⚠️⚠️⚠️ 매우 중요: 띄어쓰기 정규화 ⚠️⚠️⚠️
                사용자 질문에서 띄어쓰기 차이는 무시하고 동일하게 처리합니다!
                - "이번주" = "이번 주" (동일하게 처리)
                - "다음주" = "다음 주" (동일하게 처리)
                - "이번달" = "이번 달" (동일하게 처리)
                - "다음달" = "다음 달" (동일하게 처리)
                - "내일모레" = "내일 모레" (동일하게 처리)
                띄어쓰기 유무와 관계없이 의미가 동일하면 같은 날짜 범위로 해석합니다!
                
                1. 기준 시각
                   - 모든 날짜/시간 해석은 제공된 현재 시각(nowEpochMs=${currentTimestamp}ms, 한국 시간 KST, Asia/Seoul)을 기준으로 합니다.
                   - 출력하는 epoch millisecond 역시 KST 기준으로 계산합니다.
                
                2. 줄임말/오타/띄어쓰기 정규화 예시
                   아래와 같은 구어/줄임/오타/띄어쓰기 차이는 먼저 표준 형태로 정규화한 뒤 계산합니다.
                   - "낼", "내" → "내일"
                   - "모래" → "모레"
                   - "낼모레", "내일 모레" → "내일모레" (현재 +2일, 띄어쓰기 무관)
                   - "담주", "담쥬", "낸쥬", "다음 주", "다음주" → "다음주" (모두 동일하게 처리)
                   - "담달", "담닭", "다음 달", "다음달" → "다음달" (모두 동일하게 처리)
                   - "쫌", "좀", "쫌따", "좀따", "좀이따", "이따", "이따가" → "조금 이따가"
                   - "수욜" → "수요일", "목욜" → "목요일", "금욜" → "금요일"
                   - "퇴근후", "퇴근하고" → "퇴근 후"
                   - "지금바로" → "지금"
                
                3. 날짜 관련 표현
                   ⚠️ 중요: 아래 표현들은 띄어쓰기 유무와 관계없이 동일하게 처리합니다!
                   - "오늘" → 기준 날짜의 00:00:00 ~ 23:59:59
                   - "내일" → 기준 날짜 +1일
                   - "모레" → 기준 날짜 +2일
                   - "내일모레", "내일 모레" → 기준 날짜 +2일 (띄어쓰기 무관)
                   - "이번 주", "이번주" → 이번 주 일요일 00:00:00 ~ 토요일 23:59:59 (띄어쓰기 무관, 동일 처리)
                     * ⚠️ 중요: 이번주는 일요일부터 시작하여 토요일까지입니다!
                   - "다음주", "다음 주" → 다음 주 일요일 00:00:00 ~ 토요일 23:59:59 (띄어쓰기 무관, 동일 처리)
                     * ⚠️ 중요: 다음주는 일요일부터 시작하여 토요일까지입니다!
                   - "이번 주 금요일", "이번주 금요일" → 이번 주의 금요일 (띄어쓰기 무관)
                   - "다음주 수요일", "다음 주 수요일" → 다음 주의 수요일 (띄어쓰기 무관)
                   - "다다음주", "다다음 주" → 다음주 + 1주 (띄어쓰기 무관)
                   - "이번 달", "이번달" → 이번 달 1일 00:00:00 ~ 마지막 날 23:59:59 (띄어쓰기 무관, 동일 처리)
                   - "다음달", "다음 달" → 다음 달 1일 00:00:00 ~ 마지막 날 23:59:59 (띄어쓰기 무관, 동일 처리)
                
                4. 시간대 표현 → 구간 기본값 (시간이 구체적으로 명시되지 않은 경우만)
                   아래와 같이 시간대 어휘만 나오면, 특정 시간구간으로 해석합니다.
                   - "새벽" → 03:00~06:00 (시작 시간: 03:00)
                   - "아침" → 06:00~09:00 (시작 시간: 07:00)
                   - "오전" → 09:00~12:00 (시작 시간: 10:00)
                   - "점심", "점심시간" → 12:00~13:00 (시작 시간: 12:00)
                   - "오후" (단독) → 13:00~18:00 (시작 시간: 14:00)
                   - "저녁" → 18:00~21:00 (시작 시간: 19:00)
                   - "밤" → 21:00~24:00 (시작 시간: 21:00)
                   - "퇴근 후" → 기본적으로 18:00~20:00 (이미 지난 시각이면 다음날 같은 시간대로 이월)
                   
                   예시:
                   - "오늘 저녁" → 오늘 18:00~21:00 (이미 이 시간이 지났다면 내일 18:00~21:00)
                   - "내일 아침" → 내일 06:00~09:00
                   - "담주 수욜 밤" → 다음주 수요일 21:00~24:00
                
                5. 구체적인 시간 표현 (⚠️ 매우 중요! 시간이 명시된 경우 이 규칙을 우선 적용)
                   - "오후 N시", "PM N시" 형식: 정확히 24시간 형식으로 변환합니다.
                     * "오후 1시" → 13:00 (정확히, 13:20 아님!)
                     * "오후 6시" → 18:00 (정확히, 17:20 아님!)
                     * "오후 12시" → 12:00 (정확히)
                   - "오전 N시", "AM N시" 형식:
                     * "오전 1시" → 01:00
                     * "오전 12시" → 00:00
                   - 분이 명시되지 않으면 반드시 00분으로 설정합니다.
                     * "오후 6시" → 18:00 (정확히)
                     * "오후 6시 20분" → 18:20 (분이 명시된 경우에만)
                   - ⚠️ 주의: 사용자가 "오후 6시"라고 명확히 말했다면, 반드시 18:00으로 해석해야 합니다.
                     시간대 기본값(14:00)을 사용하지 마세요!
                
                6. 상대 시간 표현
                   - "N일 뒤", "N일 후" → 기준 날짜 +N일, 시간이 따로 없으면 기본 14:00
                   - "3일뒤 오후 1시" → 기준 날짜 +3일, 13:00 (정확히)
                   - "조금 이따가", "좀 이따", "쫌따", "좀따" → 기준 시각 +30분을 중심으로 1시간 범위로 해석
                     (예: now +30분 ~ now +90분, body에 '사용자가 "조금 이따가"라고 표현하여 대략적인 시간으로 설정'이라고 명시)
                   - "나중에 보자", "언제 한번 보자" 처럼 매우 모호한 표현은
                     → 구체적인 날짜/시간이 부족하다고 판단하고, 일정 생성/검색 범위에는 사용하지 않습니다.
                
                7. 과거 시간대 처리
                   - "오늘 저녁에 보자"인데 현재 시각이 이미 오늘 21시 이후라면
                     → 자동으로 "내일 저녁"으로 이월하여 해석했다고 body에 명시합니다.
                   - 사용자가 과거 날짜를 명확히 말한 경우(예: "지난주 금요일")는 그대로 과거 범위로 유지합니다.
                
                8. 시간이 생략된 경우의 기본값
                   - 날짜만 있고 시간 정보가 전혀 없으면 기본 14:00(오후 2시)로 설정합니다.
                   - "저녁에 보자"처럼 시간대만 있으면 위 시간대 표에 따라 시작/끝 시간을 설정하고,
                     body에 "사용자가 '저녁'이라고 표현하여 18~21시 구간으로 해석"이라고 남깁니다.
                
                ⚠️⚠️⚠️ 일정 생성 의도 판단 ⚠️⚠️⚠️
                다음 패턴은 일정 생성 의도로 판단:
                - "N일뒤에 OO 있어" / "N일 후에 OO 있어"
                - "다음주 수요일에 ○○하자", "담주 수욜 저녁에 보자"
                - "내일 저녁에 회의 잡아줘", "낼 밤에 전화하자"
                - "퇴근 후에 잠깐 회의하자"
                - "OO일 OO시에 OO 있어"
                - "OO랑 OO 약속 잡았어"
                
                다음 패턴은 질문이므로 일정 생성 의도 아님:
                - "언제 OO 있어?" (질문)
                - "담주에 일정 있어?" (질문)
                - "OO 일정 알려줘" (조회 요청)
                - "OO 일정 찾아줘" (조회 요청)
                
                다음 패턴은 너무 모호하므로 일정 생성하지 않음:
                - "나중에 한번 보자" → shouldCreate=false, body에 "시점이 너무 모호해서 일정으로 만들지 않음" 명시
                
                출력 형식 (순수 JSON만):
                {
                  "shouldCreate": true,
                  "title": "일정 제목",
                  "startAt": 1234567890123,  // ⚠️ KST 기준 epoch milliseconds (UTC 아님!)
                  "endAt": 1234567890123,    // ⚠️ KST 기준 epoch milliseconds (UTC 아님!)
                  "location": "장소 또는 null",
                  "body": "일정 설명 (시간 해석 시 가정이 있었으면 여기 자연스럽게 적기)",
                  "type": "약속"
                }
                
                ⚠️⚠️⚠️ epoch milliseconds 계산 시 주의사항 ⚠️⚠️⚠️
                - 현재 시간(nowEpochMs)은 이미 KST 기준입니다.
                - "오후 6시" = 18:00 (KST) → 해당 날짜의 18:00 KST를 epoch milliseconds로 변환
                - 예: "2025-12-03 오후 6시" → 2025-12-03 18:00 KST → epoch milliseconds
                - ⚠️ UTC로 변환하지 마세요! KST 그대로 epoch milliseconds를 계산하세요!
                - ⚠️ "오후 6시"를 19:20으로 계산하지 마세요! 정확히 18:00입니다!
                
                일정 생성 의도가 없으면:
                {
                  "shouldCreate": false
                }
                
                주의:
                - 모호한 표현을 억지로 추측하기보다는, 일정 생성 의도가 분명할 때만 shouldCreate=true로 설정합니다.
                - "조금 이따가"처럼 상대적으로 모호한 경우, 합리적인 기본값을 사용하되,
                  body에 '사용자가 "조금 이따가"라고 표현하여 now+30분 기준으로 설정함'처럼 가정을 남겨주세요.
                - 한국어로만 작성합니다.
            """.trimIndent()
            
            val userPrompt = """
                다음 사용자 질문에서 일정 정보를 추출하세요:
                
                질문: $question
                
                ${if (participants.isNotEmpty()) "참석자 정보: ${participants.joinToString(", ")}\n" else ""}
                
                ⚠️ 중요: 질문에 시간/날짜와 일정 관련 내용이 모두 포함되어 있으면 반드시 shouldCreate: true로 설정하세요.
                예: "3일뒤 오후 1시에 종민이랑 점심약속 있어" → shouldCreate: true
                
                ⚠️ 시간 파싱 중요 예시:
                - "오늘 오후 6시에 졸업 프로젝트 발표가 있어" 
                  → startAt: 오늘 18:00 (정확히 18:00, 17:20 아님!)
                - "내일 오후 3시 회의"
                  → startAt: 내일 15:00 (정확히 15:00)
                - "오후 6시 20분"
                  → startAt: 18:20 (분이 명시된 경우만)
            """.trimIndent()
            
            val messages = listOf(
                AiChatMessage(role = "system", content = systemPrompt),
                AiChatMessage(role = "user", content = userPrompt)
            )
            
            val response = callOpenAiInternal(messages)
            
            // ⚠️ 디버깅: LLM 응답 로깅
            android.util.Log.d("HuenDongMinChatGateway", "=== LLM 일정 생성 응답 ===")
            android.util.Log.d("HuenDongMinChatGateway", "원본 질문: $question")
            android.util.Log.d("HuenDongMinChatGateway", "LLM 응답: $response")
            
            val eventData = parseEventCreationResponse(response)
            
            // ⚠️ 디버깅: 파싱된 데이터 로깅
            eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()?.let { startAt ->
                val parsedTime = java.time.Instant.ofEpochMilli(startAt)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                android.util.Log.d("HuenDongMinChatGateway", 
                    "파싱된 시작 시간: ${parsedTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
            }
            
            // shouldCreate를 boolean 또는 문자열로 처리
            val shouldCreate = eventData["shouldCreate"]?.let { element ->
                val content = element.jsonPrimitive.content
                when {
                    content == "true" -> true
                    content == "false" -> false
                    content.toBooleanStrictOrNull() == true -> true
                    else -> false
                }
            } ?: false
            
            if (shouldCreate) {
                val title = eventData["title"]?.jsonPrimitive?.content ?: "약속"
                var startAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
                var endAt = eventData["endAt"]?.jsonPrimitive?.content?.toLongOrNull()
                val location = eventData["location"]?.jsonPrimitive?.content
                var body = eventData["body"]?.jsonPrimitive?.content
                val typeName = eventData["type"]?.jsonPrimitive?.content ?: "약속"
                
                if (startAt != null) {
                    // ⚠️ 시간 파싱 검증: 규칙 기반 파서로 재검증
                    val validatedTime = validateTimeParsing(question, startAt, currentTimestamp)
                    if (validatedTime != null) {
                        val timeDiff = kotlin.math.abs(validatedTime - startAt)
                        val diffMinutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(timeDiff)
                        
                        // 시간 차이가 30분 이상이면 검토 필요로 표시
                        if (diffMinutes >= 30) {
                            android.util.Log.w("HuenDongMinChatGateway", 
                                "⚠️ 시간 파싱 불일치 감지! LLM: ${java.time.Instant.ofEpochMilli(startAt).atZone(java.time.ZoneId.of("Asia/Seoul"))}, " +
                                "규칙 기반: ${java.time.Instant.ofEpochMilli(validatedTime).atZone(java.time.ZoneId.of("Asia/Seoul"))}, " +
                                "차이: ${diffMinutes}분")
                            
                            // 규칙 기반 파서 결과를 우선 사용
                            startAt = validatedTime
                            endAt = validatedTime + (60 * 60 * 1000) // 기본 1시간
                            
                            // body에 검증 메시지 추가
                            val validationNote = "\n\n[시스템 검증: 원본 텍스트에서 추출한 시간으로 수정되었습니다]"
                            body = (body ?: "") + validationNote
                        } else {
                            android.util.Log.d("HuenDongMinChatGateway", 
                                "✅ 시간 파싱 검증 통과 (차이: ${diffMinutes}분)")
                        }
                    }
                    
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
     * LLM이 파싱한 시간을 규칙 기반 파서로 검증
     * 
     * @param originalText 원본 사용자 질문
     * @param llmParsedTime LLM이 파싱한 시간 (epoch milliseconds)
     * @param referenceTimestamp 기준 시점
     * @return 규칙 기반 파서가 추출한 시간 (epoch milliseconds), 실패 시 null
     */
    private fun validateTimeParsing(
        originalText: String,
        llmParsedTime: Long,
        referenceTimestamp: Long
    ): Long? {
        return try {
            // 규칙 기반 파서로 시간 추출
            val expressions = EventTimeParser.extractTimeExpressions(originalText)
            if (expressions.isEmpty()) {
                android.util.Log.d("HuenDongMinChatGateway", "규칙 기반 파서: 시간 표현 없음")
                return null
            }
            
            val resolved = EventTimeParser.resolveExpressions(
                originalText,
                expressions,
                ResolveContext(referenceTimestamp, "Asia/Seoul")
            )
            
            if (resolved.isEmpty()) {
                android.util.Log.d("HuenDongMinChatGateway", "규칙 기반 파서: 시간 해석 실패")
                return null
            }
            
            val ruleBasedTime = resolved.first().startEpochMs
            android.util.Log.d("HuenDongMinChatGateway", 
                "시간 검증 - LLM: ${java.time.Instant.ofEpochMilli(llmParsedTime).atZone(java.time.ZoneId.of("Asia/Seoul"))}, " +
                "규칙 기반: ${java.time.Instant.ofEpochMilli(ruleBasedTime).atZone(java.time.ZoneId.of("Asia/Seoul"))}")
            
            ruleBasedTime
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinChatGateway", "시간 검증 실패", e)
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
            - Epoch ms (nowEpochMs): ${currentTimestamp}ms (한국 시간 기준)
            
            📅 날짜 계산 규칙:
            
            1단계: 기준 시점 결정
               ⚠️ 사용자 질문에서 명시적 날짜를 먼저 확인하세요:
               - 명시적인 날짜가 있으면 그 날짜를 기준 시점으로 사용합니다.
               - 없으면 nowEpochMs(현재 시각, KST)를 기준으로 사용합니다.
            
            2단계: 상대적/구어체 표현 계산
            
            ⏰ **자연어 시간 표현 해석 규칙:**
            
            ⚠️⚠️⚠️ 매우 중요: 띄어쓰기 정규화 ⚠️⚠️⚠️
            사용자 질문에서 띄어쓰기 차이는 무시하고 동일하게 처리합니다!
            - "이번주" = "이번 주" (동일하게 처리)
            - "다음주" = "다음 주" (동일하게 처리)
            - "이번달" = "이번 달" (동일하게 처리)
            - "다음달" = "다음 달" (동일하게 처리)
            - "내일모레" = "내일 모레" (동일하게 처리)
            띄어쓰기 유무와 관계없이 의미가 동일하면 같은 날짜 범위로 해석합니다!
            
            1. 기준 시각
               - 모든 날짜/시간 해석은 제공된 현재 시각(nowEpochMs=${currentTimestamp}ms, 한국 시간 KST)을 기준으로 합니다.
               - 출력하는 epoch millisecond 역시 KST 기준으로 계산합니다.
            
            2. 줄임말/오타/띄어쓰기 정규화 예시
               아래와 같은 구어/줄임/오타/띄어쓰기 차이는 먼저 표준 형태로 정규화한 뒤 계산합니다.
               - "낼", "내" → "내일"
               - "모래" → "모레"
               - "담주", "담쥬", "낸쥬", "다음 주", "다음주" → "다음주" (모두 동일하게 처리)
               - "담달", "담닭", "다음 달", "다음달" → "다음달" (모두 동일하게 처리)
               - "수욜" → "수요일", "목욜" → "목요일", "금욜" → "금요일"
               - "이번주말", "이번 주말", "이번 주 말" → 이번 주 토요일~일요일 (모두 동일하게 처리)
               - "다음주말", "다음 주말", "다음 주 말" → 다음 주 토요일~일요일 (모두 동일하게 처리)
               - "이번주", "이번 주" → 이번 주 일요일~토요일 (띄어쓰기 유무와 관계없이 동일하게 처리)
                 * ⚠️ 중요: 이번주는 일요일부터 시작하여 토요일까지입니다!
            
            3. 날짜 관련 표현
               ⚠️ 중요: 아래 표현들은 띄어쓰기 유무와 관계없이 동일하게 처리합니다!
               - "오늘" → 오늘 00:00:00 ~ 23:59:59
               - "내일" → 내일 00:00:00 ~ 23:59:59
               - "어제" → 어제 00:00:00 ~ 23:59:59
               - "모레" → 모레 00:00:00 ~ 23:59:59
               - "이번 주", "이번주" → 이번 주 일요일 00:00:00 ~ 토요일 23:59:59 (띄어쓰기 무관, 동일 처리)
                 * 현재 날짜를 기준으로 이번 주 일요일과 토요일을 계산합니다.
                 * 예: 현재가 2025년 12월 10일(수요일)이면, 이번 주는 2025년 12월 7일(일) 00:00:00 ~ 12월 13일(토) 23:59:59
                 * ⚠️ 중요: 이번주는 일요일부터 시작하여 토요일까지입니다!
               - "다음주", "다음 주" → 다음 주 일요일 00:00:00 ~ 토요일 23:59:59 (띄어쓰기 무관, 동일 처리)
                 * 다음주는 이번주 다음 주의 일요일부터 토요일까지입니다.
                 * 예: 현재가 2025년 12월 10일(수요일)이면, 다음주는 2025년 12월 14일(일) 00:00:00 ~ 12월 20일(토) 23:59:59
                 * ⚠️ 중요: 다음주는 일요일부터 시작하여 토요일까지입니다!
               - "이번 주 금요일", "이번주 금요일" → 이번 주 금요일 하루 (00:00:00 ~ 23:59:59) (띄어쓰기 무관)
               - "다음주 수요일", "다음 주 수요일" → 다음 주 수요일 하루 (00:00:00 ~ 23:59:59) (띄어쓰기 무관)
               - "이번 달", "이번달" → 이번 달 1일 00:00:00 ~ 마지막 날 23:59:59 (띄어쓰기 무관, 동일 처리)
               - "지난달", "저번달", "지난 달", "저번 달" → 지난 달 전체 (1일 00:00:00 ~ 마지막 날 23:59:59) (띄어쓰기 무관)
               - "다음달", "담달", "다음 달" → 다음 달 전체 (1일 00:00:00 ~ 마지막 날 23:59:59) (띄어쓰기 무관)
               - "10월 30일" → ${currentDate.year}년 10월 30일 00:00:00 ~ 23:59:59 (과거면 다음 해)
            
            4. 시간대 표현 (검색 필터용)
               검색 필터에서는 시간대 단위가 너무 좁지 않아도 되므로,
               "저녁에 했던 회의" 같은 표현은 해당 날짜의 18:00~23:59:59 범위로 처리할 수 있습니다.
               - "새벽" → 03:00~06:00
               - "아침" → 06:00~09:00
               - "오전" → 09:00~12:00
               - "점심" → 12:00~13:00
               - "오후" → 13:00~18:00
               - "저녁" → 18:00~23:59:59
               - "밤" → 21:00~23:59:59
               - "퇴근 후" → 18:00~20:00 (이미 지났으면 다음날)
               
               대부분의 경우, 날짜 단위 범위(start_time_millis, end_time_millis)를 설정하면 충분합니다.
            
            5. 상대 시간 표현
               - "N일 뒤", "N일 후" → 기준 날짜 +N일
               - "나중에 했던 회의" 등 너무 모호한 표현은 날짜 범위를 비워두고 keywords만 채우는 것이 안전합니다.
            
            3. 키워드 추출:
               - 사람 이름, 장소, 이벤트명, 중요한 명사/동사는 keywords 배열에 넣습니다.
               - 예: "김철수", "회의실", "프로젝트 발표"
               - ⚠️ 중요: "다음주 일정은 뭐야?", "이번주 일정 알려줘" 같은 질문에서 "일정"이라는 키워드를 반드시 추출하세요.
               - "일정", "약속", "회의", "스케줄" 같은 단어는 keywords에 포함하세요.
            
            4. 소스 추출:
               - "이메일에서", "메일에서" → "gmail"
               - "카톡에서", "메신저에서" → "chat" 또는 내부에서 사용하는 소스명
               - 명시적인 소스가 없으면 null 또는 기본값을 사용합니다.
            
            ⚠️⚠️⚠️ 질문 패턴 처리 예시 (반드시 참고하세요!) ⚠️⚠️⚠️
            
            현재 날짜가 ${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일($dayOfWeekKorean)인 경우:
            
            - "다음주 일정은 뭐야?" 또는 "다음주 일정이 뭐야?"
              → start_time_millis: 다음 주 일요일 00:00:00 (KST 기준 epoch milliseconds)
              → end_time_millis: 다음 주 토요일 23:59:59 (KST 기준 epoch milliseconds)
              → keywords: ["일정"]
              → ⚠️ 중요: 다음주는 일요일부터 시작하여 토요일까지입니다! 현재 날짜를 기준으로 다음 주 일요일과 토요일을 정확히 계산하세요!
              
            - "이번주 일정 알려줘"
              → start_time_millis: 이번 주 일요일 00:00:00 (KST 기준 epoch milliseconds)
              → end_time_millis: 이번 주 토요일 23:59:59 (KST 기준 epoch milliseconds)
              → ⚠️ 중요: 이번주는 일요일부터 시작하여 토요일까지입니다!
              → keywords: ["일정"]
              
            - "내일 일정 있어?"
              → start_time_millis: 내일 00:00:00 (KST 기준 epoch milliseconds)
              → end_time_millis: 내일 23:59:59 (KST 기준 epoch milliseconds)
              → keywords: ["일정"]
              
            - "오늘 약속 뭐야?"
              → start_time_millis: 오늘 00:00:00 (KST 기준 epoch milliseconds)
              → end_time_millis: 오늘 23:59:59 (KST 기준 epoch milliseconds)
              → keywords: ["약속"]
            
            ⚠️⚠️⚠️ 다음주 계산 방법 (매우 중요!) ⚠️⚠️⚠️
            1. 현재 날짜의 요일을 확인하세요.
            2. 다음 주 일요일을 찾으세요 (일요일~토요일 기준):
               - 현재가 일요일이면: 현재 + 7일
               - 현재가 월요일이면: 현재 + 6일
               - 현재가 화요일이면: 현재 + 5일
               - 현재가 수요일이면: 현재 + 4일
               - 현재가 목요일이면: 현재 + 3일
               - 현재가 금요일이면: 현재 + 2일
               - 현재가 토요일이면: 현재 + 1일
            3. 다음 주 일요일 00:00:00을 KST 기준으로 epoch milliseconds로 변환하세요.
            4. 다음 주 토요일 23:59:59를 KST 기준으로 epoch milliseconds로 변환하세요.
            
            ⚠️⚠️⚠️ 반드시 계산된 숫자(epoch milliseconds)를 반환하세요! 수식이나 설명을 포함하지 마세요!
            
            출력 형식 (순수 JSON만):
            {
              "start_time_millis": 1234567890123,
              "end_time_millis": 1234567890123,
              "keywords": ["키워드1", "키워드2"],
              "source": "gmail" // 또는 null
            }
            
            주의:
            - 날짜/시간 표현이 전혀 없으면, start_time_millis와 end_time_millis는 null로 둘 수 있습니다.
            - 너무 모호한 표현("나중에 했던 회의" 등)은 현재 시점 주변 며칠을 넓게 잡지 말고,
              오히려 날짜 범위를 비워둔 채 keywords만 채우는 것이 더 안전합니다.
            - 모든 시간은 반드시 계산된 epoch milliseconds 숫자로 반환하세요 (수식이나 계산식 포함 금지).
            - 순수 JSON만 반환, 추가 설명 금지!
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
            // 실패 시 질문에서 간단한 키워드만 추출하여 필터 생성
            val simpleKeywords = userQuery.split(" ")
                .filter { it.length > 1 && !it.matches(Regex("^[0-9]+$")) }
                .take(5)
            QueryFilters(
                startTimeMillis = null,
                endTimeMillis = null,
                keywords = simpleKeywords,
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
                ?: throw Exception("OpenAI API 응답이 비어있습니다.")
            
            if (!response.isSuccessful) {
                val errorMessage = when (response.code) {
                    401 -> "OpenAI API 인증 실패: API 키를 확인해주세요."
                    429 -> "OpenAI API 사용량 초과: 잠시 후 다시 시도해주세요."
                    500, 502, 503 -> "OpenAI 서버 오류: 잠시 후 다시 시도해주세요."
                    else -> "OpenAI API 오류 (${response.code}): $responseBody"
                }
                throw Exception(errorMessage)
            }
            
            val chatResponse = try {
                json.decodeFromString(AiChatResponse.serializer(), responseBody)
            } catch (e: Exception) {
                android.util.Log.e("HuenDongMinChatGateway", "응답 파싱 실패: $responseBody", e)
                throw Exception("OpenAI 응답 형식 오류: ${e.message}")
            }
            
            val content = chatResponse.choices.firstOrNull()?.message?.content?.trim()
            if (content.isNullOrBlank()) {
                throw Exception("OpenAI 응답에 내용이 없습니다.")
            }
            content
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

            // 2) KST(Asia/Seoul) 기준 시간 보정 (필요한 경우만)
            // AI가 UTC 기준으로 반환했을 가능성에 대비
            // 단, 구체적인 시간이 명시된 경우(예: "오후 3시")는 그대로 유지
            if (start != null && end != null) {
                val zone = java.time.ZoneId.of("Asia/Seoul")
                val startZdt = java.time.Instant.ofEpochMilli(start).atZone(zone)
                val endZdt = java.time.Instant.ofEpochMilli(end).atZone(zone)
                
                // 시작 시간과 종료 시간이 같은 날짜이고, 시간이 00:00:00과 23:59:59인 경우에만 하루 전체 범위로 정규화
                // (AI가 "오늘" 같은 표현을 하루 전체로 해석한 경우)
                val isSameDay = startZdt.toLocalDate() == endZdt.toLocalDate()
                val isStartMidnight = startZdt.hour == 0 && startZdt.minute == 0 && startZdt.second == 0
                val isEndEndOfDay = endZdt.hour == 23 && endZdt.minute == 59 && endZdt.second == 59
                
                if (isSameDay && isStartMidnight && isEndEndOfDay) {
                    // 이미 하루 전체 범위로 설정되어 있음, 그대로 유지
                } else if (isSameDay && startZdt.hour == 0 && startZdt.minute == 0 && 
                          endZdt.hour == 0 && endZdt.minute == 0 && endZdt.second == 0) {
                    // 시작과 끝이 모두 자정인 경우, 하루 전체 범위로 확장
                    val normalizedStart = startZdt
                        .withHour(0).withMinute(0).withSecond(0).withNano(0)
                        .toInstant().toEpochMilli()
                    val normalizedEnd = startZdt
                        .withHour(23).withMinute(59).withSecond(59).withNano(999_000_000)
                        .toInstant().toEpochMilli()
                    
                    if (normalizedEnd >= normalizedStart) {
                        start = normalizedStart
                        end = normalizedEnd
                    }
                }
                // 그 외의 경우(구체적인 시간이 명시된 경우)는 그대로 유지
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

    /**
     * "이번주"/"다음주"가 포함된 질문에 대해
     * - 키워드가 없으면 "일정" 추가
     * - 주간 범위가 없거나, 월요일~일요일 등 잘못된 범위가 들어오면
     *   일요일 00:00:00 ~ 토요일 23:59:59 (KST)로 재계산하여 보정한다.
     */
    private fun adjustWeekFiltersIfNeeded(
        question: String,
        currentTimestamp: Long,
        filters: QueryFilters
    ): QueryFilters {
        val lower = question.lowercase()
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val now = java.time.Instant.ofEpochMilli(currentTimestamp).atZone(zone)

        val containsThisWeek = lower.contains("이번주") || lower.contains("이번 주")
        val containsNextWeek = lower.contains("다음주") || lower.contains("다음 주") || lower.contains("담주")
        val needsWeekAdjust = containsThisWeek || containsNextWeek

        // 항상 키워드는 보정
        val keywords = if (filters.keywords.isEmpty()) listOf("일정") else filters.keywords

        if (!needsWeekAdjust) {
            return filters.copy(keywords = keywords)
        }

        // 기대 범위 계산 (일요일 00:00:00 ~ 토요일 23:59:59.999)
        val currentDow = now.dayOfWeek.value // 1=월 ... 7=일
        val daysFromSunday = if (currentDow == 7) 0L else currentDow.toLong()
        val thisWeekSunday = now.minusDays(daysFromSunday)
            .withHour(0).withMinute(0).withSecond(0).withNano(0)

        val targetStartZdt = if (containsNextWeek) thisWeekSunday.plusDays(7) else thisWeekSunday
        val targetEndZdt = targetStartZdt.plusDays(6)
            .withHour(23).withMinute(59).withSecond(59).withNano(999_000_000)

        val targetStartMs = targetStartZdt.toInstant().toEpochMilli()
        val targetEndMs = targetEndZdt.toInstant().toEpochMilli()

        // 현재 필터가 주간 범위를 올바르게 담고 있는지 검사
        val startOk = filters.startTimeMillis?.let {
            val z = java.time.Instant.ofEpochMilli(it).atZone(zone)
            z.dayOfWeek == java.time.DayOfWeek.SUNDAY &&
                    z.hour == 0 && z.minute == 0 && z.second == 0
        } ?: false

        val endOk = filters.endTimeMillis?.let {
            val z = java.time.Instant.ofEpochMilli(it).atZone(zone)
            z.dayOfWeek == java.time.DayOfWeek.SATURDAY &&
                    z.hour == 23 && z.minute == 59 && z.second == 59
        } ?: false

        val rangeOk = startOk && endOk &&
                filters.startTimeMillis != null && filters.endTimeMillis != null &&
                filters.startTimeMillis!! <= filters.endTimeMillis!!

        val isExpectedRange = rangeOk &&
                filters.startTimeMillis == targetStartMs &&
                filters.endTimeMillis == targetEndMs

        val useOverride = !isExpectedRange

        return if (useOverride) {
            android.util.Log.d(
                "HuenDongMinChatGateway",
                "필터 보정 적용 - ${if (containsNextWeek) "다음주" else "이번주"}: $targetStartZdt ~ $targetEndZdt"
            )
            QueryFilters(
                startTimeMillis = targetStartMs,
                endTimeMillis = targetEndMs,
                keywords = keywords,
                source = filters.source,
            )
        } else {
            filters.copy(keywords = keywords)
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

