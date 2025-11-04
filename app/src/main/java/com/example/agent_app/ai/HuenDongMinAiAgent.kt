package com.example.agent_app.ai

import android.content.Context
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
import com.example.agent_app.util.SmsReader
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val context: Context,
    private val eventDao: EventDao,
    private val eventTypeDao: EventTypeDao,
    private val ingestRepository: IngestRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    
    // Few-shot 예시 로더
    private val fewShotLoader = FewShotExampleLoader(context)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC  // BODY → BASIC으로 변경
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
        
        // 실제 현재 시간 (한국시간)
        val now = java.time.Instant.now().atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        // 메일 수신 시간 (한국시간)
        val emailReceivedDate = java.time.Instant.ofEpochMilli(receivedTimestamp)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        // 요일 이름 가져오기 (한글) - 현재 시간 기준
        val dayOfWeekKorean = when (now.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "월요일"
            java.time.DayOfWeek.TUESDAY -> "화요일"
            java.time.DayOfWeek.WEDNESDAY -> "수요일"
            java.time.DayOfWeek.THURSDAY -> "목요일"
            java.time.DayOfWeek.FRIDAY -> "금요일"
            java.time.DayOfWeek.SATURDAY -> "토요일"
            java.time.DayOfWeek.SUNDAY -> "일요일"
        }
        
        val systemPrompt = """
            당신은 사용자의 개인 데이터를 지능적으로 관리하는 AI 비서 "HuenDongMin"입니다.
            
            ⚠️⚠️⚠️ 절대적으로 중요: Gmail 이메일 처리 (한국 표준시 KST, Asia/Seoul, UTC+9) ⚠️⚠️⚠️
            
            📧 메일 수신 정보 (참고용):
            - 메일 수신 연도: ${emailReceivedDate.year}년
            - 메일 수신 월: ${emailReceivedDate.monthValue}월
            - 메일 수신 일: ${emailReceivedDate.dayOfMonth}일
            - 메일 수신 Epoch ms: ${receivedTimestamp}ms
            
            📅 현재 시간 (참고용):
            - 현재 연도: ${now.year}년
            - 현재 월: ${now.monthValue}월
            - 현재 일: ${now.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            - 현재 Epoch ms: ${now.toInstant().toEpochMilli()}ms (한국 시간 기준)
            
            🔴🔴🔴 Gmail 시간 계산 원칙 (명시적 날짜 우선!) 🔴🔴🔴

            **원칙 1: '기준 시점'의 확립**
            
            **1순위: 본문 내 명시적 날짜 (최우선!)**
            - 메일 본문에 "9.30", "10/16", "2025년 10월 16일" 등 명시적 날짜가 있습니까?
            - **그렇다면:** 이 날짜가 **'절대 기준 시점'**입니다. "내일", "다음주", "수요일" 등 모든 상대적 표현은 **이 날짜를 기준으로 계산하세요.**
            - 예: 메일 본문 "10월 16일 14시 회의" → ${now.year}년 10월 16일 14:00 ✅
            - 예: 메일 본문 "10월 16일 ... 다음주 수요일" → 10월 16일 기준 다음주 수요일 = **10월 22일** (✅)

            **2순위: 메일 수신 시간 (명시적 날짜가 없을 때만)**
            - 본문에 명시적 날짜가 없다면, **그때만** 메일 수신 시간(${emailReceivedDate.year}년 ${emailReceivedDate.monthValue}월 ${emailReceivedDate.dayOfMonth}일)을 기준 시점으로 사용하세요.
            - 예: 메일 본문 "내일 오후 3시" → 수신일 기준 다음날 15:00

            ---

            **원칙 2: '명시적 날짜'는 '역사적 사실'이다 (절대 수정 금지!)**
            
            - "9.30", "10.16"처럼 월/일이 명시된 날짜는 **'사실'**을 의미합니다.
            - **⚠️ 최우선 규칙:** 이 날짜가 현재(${now.year}년 ${now.monthValue}월 ${now.dayOfMonth}일)보다 **과거일지라도, 절대로 연도를 수정하거나 다음 해(${now.year + 1}년)로 조정하지 마세요.**
            - 연도가 생략된 모든 명시적 날짜는 **무조건 현재 연도(${now.year})**를 사용합니다.
            
            - ✅ **올바른 예:**
              - 현재 10월 28일, 메일 본문 "9.30(화) 14시 회의"
              - → **${now.year}년 9월 30일 14:00** ✅

            - ❌ **틀린 예 (절대 금지):**
              - 9.30이 과거니까 → ${now.year + 1}년 9월 30일 (AI가 임의로 미래 조정 ❌)

            ---

            **원칙 3: '상대적 표현'은 '순행' 원칙을 따른다**

            - "내일", "모레", "다음주", "다음달" 등은 **'원칙 1'에서 정한 '기준 시점'**을 기준으로 계산합니다.
            
            **"다음주" 계산 알고리즘:**
            1. 기준 시점의 요일 확인 (월요일=1, 화요일=2, ..., 일요일=7)
            2. 기준 주의 월요일 찾기: 기준 시점이 월요일이면 그대로, 아니면 월요일로 역산
            3. 다음 주 월요일 = 기준 주 월요일 + 7일
            4. "다음주 [요일]" = 다음 주 월요일 + (요일번호 - 1)일
            
            **"다음달" 계산 알고리즘:**
            1. 기준 시점의 월/연도 확인
            2. 다음 달 = 기준 시점의 월 + 1 (12월이면 다음 연도 1월)
            3. "다음달 [날짜]" = 다음 달의 해당 날짜
            
            **"N째주" 계산 알고리즘:**
            1. 해당 월의 첫 번째 날 찾기 (예: 다음달 1일)
            2. 첫 번째 날의 요일 확인
            3. 첫 번째 월요일 찾기 (1일이 월요일이면 그대로, 아니면 다음 월요일)
            4. "둘째주 수요일" = 첫 번째 월요일 + (2-1)주 + 2일 = 첫 번째 월요일 + 7일 + 2일 = 첫 번째 월요일 + 9일
            5. 일반 공식: "N째주 [요일]" = 첫 번째 월요일 + (N-1)*7 + (요일번호 - 1)일
            
            **복합 표현: "다음달 둘째주 수요일" 계산:**
            1. 기준 시점의 다음 달 찾기
            2. 다음 달의 첫 번째 월요일 찾기
            3. 둘째주 수요일 = 첫 번째 월요일 + 7일 + 2일 = 첫 번째 월요일 + 9일
            
            **요일 매핑:**
            - 월요일 = 1, 화요일 = 2, 수요일 = 3, 목요일 = 4, 금요일 = 5, 토요일 = 6, 일요일 = 7
        """.trimIndent()
        
        val userPrompt = """
            다음 Gmail 메일을 분석하여 약속/일정이 있는지 확인하고, 있다면 구조화된 JSON으로 반환하세요.
            
            📧 제목: ${emailSubject ?: "(없음)"}
            
            📧 본문:
            ${emailBody ?: ""}
            
            📅 현재 기준 시간:
            - 연도: ${now.year}년
            - 월: ${now.monthValue}월
            - 일: ${now.dayOfMonth}일
            - 요일: $dayOfWeekKorean
            - 현재 Epoch ms: ${now.toInstant().toEpochMilli()}ms
            
            📧 메일 수신 시간:
            - 연도: ${emailReceivedDate.year}년
            - 월: ${emailReceivedDate.monthValue}월
            - 일: ${emailReceivedDate.dayOfMonth}일
            - 메일 Epoch ms: ${receivedTimestamp}ms
            
            🔴🔴🔴 처리 순서 (반드시 이 순서대로 따르세요!) 🔴🔴🔴
            
            **1단계: 명시적 날짜 찾기 (최우선!)**
            
            메일 본문에서 다음 패턴을 찾으세요:
            - "9.30", "10.16" 등 점(.) 구분 → 9월 30일, 10월 16일
            - "9/30", "10/16" 등 슬래시(/) 구분 → 9월 30일, 10월 16일
            - "10월 16일", "9월 30일" 등 한글 → 그대로 인식
            - "2025년 10월 16일" 등 전체 날짜 → 그대로 인식
            - "9.30(화)", "10.16(목)" 등 날짜+요일 → 날짜 우선, 요일은 검증용
            
            **2단계: 기준 시점 결정**
            
            - 1단계에서 명시적 날짜를 **찾았으면**: 그 날짜를 기준 시점으로 사용
            - 1단계에서 명시적 날짜가 **없으면**: 메일 수신 시간(${emailReceivedDate.year}년 ${emailReceivedDate.monthValue}월 ${emailReceivedDate.dayOfMonth}일)을 기준 시점으로 사용
            
            🔍 예시:
            - 메일에 "9.30(화) 14시 회의" → 기준 시점: ${now.year}년 9월 30일 14:00 ✅
            - 메일에 "2025년 10월 16일 오후 3시" → 기준 시점: 2025년 10월 16일 15:00 ✅
            - 메일에 날짜 없고 "내일 오후 3시" → 기준 시점: 메일 수신일 기준 다음날 15:00 ✅
            
            **3단계: 상대적 표현 계산**
            
            "내일", "모레", "다음주", "담주" 등은 **2단계의 기준 시점**을 기준으로 계산
            
            **"다음주" 계산 알고리즘:**
            1. 기준 시점의 요일 확인 (월요일=1, 화요일=2, ..., 일요일=7)
            2. 기준 주의 월요일 찾기:
               - 기준 시점이 월요일이면 그대로 사용
               - 기준 시점이 화요일~일요일이면 월요일로 역산 (화요일=월요일-1일, 수요일=월요일-2일, ...)
            3. 다음 주 월요일 = 기준 주 월요일 + 7일
            4. "다음주 수요일" = 다음 주 월요일 + 2일
            5. "다음주 [요일]" = 다음 주 월요일 + (요일번호 - 1)일
            
            **요일 매핑:**
            - 월요일 = 1, 화요일 = 2, 수요일 = 3, 목요일 = 4, 금요일 = 5, 토요일 = 6, 일요일 = 7
            
            🔍 예시:
            - 기준 시점: 임의의 날짜, 표현: "다음주 수요일" → 다음 주 월요일 + 2일 계산 ✅
            - 기준 시점: 임의의 날짜, 표현: "14시" → 기준 시점의 날짜 14:00 ✅
            - 기준 시점: 현재, 표현: "내일" → 현재 기준 내일 ✅
            
            **4단계: epoch milliseconds 변환**
            
            - 3단계에서 계산한 날짜/시간을 epoch milliseconds로 변환
            - 한국 시간(KST, UTC+9) 기준으로 계산
            
            출력 형식 (순수 JSON만):
            
            ⚠️ 여러 개의 일정이 있으면 배열로 반환하세요!
            
            일정이 1개인 경우:
            {
              "type": "event",
              "confidence": 0.9,
              "events": [
                {
                  "title": "일정 제목",
                  "startAt": 1234567890123,
                  "endAt": 1234567890123,
                  "location": "장소",
                  "type": "이벤트",
                  "body": "메일 내용 요약"
                }
              ]
            }
            
            일정이 여러 개인 경우:
            {
              "type": "event",
              "confidence": 0.9,
              "events": [
                {
                  "title": "첫 번째 일정",
                  "startAt": 1234567890123,
                  "endAt": 1234567890123,
                  "location": "장소1",
                  "type": "회의",
                  "body": "첫 번째 일정 요약"
                },
                {
                  "title": "두 번째 일정",
                  "startAt": 1234567890456,
                  "endAt": 1234567890456,
                  "location": "장소2",
                  "type": "약속",
                  "body": "두 번째 일정 요약"
                }
              ]
            }
            
            일정이 없는 경우:
            {
              "type": "note",
              "confidence": 0.5,
              "events": []
            }
            
            ⚠️⚠️⚠️ 중요 규칙:
            
            **🔴 절대 금지: 일정이 없으면 일정을 생성하지 마세요!**
            - 메일 본문에 명확한 날짜, 시간, 약속, 회의 등이 **전혀 없으면**
            - **절대로 일정(type: "event")을 생성하지 말고**
            - **반드시 type: "note"와 events: []를 반환하세요**
            - 단순 인사, 문의, 알림, 광고 등은 모두 "note"입니다
            - 확실한 약속/일정이 있을 때만 "event"를 생성하세요!
            
            예시:
            - "안녕하세요. 잘 지내시나요?" → type: "note", events: [] ✅
            - "내일 3시에 만나요" → type: "event", events: [...] ✅
            - "9월 30일 회의 있습니다" → type: "event", events: [...] ✅
            - "다음주 수요일 오후 2시 약속" → type: "event", events: [...] ✅
            
            일반 규칙:
            1. 모든 시간은 한국 표준시(KST, UTC+9) 기준으로 계산하세요!
               - epoch milliseconds는 한국 시간으로 변환한 값입니다
               - 예: 2025년 10월 28일 15:00 (한국 시간) = 1761631200000
            
            2. startAt과 endAt은 반드시 계산된 숫자여야 합니다!
               ❌ 나쁜 예: "startAt": 1761050295871 + (7 * 24 * 60 * 60 * 1000)
               ✅ 좋은 예: "startAt": 1761655895871
            
            3. 시간이 명시되지 않은 경우 오전 12시(00:00:00)를 기준으로 하세요!
               - "내일" → 내일 00:00:00
               - "10월 30일" → 10월 30일 00:00:00
               - "다음주 수요일" → 다음주 수요일 00:00:00
            
            4. body는 줄바꿈 없이 한 줄로 작성하세요!
               ❌ 나쁜 예: "body": "첫줄\두번째줄\세번째줄"
               ✅ 좋은 예: "body": "메일 내용 요약 - 회의 일정 공지"
            
            5. 여러 일정이 있으면 반드시 events 배열에 모두 포함하세요!
        """.trimIndent()
        
        val messages = listOf(
            AiMessage(role = "system", content = systemPrompt),
            AiMessage(role = "user", content = userPrompt)
        )
        
        val response = callOpenAi(messages)
        
        android.util.Log.d("HuenDongMinAiAgent", "=== Gmail AI 원본 응답 ===")
        android.util.Log.d("HuenDongMinAiAgent", response)
        android.util.Log.d("HuenDongMinAiAgent", "=====================================")
        
        val result = parseAiResponse(response)
        
        android.util.Log.d("HuenDongMinAiAgent", "=== Gmail AI 응답 분석 ===")
        android.util.Log.d("HuenDongMinAiAgent", "Type: ${result.type}, Confidence: ${result.confidence}")
        android.util.Log.d("HuenDongMinAiAgent", "추출된 이벤트 개수: ${result.events.size}개")
        
        // 모든 Gmail 메시지를 IngestItem으로 저장
        val firstEvent = result.events.firstOrNull()
        val ingestItem = IngestItem(
            id = originalEmailId,
            source = "gmail",
            type = result.type ?: "note",
            title = emailSubject,
            body = emailBody,
            timestamp = receivedTimestamp,
            dueDate = firstEvent?.get("startAt")?.jsonPrimitive?.content?.toLongOrNull(),
            confidence = result.confidence,
            metaJson = null
        )
        ingestRepository.upsert(ingestItem)
        android.util.Log.d("HuenDongMinAiAgent", "Gmail IngestItem 저장 완료 (Type: ${result.type}, Events: ${result.events.size}개)")
        
        // Event 저장 (일정이 있는 경우만)
        if (result.type == "event" && result.events.isNotEmpty()) {
            
            // Event 저장 (여러 개 지원)
            result.events.forEachIndexed { index: Int, eventData: Map<String, JsonElement?> ->
                val originalStartAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
                android.util.Log.d("HuenDongMinAiAgent", "Gmail Event ${index + 1} - AI 추출 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                
                // ⚠️ correctPastDate 제거: AI가 정확하게 날짜를 추출하도록 프롬프트를 강화했으므로
                // AI의 응답을 그대로 신뢰합니다.
                
                // 모든 Event는 같은 IngestItem을 참조 (원본 데이터 추적용)
                val event = createEventFromAiData(eventData, originalEmailId, "gmail")
                eventDao.upsert(event)
                android.util.Log.d("HuenDongMinAiAgent", "Gmail Event ${index + 1} 저장 완료 - ${event.title}, sourceId: $originalEmailId, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
            }
        }
        
        result
    }
    
    /**
     * SMS 카테고리 분류 (프로모션/개인)
     * SmsReader의 분류 로직을 재사용
     */
    private fun classifySmsCategory(address: String, body: String): com.example.agent_app.util.SmsCategory {
        // 발신자 번호 패턴 분석
        val cleanAddress = address.replace("-", "").replace(" ", "").replace("+82", "0")
        
        // 짧은 번호 (4-5자리)는 프로모션 가능성 높음
        val isShortNumber = cleanAddress.length in 4..5 && cleanAddress.all { it.isDigit() }
        
        // 프로모션 키워드 패턴
        val promotionKeywords = listOf(
            "할인", "특가", "이벤트", "프로모션", "쿠폰", "적립", "포인트",
            "무료", "증정", "선착순", "마감", "광고", "알림톡",
            "신청", "가입", "구독", "해지", "문의", "상담",
            "www.", "http://", "https://", ".com", ".kr",
            "안내", "공지", "서비스", "혜택", "추천"
        )
        
        val bodyLower = body.lowercase()
        val hasPromotionKeyword = promotionKeywords.any { keyword ->
            bodyLower.contains(keyword.lowercase())
        }
        
        // 개인 메시지 특징
        val personalKeywords = listOf(
            "안녕", "감사", "고맙", "미안", "죄송", "만나", "약속", "회의",
            "오늘", "내일", "모레", "다음주", "언제", "어디", "뭐"
        )
        val hasPersonalKeyword = personalKeywords.any { keyword ->
            bodyLower.contains(keyword.lowercase())
        }
        
        // 분류 로직
        return when {
            // 짧은 번호 + 프로모션 키워드 → 프로모션
            isShortNumber && hasPromotionKeyword -> com.example.agent_app.util.SmsCategory.PROMOTION
            // 개인 키워드가 있고 프로모션 키워드가 없으면 → 개인
            hasPersonalKeyword && !hasPromotionKeyword -> com.example.agent_app.util.SmsCategory.PERSONAL
            // 짧은 번호만 있으면 → 프로모션 가능성 높음
            isShortNumber -> com.example.agent_app.util.SmsCategory.PROMOTION
            // 프로모션 키워드만 있으면 → 프로모션
            hasPromotionKeyword -> com.example.agent_app.util.SmsCategory.PROMOTION
            // 일반 전화번호 형식이면 → 개인 가능성 높음
            cleanAddress.matches(Regex("^01[0-9]{8,9}$")) -> com.example.agent_app.util.SmsCategory.PERSONAL
            // 기본값
            else -> com.example.agent_app.util.SmsCategory.UNKNOWN
        }
    }
    
    /**
     * SMS 메시지에서 일정 추출 (Tool: processSMSForEvent)
     */
    suspend fun processSMSForEvent(
        smsBody: String,
        smsAddress: String,
        receivedTimestamp: Long,
        originalSmsId: String
    ): AiProcessingResult = withContext(dispatcher) {
        
        android.util.Log.d("HuenDongMinAiAgent", "SMS 처리 시작 - ID: $originalSmsId")
        
        // 실제 현재 시간 (한국시간)
        val now = java.time.Instant.now().atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        // SMS 수신 시간 (한국시간)
        val smsReceivedDate = java.time.Instant.ofEpochMilli(receivedTimestamp)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        // 요일 이름 가져오기 (한글) - 현재 시간 기준
        val dayOfWeekKorean = when (now.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "월요일"
            java.time.DayOfWeek.TUESDAY -> "화요일"
            java.time.DayOfWeek.WEDNESDAY -> "수요일"
            java.time.DayOfWeek.THURSDAY -> "목요일"
            java.time.DayOfWeek.FRIDAY -> "금요일"
            java.time.DayOfWeek.SATURDAY -> "토요일"
            java.time.DayOfWeek.SUNDAY -> "일요일"
        }
        
        val systemPrompt = """
            당신은 사용자의 개인 데이터를 지능적으로 관리하는 AI 비서 "HuenDongMin"입니다.
            
            ⚠️⚠️⚠️ 절대적으로 중요: SMS 수신 시간 기준 (한국 표준시 KST, Asia/Seoul, UTC+9) ⚠️⚠️⚠️
            
            📱 SMS 수신 정보 (모든 시간 계산의 기준 시점):
            - SMS 수신 연도: ${smsReceivedDate.year}년
            - SMS 수신 월: ${smsReceivedDate.monthValue}월
            - SMS 수신 일: ${smsReceivedDate.dayOfMonth}일
            - SMS 수신 요일: ${when (smsReceivedDate.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "월요일"
                java.time.DayOfWeek.TUESDAY -> "화요일"
                java.time.DayOfWeek.WEDNESDAY -> "수요일"
                java.time.DayOfWeek.THURSDAY -> "목요일"
                java.time.DayOfWeek.FRIDAY -> "금요일"
                java.time.DayOfWeek.SATURDAY -> "토요일"
                java.time.DayOfWeek.SUNDAY -> "일요일"
            }}
            - SMS 수신 Epoch ms: ${receivedTimestamp}ms
            - 전체 시간: $smsReceivedDate
            
            📅 현재 시간 (참고용):
            - 현재 연도: ${now.year}년
            - 현재 월: ${now.monthValue}월
            - 현재 일: ${now.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            - 현재 Epoch ms: ${now.toInstant().toEpochMilli()}ms
            
            🔴🔴🔴 SMS 시간 계산 원칙 (명시적 날짜 우선!) 🔴🔴🔴

            **핵심 원칙: 명시적 날짜가 있으면 그 날짜를 기준, 없으면 SMS 수신 시간을 기준으로 계산!**
            
            **1. 명시적 날짜 처리 (최우선!):**
            - SMS 본문에 "9.30", "10/16", "2025년 10월 16일" 등 명시적 날짜가 있으면 **그 날짜를 기준 시점으로 사용**
            - 연도가 생략된 경우 현재 연도(${now.year}) 사용
            - 예: "9.30(화) 14시" → ${now.year}년 9월 30일 14:00
            - 예: "10월 16일 오후 3시" → ${now.year}년 10월 16일 15:00
            
            **2. 상대적 표현 처리:**
            
            **명시적 날짜가 있는 경우:**
            - 명시적 날짜를 기준 시점으로 사용
            - 예: "10월 16일 ... 다음주 수요일" → 10월 16일 기준 다음주 수요일
            
            **명시적 날짜가 없는 경우:**
            - SMS 수신 시간(${smsReceivedDate.year}년 ${smsReceivedDate.monthValue}월 ${smsReceivedDate.dayOfMonth}일)을 기준 시점으로 사용
            - **"내일"**: SMS 수신일 + 1일
            - **"모레"**: SMS 수신일 + 2일
            - **"다음주"**: SMS 수신일 기준 다음 주
              - 예: 수신일이 화요일 → 다음주 화요일 = 수신일 + 7일
            - **"다음주 [요일]"**: 다음 주의 해당 요일
              - 예: 수신일이 화요일, "다음주 수요일" → 수신일 다음 주 수요일
            - **"다음달"**: SMS 수신일의 다음 달 같은 날짜
              - 예: 수신일이 10월 15일 → 다음달 15일 = 11월 15일
            - **"[요일]"**: SMS 수신일 기준 가장 가까운 해당 요일
              - 수신일 이후 같은 요일이 있으면 그 날, 없으면 다음 주 해당 요일
              - 예: 수신일이 화요일, "수요일" → 다음 날 수요일
            
            **요일 매핑:**
            - 월요일 = 1, 화요일 = 2, 수요일 = 3, 목요일 = 4, 금요일 = 5, 토요일 = 6, 일요일 = 7
            
            **3. 시간 처리:**
            - 시간이 명시되지 않으면 오전 12시(00:00:00) 기준
            - "오후 3시", "15시" 등은 그대로 사용
        """.trimIndent()
        
        val userPrompt = """
            다음 SMS 메시지를 분석하여 약속/일정이 있는지 확인하고, 있다면 구조화된 JSON으로 반환하세요.
            
            📱 발신자: $smsAddress
            
            📱 본문:
            $smsBody
            
            📅 SMS 수신 시간 (모든 시간 계산의 기준):
            - 연도: ${smsReceivedDate.year}년
            - 월: ${smsReceivedDate.monthValue}월
            - 일: ${smsReceivedDate.dayOfMonth}일
            - 요일: ${when (smsReceivedDate.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "월요일"
                java.time.DayOfWeek.TUESDAY -> "화요일"
                java.time.DayOfWeek.WEDNESDAY -> "수요일"
                java.time.DayOfWeek.THURSDAY -> "목요일"
                java.time.DayOfWeek.FRIDAY -> "금요일"
                java.time.DayOfWeek.SATURDAY -> "토요일"
                java.time.DayOfWeek.SUNDAY -> "일요일"
            }}
            - SMS 수신 Epoch ms: ${receivedTimestamp}ms
            
            📅 현재 시간 (참고용):
            - 현재 연도: ${now.year}년
            - 현재 월: ${now.monthValue}월
            - 현재 일: ${now.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            
            🔴🔴🔴 SMS 처리 순서 (명시적 날짜 우선!) 🔴🔴🔴
            
            **1단계: 명시적 날짜 찾기 (최우선!)**
            
            SMS 본문에서 다음 패턴을 찾으세요:
            - "9.30", "10.16" 등 점(.) 구분 → 9월 30일, 10월 16일
            - "9/30", "10/16" 등 슬래시(/) 구분 → 9월 30일, 10월 16일
            - "10월 16일", "9월 30일" 등 한글 → 그대로 인식
            - "2025년 10월 16일" 등 전체 날짜 → 그대로 인식
            - "9.30(화)", "10.16(목)" 등 날짜+요일 → 날짜 우선
            
            🔍 예시:
            - SMS에 "9.30(화) 14시 회의" → ${now.year}년 9월 30일 14:00 ✅
            - SMS에 "10월 16일 오후 3시" → ${now.year}년 10월 16일 15:00 ✅
            
            **2단계: 기준 시점 결정**
            
            - 1단계에서 명시적 날짜를 **찾았으면**: 그 날짜를 기준 시점으로 사용
            - 1단계에서 명시적 날짜가 **없으면**: SMS 수신 시간(${smsReceivedDate.year}년 ${smsReceivedDate.monthValue}월 ${smsReceivedDate.dayOfMonth}일)을 기준 시점으로 사용
            
            🔍 예시:
            - SMS에 "10월 16일 ... 다음주 수요일" → 10월 16일 기준 다음주 수요일 ✅
            - SMS에 날짜 없고 "내일 오후 3시" → SMS 수신일 기준 다음날 15:00 ✅
            
            **3단계: 상대적 표현 처리 (2단계의 기준 시점을 기준으로 계산)**
            
            명시적 날짜가 있으면 그 날짜를 기준으로, 없으면 **SMS 수신 시간**을 기준으로 계산:
            
            - **"내일"**: ${smsReceivedDate.year}년 ${smsReceivedDate.monthValue}월 ${smsReceivedDate.dayOfMonth}일 + 1일
            - **"모레"**: ${smsReceivedDate.year}년 ${smsReceivedDate.monthValue}월 ${smsReceivedDate.dayOfMonth}일 + 2일
            - **"다음주"**: SMS 수신일 기준 다음 주 (수신일이 ${when (smsReceivedDate.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "월요일"
                java.time.DayOfWeek.TUESDAY -> "화요일"
                java.time.DayOfWeek.WEDNESDAY -> "수요일"
                java.time.DayOfWeek.THURSDAY -> "목요일"
                java.time.DayOfWeek.FRIDAY -> "금요일"
                java.time.DayOfWeek.SATURDAY -> "토요일"
                java.time.DayOfWeek.SUNDAY -> "일요일"
            }}이면 다음 주 ${when (smsReceivedDate.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "월요일"
                java.time.DayOfWeek.TUESDAY -> "화요일"
                java.time.DayOfWeek.WEDNESDAY -> "수요일"
                java.time.DayOfWeek.THURSDAY -> "목요일"
                java.time.DayOfWeek.FRIDAY -> "금요일"
                java.time.DayOfWeek.SATURDAY -> "토요일"
                java.time.DayOfWeek.SUNDAY -> "일요일"
            }} = 수신일 + 7일)
            - **"다음주 [요일]"**: SMS 수신일 기준 다음 주의 해당 요일
            - **"[요일]"**: SMS 수신일 이후 가장 가까운 해당 요일
              - 수신일이 ${when (smsReceivedDate.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "월요일"
                java.time.DayOfWeek.TUESDAY -> "화요일"
                java.time.DayOfWeek.WEDNESDAY -> "수요일"
                java.time.DayOfWeek.THURSDAY -> "목요일"
                java.time.DayOfWeek.FRIDAY -> "금요일"
                java.time.DayOfWeek.SATURDAY -> "토요일"
                java.time.DayOfWeek.SUNDAY -> "일요일"
            }}이고 "수요일"이면 → 다음 날 수요일 (수신일 + 1일)
              - 수신일이 ${when (smsReceivedDate.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "월요일"
                java.time.DayOfWeek.TUESDAY -> "화요일"
                java.time.DayOfWeek.WEDNESDAY -> "수요일"
                java.time.DayOfWeek.THURSDAY -> "목요일"
                java.time.DayOfWeek.FRIDAY -> "금요일"
                java.time.DayOfWeek.SATURDAY -> "토요일"
                java.time.DayOfWeek.SUNDAY -> "일요일"
            }}이고 "월요일"이면 → 다음 주 월요일 (수신일 + ${when (smsReceivedDate.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> 7
                java.time.DayOfWeek.TUESDAY -> 6
                java.time.DayOfWeek.WEDNESDAY -> 5
                java.time.DayOfWeek.THURSDAY -> 4
                java.time.DayOfWeek.FRIDAY -> 3
                java.time.DayOfWeek.SATURDAY -> 2
                java.time.DayOfWeek.SUNDAY -> 1
            }}일)
            
            🔍 예시:
            - SMS 수신일: ${smsReceivedDate.year}년 ${smsReceivedDate.monthValue}월 ${smsReceivedDate.dayOfMonth}일, 본문: "내일 오후 3시" → ${smsReceivedDate.plusDays(1).year}년 ${smsReceivedDate.plusDays(1).monthValue}월 ${smsReceivedDate.plusDays(1).dayOfMonth}일 15:00 ✅
            - SMS 수신일: ${smsReceivedDate.year}년 ${smsReceivedDate.monthValue}월 ${smsReceivedDate.dayOfMonth}일, 본문: "다음주 수요일" → 다음 주 수요일 계산 ✅
            
            **3단계: epoch milliseconds 변환**
            
            - 계산한 날짜/시간을 epoch milliseconds로 변환
            - 한국 시간(KST, UTC+9) 기준으로 계산
            
            출력 형식 (순수 JSON만):
            
            ⚠️ 여러 개의 일정이 있으면 배열로 반환하세요!
            
            일정이 1개인 경우:
            {
              "type": "event",
              "confidence": 0.9,
              "events": [
                {
                  "title": "일정 제목",
                  "startAt": 1234567890123,
                  "endAt": 1234567890123,
                  "location": "장소",
                  "type": "이벤트",
                  "body": "SMS 내용 요약"
                }
              ]
            }
            
            일정이 여러 개인 경우:
            {
              "type": "event",
              "confidence": 0.9,
              "events": [
                {
                  "title": "첫 번째 일정",
                  "startAt": 1234567890123,
                  "endAt": 1234567890123,
                  "location": "장소1",
                  "type": "회의",
                  "body": "첫 번째 일정 요약"
                },
                {
                  "title": "두 번째 일정",
                  "startAt": 1234567890456,
                  "endAt": 1234567890456,
                  "location": "장소2",
                  "type": "약속",
                  "body": "두 번째 일정 요약"
                }
              ]
            }
            
            일정이 없는 경우:
            {
              "type": "note",
              "confidence": 0.5,
              "events": []
            }
            
            ⚠️⚠️⚠️ 중요 규칙:
            
            **🔴 절대 금지: 일정이 없으면 일정을 생성하지 마세요!**
            - SMS 본문에 명확한 날짜, 시간, 약속, 회의 등이 **전혀 없으면**
            - **절대로 일정(type: "event")을 생성하지 말고**
            - **반드시 type: "note"와 events: []를 반환하세요**
            - 단순 인사, 문의, 알림, 광고 등은 모두 "note"입니다
            - 확실한 약속/일정이 있을 때만 "event"를 생성하세요!
            
            예시:
            - "안녕하세요. 잘 지내시나요?" → type: "note", events: [] ✅
            - "내일 3시에 만나요" → type: "event", events: [...] ✅
            - "9월 30일 회의 있습니다" → type: "event", events: [...] ✅
            - "다음주 수요일 오후 2시 약속" → type: "event", events: [...] ✅
            
            일반 규칙:
            1. 모든 시간은 한국 표준시(KST, UTC+9) 기준으로 계산하세요!
               - epoch milliseconds는 한국 시간으로 변환한 값입니다
               - 예: 2025년 10월 28일 15:00 (한국 시간) = 1761631200000
            
            2. startAt과 endAt은 반드시 계산된 숫자여야 합니다!
               ❌ 나쁜 예: "startAt": 1761050295871 + (7 * 24 * 60 * 60 * 1000)
               ✅ 좋은 예: "startAt": 1761655895871
            
            3. 시간이 명시되지 않은 경우 오전 12시(00:00:00)를 기준으로 하세요!
               - "내일" → 내일 00:00:00
               - "10월 30일" → 10월 30일 00:00:00
               - "다음주 수요일" → 다음주 수요일 00:00:00
            
            4. body는 줄바꿈 없이 한 줄로 작성하세요!
               ❌ 나쁜 예: "body": "첫줄\두번째줄\세번째줄"
               ✅ 좋은 예: "body": "SMS 내용 요약 - 회의 일정 공지"
            
            5. 여러 일정이 있으면 반드시 events 배열에 모두 포함하세요!
        """.trimIndent()
        
        val messages = listOf(
            AiMessage(role = "system", content = systemPrompt),
            AiMessage(role = "user", content = userPrompt)
        )
        
        val response = callOpenAi(messages)
        
        android.util.Log.d("HuenDongMinAiAgent", "=== SMS AI 원본 응답 ===")
        android.util.Log.d("HuenDongMinAiAgent", response)
        android.util.Log.d("HuenDongMinAiAgent", "=====================================")
        
        val result = parseAiResponse(response)
        
        android.util.Log.d("HuenDongMinAiAgent", "=== SMS AI 응답 분석 ===")
        android.util.Log.d("HuenDongMinAiAgent", "Type: ${result.type}, Confidence: ${result.confidence}")
        android.util.Log.d("HuenDongMinAiAgent", "추출된 이벤트 개수: ${result.events.size}개")
        
        // 모든 SMS 메시지를 IngestItem으로 저장 (일정이 없어도 저장)
        val firstEvent = result.events.firstOrNull()
        
        // SMS 카테고리 정보 추출 (SmsMessage에서 전달받음)
        // smsAddress에서 카테고리 정보를 추출하기 위해 SmsReader의 분류 함수를 재사용
        val smsCategory = classifySmsCategory(smsAddress, smsBody)
        
        val metaJson = buildString {
            append("{")
            append("\"category\":\"${smsCategory.name}\",")
            append("\"address\":\"$smsAddress\"")
            if (result.type == "event" && firstEvent != null) {
                append(",\"event\":true")
            }
            append("}")
        }
        
        val ingestItem = IngestItem(
            id = originalSmsId,
            source = "sms",
            type = result.type ?: "note",
            title = smsAddress,
            body = smsBody,
            timestamp = receivedTimestamp,
            dueDate = firstEvent?.get("startAt")?.jsonPrimitive?.content?.toLongOrNull(),
            confidence = result.confidence,
            metaJson = metaJson
        )
        ingestRepository.upsert(ingestItem)
        android.util.Log.d("HuenDongMinAiAgent", "SMS IngestItem 저장 완료 (Type: ${result.type}, Category: $smsCategory)")
        
        // Event 저장 (일정이 있는 경우만)
        if (result.type == "event" && result.events.isNotEmpty()) {
            
            // Event 저장 (여러 개 지원)
            result.events.forEachIndexed { index: Int, eventData: Map<String, JsonElement?> ->
                val originalStartAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
                android.util.Log.d("HuenDongMinAiAgent", "SMS Event ${index + 1} - AI 추출 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                
                // 모든 Event는 같은 IngestItem을 참조 (원본 데이터 추적용)
                val event = createEventFromAiData(eventData, originalSmsId, "sms")
                eventDao.upsert(event)
                android.util.Log.d("HuenDongMinAiAgent", "SMS Event ${index + 1} 저장 완료 - ${event.title}, sourceId: $originalSmsId, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
            }
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
        
        // 실제 현재 시간 (한국시간)
        val now = java.time.Instant.now().atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        // OCR 처리 시간 (한국시간)
        val ocrProcessedDate = java.time.Instant.ofEpochMilli(currentTimestamp)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        // 요일 이름 가져오기 (한글) - 현재 시간 기준
        val dayOfWeekKorean = when (now.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "월요일"
            java.time.DayOfWeek.TUESDAY -> "화요일"
            java.time.DayOfWeek.WEDNESDAY -> "수요일"
            java.time.DayOfWeek.THURSDAY -> "목요일"
            java.time.DayOfWeek.FRIDAY -> "금요일"
            java.time.DayOfWeek.SATURDAY -> "토요일"
            java.time.DayOfWeek.SUNDAY -> "일요일"
        }
        
        android.util.Log.d("HuenDongMinAiAgent", "📱 OCR 처리 시간(ms): $currentTimestamp")
        android.util.Log.d("HuenDongMinAiAgent", "📅 현재 날짜: ${now.year}년 ${now.monthValue}월 ${now.dayOfMonth}일 $dayOfWeekKorean")
        android.util.Log.d("HuenDongMinAiAgent", "📅 OCR 처리 날짜: ${ocrProcessedDate.year}년 ${ocrProcessedDate.monthValue}월 ${ocrProcessedDate.dayOfMonth}일")
        android.util.Log.d("HuenDongMinAiAgent", "⚠️ AI에게 전달: ${now.year}년 ${now.monthValue}월을 기준으로 해석하라고 명령!")
        android.util.Log.d("HuenDongMinAiAgent", "🕐 전체 현재 시간 정보: $now")
        
        val systemPrompt = """
            당신은 이미지(OCR)에서 일정을 추출하는 AI 비서 "HuenDongMin"입니다.
            
            ⚠️⚠️⚠️ 절대적으로 중요: OCR은 이미지 촬영 시점 (현재 시간 기준) ⚠️⚠️⚠️
            
            📅 현재 시간 (이미지 촬영 시점):
            - 현재 연도: ${now.year}년
            - 현재 월: ${now.monthValue}월
            - 현재 일: ${now.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            - 현재 Epoch ms: ${now.toInstant().toEpochMilli()}ms (한국 시간 기준)
            
            📅 OCR 처리 시간 (참고용):
            - OCR 처리 연도: ${ocrProcessedDate.year}년
            - OCR 처리 월: ${ocrProcessedDate.monthValue}월
            - OCR 처리 일: ${ocrProcessedDate.dayOfMonth}일
            - OCR 처리 Epoch ms: ${currentTimestamp}ms
            
            🔴🔴🔴 OCR 시간 계산 원칙 (OCR은 명시적 날짜 중심!) 🔴🔴🔴

            **핵심 원칙: OCR은 이미지에서 텍스트를 추출한 것이므로, 명시적 날짜만 사용합니다!**
            
            **1. 명시적 날짜 처리 (최우선!):**
            - OCR 텍스트에 "2025,10,30.(목)", "10월 30일", "10.30" 등 명시적 날짜가 있으면 그 날짜를 그대로 사용
            - 연도가 생략된 경우 현재 연도(${now.year}) 사용
            - 예: "10월 30일" → ${now.year}년 10월 30일
            - 예: "2025,10,30.(목)" → 2025년 10월 30일 목요일
            
            **2. 상대적 표현 처리:**
            - OCR에는 일반적으로 "내일", "다음주" 같은 상대적 표현이 거의 없음
            - 만약 상대적 표현이 있다면, **현재 시간(${now.year}년 ${now.monthValue}월 ${now.dayOfMonth}일)**을 기준으로 계산
            - 예: "내일" → ${now.plusDays(1).year}년 ${now.plusDays(1).monthValue}월 ${now.plusDays(1).dayOfMonth}일
            
            **3. 시간 처리:**
            - 시간이 명시되지 않으면 오전 12시(00:00:00) 기준
            - "11:30" → 11:30:00 KST
            - "14시" → 14:00:00 KST
            - "오후 3시" → 15:00:00 KST
            
            ⚠️ **절대 금지:**
            - 명시적 날짜를 수정하거나 변경 ❌
            - "10월 30일"을 "10월 29일"로 변경 ❌
            - 명시적 날짜를 상대적 표현으로 해석 ❌
            - OCR 텍스트에 없는 날짜를 임의로 추가 ❌
        """.trimIndent()
        
        // Few-shot 예시 (하드코딩 - 리소스 로딩 문제 우회)
        val fewShotExamples = """
            
            🎯 **실제 예시:**
            
            **예시 1: 명시적 날짜 (매우 중요!)**
            OCR: "2025,10,30.(목) 11:30"
            
            **처리 과정:**
            1. "2025,10,30.(목)" 발견 → 명시적 날짜: **2025년 10월 30일 목요일** ✅
            2. "11:30" 발견 → 시간: 11:30:00
            3. Epoch 계산: 2025년 10월 30일 11:30:00 KST = 1761631200000 ✅
            
            **결과:**
            ```json
            {
              "type": "event",
              "confidence": 0.9,
              "events": [{
                "title": "일정",
                "startAt": 1761631200000,
                "endAt": 1761631200000,
                "location": "",
                "type": "회의",
                "body": "2025년 10월 30일 목요일 11:30 일정"
              }]
            }
            ```
            
            ⚠️ **절대 금지:**
            - ❌ "2025,10,30"을 "2025,10,29"로 변경
            - ❌ "10월 30일"을 "10월 29일"로 해석
            - ❌ 명시적 날짜를 상대적으로 계산
            
            **예시 2: 한글 날짜**
            OCR: "10월 30일 14시 회의"
            
            **처리 과정:**
            1. "10월 30일" 발견 → 명시적 날짜: **${now.year}년 10월 30일** ✅
            2. "14시" 발견 → 시간: 14:00:00
            3. Epoch 계산: ${now.year}년 10월 30일 14:00:00 KST = 1761681600000 ✅
            
            **결과:**
            ```json
            {
              "type": "event",
              "confidence": 0.9,
              "events": [{
                "title": "회의",
                "startAt": 1761681600000,
                "endAt": 1761681600000,
                "location": "",
                "type": "회의",
                "body": "10월 30일 14시 회의"
              }]
            }
            ```
        """.trimIndent()
        
        val fullSystemPrompt = systemPrompt + fewShotExamples
        
        android.util.Log.d("HuenDongMinAiAgent", "📊 기본 System Prompt 길이: ${systemPrompt.length}자")
        android.util.Log.d("HuenDongMinAiAgent", "📊 Few-shot 추가 길이: ${fewShotExamples.length}자")
        android.util.Log.d("HuenDongMinAiAgent", "📊 최종 System Prompt 길이: ${fullSystemPrompt.length}자")
        
        val userPrompt = """
            다음 OCR 텍스트에서 일정 정보를 추출하세요.
            
            📱 OCR 텍스트:
            ${ocrText}
            
            📅 현재 시간 (이미지 촬영 시점):
            - 연도: ${now.year}년
            - 월: ${now.monthValue}월
            - 일: ${now.dayOfMonth}일
            - 요일: $dayOfWeekKorean
            - 현재 Epoch ms: ${now.toInstant().toEpochMilli()}ms
            
            🔴🔴🔴 OCR 처리 순서 (OCR은 명시적 날짜 중심!) 🔴🔴🔴
            
            **1단계: 명시적 날짜 찾기 (최우선!)**
            
            OCR 텍스트에서 다음 패턴을 찾으세요:
            - "2025,10,30.(목)" → 2025년 10월 30일 목요일
            - "10월 30일" → ${now.year}년 10월 30일
            - "10.30" → ${now.year}년 10월 30일
            - "9/30" → ${now.year}년 9월 30일
            - "2025년 10월 30일" → 2025년 10월 30일
            
            🔍 예시:
            - OCR에 "10월 30일 14시" → ${now.year}년 10월 30일 14:00 ✅
            - OCR에 "2025,10,30.(목) 11:30" → 2025년 10월 30일 11:30 ✅
            
            **2단계: 상대적 표현 처리 (거의 없지만, 있다면 현재 시간 기준)**
            
            OCR에 상대적 표현("내일", "다음주" 등)이 있다면, **현재 시간(${now.year}년 ${now.monthValue}월 ${now.dayOfMonth}일)**을 기준으로 계산:
            - "내일" → ${now.plusDays(1).year}년 ${now.plusDays(1).monthValue}월 ${now.plusDays(1).dayOfMonth}일
            - "다음주 수요일" → 현재 시간 기준 다음 주 수요일
            
            ⚠️ **매우 중요:**
            - 명시적 날짜는 절대 수정하지 마세요!
            - "10월 30일"을 "10월 29일"로 변경하지 마세요!
            - "2025,10,30"을 다른 날짜로 해석하지 마세요!
            
            **3단계: 시간 찾기**
            
            OCR 텍스트에서 시간을 찾으세요:
            - "11:30" → 11:30:00
            - "14시" → 14:00:00
            - "오후 3시" → 15:00:00
            - 시간이 없으면 00:00:00 사용
            
            **4단계: epoch milliseconds 변환**
            
            - 계산한 날짜/시간을 epoch milliseconds로 변환
            - 한국 시간(KST, UTC+9) 기준으로 계산
            
            출력 형식 (JSON만):
            {
              "type": "event",
              "confidence": 0.9,
              "events": [
                {
                  "title": "일정 제목",
                  "startAt": 1761631200000,
                  "endAt": 1761631200000,
                  "location": "장소",
                  "type": "회의",
                  "body": "OCR 텍스트 요약"
                }
              ]
            }
        """.trimIndent()
        
        android.util.Log.d("HuenDongMinAiAgent", "=== AI에게 전송할 프롬프트 ===")
        android.util.Log.d("HuenDongMinAiAgent", "System Prompt (일부):")
        android.util.Log.d("HuenDongMinAiAgent", fullSystemPrompt.take(500))
        android.util.Log.d("HuenDongMinAiAgent", "User Prompt (일부):")
        android.util.Log.d("HuenDongMinAiAgent", userPrompt.take(500))
        android.util.Log.d("HuenDongMinAiAgent", "=====================================")
        
        val messages = listOf(
            AiMessage(role = "system", content = fullSystemPrompt),
            AiMessage(role = "user", content = userPrompt)
        )
        
        android.util.Log.d("HuenDongMinAiAgent", "🚀 callOpenAi 호출 직전")
        android.util.Log.d("HuenDongMinAiAgent", "📊 Messages 개수: ${messages.size}")
        android.util.Log.d("HuenDongMinAiAgent", "📊 System Prompt 길이: ${messages[0].content.length}자")
        android.util.Log.d("HuenDongMinAiAgent", "📊 User Prompt 길이: ${messages[1].content.length}자")
        
        val response = try {
            callOpenAi(messages)
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinAiAgent", "❌ callOpenAi 실패!", e)
            throw e
        }
        
        android.util.Log.d("HuenDongMinAiAgent", "=== OCR AI 원본 응답 ===")
        android.util.Log.d("HuenDongMinAiAgent", response)
        android.util.Log.d("HuenDongMinAiAgent", "=====================================")
        
        val result = parseAiResponse(response)
        
        android.util.Log.d("HuenDongMinAiAgent", "=== OCR AI 응답 분석 ===")
        android.util.Log.d("HuenDongMinAiAgent", "Type: ${result.type}, Confidence: ${result.confidence}")
        android.util.Log.d("HuenDongMinAiAgent", "추출된 이벤트 개수: ${result.events.size}개")
        
        // Event 저장 (일정인 경우만 IngestItem과 Event 저장)
        if (result.type == "event" && result.events.isNotEmpty()) {
            // 일정이 있는 경우에만 IngestItem 저장 (원본 보관, 첫 번째 이벤트 정보 사용)
            val firstEvent = result.events.firstOrNull()
            val ingestItem = IngestItem(
                id = originalOcrId,
                source = "ocr",
                type = result.type,
                title = firstEvent?.get("title")?.jsonPrimitive?.content,
                body = ocrText,
                timestamp = currentTimestamp,
                dueDate = firstEvent?.get("startAt")?.jsonPrimitive?.content?.toLongOrNull(),
                confidence = result.confidence,
                metaJson = null
            )
            ingestRepository.upsert(ingestItem)
            android.util.Log.d("HuenDongMinAiAgent", "OCR IngestItem 저장 완료 (일정 있음)")
            
            // Event 저장 (여러 개 지원)
            result.events.forEachIndexed { index: Int, eventData: Map<String, JsonElement?> ->
                val originalStartAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
                android.util.Log.d("HuenDongMinAiAgent", "OCR Event ${index + 1} - AI 추출 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                
                // 🔍 AI 응답 검증 및 수정
                val correctedEventData = validateAndCorrectAiResponse(eventData, ocrText, now)
                
                // 모든 Event는 같은 IngestItem을 참조 (원본 데이터 추적용)
                val event = createEventFromAiData(correctedEventData, originalOcrId, "ocr")
                eventDao.upsert(event)
                android.util.Log.d("HuenDongMinAiAgent", "OCR Event ${index + 1} 저장 완료 - ${event.title}, sourceId: $originalOcrId, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
            }
        }
        
        result
    }
    
    /**
     * AI 응답 검증 및 수정
     * OCR 텍스트에서 명시적 날짜를 찾아서 AI 응답과 비교하고 수정
     */
    private fun validateAndCorrectAiResponse(
        eventData: Map<String, JsonElement?>,
        ocrText: String,
        currentTime: java.time.ZonedDateTime
    ): Map<String, JsonElement?> {
        
        android.util.Log.d("HuenDongMinAiAgent", "🔍 AI 응답 검증 시작")
        
        // OCR 텍스트에서 명시적 날짜 패턴 찾기
        val explicitDatePatterns = listOf(
            // "2025.10.30.(목)" 패턴
            """(\d{4})\.(\d{1,2})\.(\d{1,2})\.\([월화수목금토일]\)""".toRegex(),
            // "2025,10,30.(목)" 패턴  
            """(\d{4}),(\d{1,2}),(\d{1,2})\.\([월화수목금토일]\)""".toRegex(),
            // "10월 30일" 패턴
            """(\d{1,2})월\s*(\d{1,2})일""".toRegex(),
            // "10.30" 패턴
            """(\d{1,2})\.(\d{1,2})""".toRegex()
        )
        
        var foundExplicitDate: Triple<Int, Int, Int>? = null
        
        for (pattern in explicitDatePatterns) {
            val match = pattern.find(ocrText)
            if (match != null) {
                val groups = match.groupValues
                when (pattern) {
                    explicitDatePatterns[0], explicitDatePatterns[1] -> {
                        // "2025.10.30.(목)" 또는 "2025,10,30.(목)" 패턴
                        val year = groups[1].toInt()
                        val month = groups[2].toInt()
                        val day = groups[3].toInt()
                        foundExplicitDate = Triple(year, month, day)
                        android.util.Log.d("HuenDongMinAiAgent", "✅ 명시적 날짜 발견: ${year}년 ${month}월 ${day}일")
                        break
                    }
                    explicitDatePatterns[2] -> {
                        // "10월 30일" 패턴
                        val month = groups[1].toInt()
                        val day = groups[2].toInt()
                        foundExplicitDate = Triple(currentTime.year, month, day)
                        android.util.Log.d("HuenDongMinAiAgent", "✅ 명시적 날짜 발견: ${currentTime.year}년 ${month}월 ${day}일")
                        break
                    }
                    explicitDatePatterns[3] -> {
                        // "10.30" 패턴
                        val month = groups[1].toInt()
                        val day = groups[2].toInt()
                        foundExplicitDate = Triple(currentTime.year, month, day)
                        android.util.Log.d("HuenDongMinAiAgent", "✅ 명시적 날짜 발견: ${currentTime.year}년 ${month}월 ${day}일")
                        break
                    }
                }
            }
        }
        
        if (foundExplicitDate == null) {
            android.util.Log.d("HuenDongMinAiAgent", "⚠️ 명시적 날짜를 찾을 수 없음, AI 응답 그대로 사용")
            return eventData
        }
        
        val (targetYear, targetMonth, targetDay) = foundExplicitDate
        
        // AI가 추출한 시간 확인
        val aiStartAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
        if (aiStartAt == null) {
            android.util.Log.d("HuenDongMinAiAgent", "⚠️ AI가 startAt을 추출하지 못함")
            return eventData
        }
        
        val aiDate = java.time.Instant.ofEpochMilli(aiStartAt!!)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        android.util.Log.d("HuenDongMinAiAgent", "🔍 AI 추출 날짜: ${aiDate.year}년 ${aiDate.monthValue}월 ${aiDate.dayOfMonth}일")
        android.util.Log.d("HuenDongMinAiAgent", "🎯 명시적 날짜: ${targetYear}년 ${targetMonth}월 ${targetDay}일")
        
        // 날짜가 다르면 수정
        if (aiDate.year != targetYear || aiDate.monthValue != targetMonth || aiDate.dayOfMonth != targetDay) {
            android.util.Log.d("HuenDongMinAiAgent", "❌ 날짜 불일치 감지! AI 응답 수정 중...")
            
            // 시간은 AI가 추출한 것을 유지하고, 날짜만 수정
            val correctedDate = aiDate.withYear(targetYear).withMonth(targetMonth).withDayOfMonth(targetDay)
            val correctedStartAt = correctedDate.toInstant().toEpochMilli()
            
            android.util.Log.d("HuenDongMinAiAgent", "✅ 날짜 수정 완료: ${correctedDate.year}년 ${correctedDate.monthValue}월 ${correctedDate.dayOfMonth}일 ${correctedDate.hour}:${correctedDate.minute}")
            
            // endAt도 수정 (있다면)
            val correctedEndAt = eventData["endAt"]?.jsonPrimitive?.content?.toLongOrNull()?.let { endAt ->
                val endDate = java.time.Instant.ofEpochMilli(endAt)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                val correctedEndDate = endDate.withYear(targetYear).withMonth(targetMonth).withDayOfMonth(targetDay)
                correctedEndDate.toInstant().toEpochMilli()
            }
            
            return eventData.toMutableMap().apply {
                this["startAt"] = JsonPrimitive(correctedStartAt.toString())
                if (correctedEndAt != null) {
                    this["endAt"] = JsonPrimitive(correctedEndAt.toString())
                }
            }
        }
        
        android.util.Log.d("HuenDongMinAiAgent", "✅ 날짜 일치, 수정 불필요")
        return eventData
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
            startAt = extractedData["startAt"]?.jsonPrimitive?.content?.toLongOrNull(),
            endAt = extractedData["endAt"]?.jsonPrimitive?.content?.toLongOrNull(),
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
        android.util.Log.d("HuenDongMinAiAgent", "📡 callOpenAi 시작")
        
        try {
            val apiKey = BuildConfig.OPENAI_API_KEY
            android.util.Log.d("HuenDongMinAiAgent", "🔑 API Key 확인: ${if (apiKey.isNotBlank()) "존재 (${apiKey.length}자)" else "없음!"}")
            require(apiKey.isNotBlank()) { "OpenAI API 키가 설정되지 않았습니다." }
            
            android.util.Log.d("HuenDongMinAiAgent", "📦 요청 객체 생성 시작 (messages 개수: ${messages.size})")
            android.util.Log.d("HuenDongMinAiAgent", "  - model: gpt-4o-mini")
            android.util.Log.d("HuenDongMinAiAgent", "  - temperature: 0.3")
            android.util.Log.d("HuenDongMinAiAgent", "  - maxTokens: 1000")
            
            // 1. 안전한 JSON 문자열 생성
            fun String.escapeJson(): String = this
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            
            val systemContent = messages[0].content.escapeJson()
            val userContent = messages[1].content.escapeJson()
            
            val jsonString = """
                {
                  "model": "gpt-4o-mini",
                  "messages": [
                    {
                      "role": "system",
                      "content": "$systemContent"
                    },
                    {
                      "role": "user",
                      "content": "$userContent"
                    }
                  ],
                  "temperature": 0.3,
                  "max_tokens": 1000
                }
            """.trimIndent()
            android.util.Log.d("HuenDongMinAiAgent", "✅ JSON 생성 완료 (${jsonString.length}자)")
            android.util.Log.d("HuenDongMinAiAgent", "📄 생성된 JSON 미리보기: ${jsonString.take(200)}...")
            
            val requestBody = jsonString.toRequestBody("application/json".toMediaType())
            android.util.Log.d("HuenDongMinAiAgent", "✅ RequestBody 생성 완료")
            
            android.util.Log.d("HuenDongMinAiAgent", "🌐 HTTP 요청 생성")
            val httpRequest = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            android.util.Log.d("HuenDongMinAiAgent", "✅ HTTP 요청 객체 생성 완료")
            
            android.util.Log.d("HuenDongMinAiAgent", "📤 HTTP 요청 전송 중...")
            android.util.Log.d("HuenDongMinAiAgent", "🌐 요청 URL: ${httpRequest.url}")
            android.util.Log.d("HuenDongMinAiAgent", "🔑 Authorization 헤더: ${httpRequest.header("Authorization")?.take(20)}...")
            android.util.Log.d("HuenDongMinAiAgent", "⏰ 타임아웃 설정: 연결 5초, 읽기 10초")
            try {
                android.util.Log.d("HuenDongMinAiAgent", "🔄 execute() 호출 직전...")
                client.newCall(httpRequest).execute().use { response ->
                android.util.Log.d("HuenDongMinAiAgent", "📥 응답 수신: ${response.code}")
                
                val responseBody = response.body?.string()
                android.util.Log.d("HuenDongMinAiAgent", "📄 응답 본문 길이: ${responseBody?.length ?: 0}자")
                
                if (responseBody == null) {
                    throw Exception("Empty response from OpenAI")
                }
                
                if (!response.isSuccessful) {
                    android.util.Log.e("HuenDongMinAiAgent", "❌ API 오류: ${response.code}")
                    android.util.Log.e("HuenDongMinAiAgent", "응답 내용: ${responseBody.take(500)}")
                    throw Exception("OpenAI API 오류: ${response.code} - ${responseBody.take(200)}")
                }
                
                // 정규식으로 응답 파싱 (임시 해결책)
                android.util.Log.d("HuenDongMinAiAgent", "🔄 응답 파싱 중 (정규식 사용)...")
                val contentRegex = """"content"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
                val matchResult = contentRegex.find(responseBody)
                
                val content = if (matchResult != null) {
                    matchResult.groupValues[1]
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                } else {
                    null
                }
                
                if (content.isNullOrBlank()) {
                    android.util.Log.e("HuenDongMinAiAgent", "❌ content가 비어있습니다.")
                    throw Exception("OpenAI 응답에서 content를 찾을 수 없습니다")
                }
                
                android.util.Log.d("HuenDongMinAiAgent", "✅ AI 응답 성공 (${content.length}자)")
                android.util.Log.d("HuenDongMinAiAgent", "  응답 내용 미리보기: ${content.take(100)}")
                
                content
                }
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("HuenDongMinAiAgent", "⏰ 네트워크 타임아웃 발생!", e)
                throw Exception("API 요청 시간 초과: ${e.message}")
            } catch (e: java.net.UnknownHostException) {
                android.util.Log.e("HuenDongMinAiAgent", "🌐 네트워크 연결 실패!", e)
                throw Exception("인터넷 연결을 확인해주세요: ${e.message}")
            } catch (e: java.io.IOException) {
                android.util.Log.e("HuenDongMinAiAgent", "📡 네트워크 I/O 오류!", e)
                throw Exception("네트워크 오류: ${e.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinAiAgent", "❌❌❌ callOpenAi에서 예외 발생! ❌❌❌", e)
            android.util.Log.e("HuenDongMinAiAgent", "예외 타입: ${e.javaClass.simpleName}")
            android.util.Log.e("HuenDongMinAiAgent", "예외 메시지: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    /**
     * AI 응답 파싱 (여러 이벤트 지원)
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
            
            // events 배열 파싱
            val events = try {
                jsonObj["events"]?.jsonArray?.map { eventElement ->
                    eventElement.jsonObject.toMap()
                } ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.w("HuenDongMinAiAgent", "events 배열 파싱 실패, 구버전 형식 시도", e)
                // 구버전 호환성: extractedData가 있으면 단일 이벤트로 변환
                val extractedData = jsonObj["extractedData"]?.jsonObject?.toMap()
                if (extractedData != null) {
                    listOf(extractedData)
                } else {
                    emptyList()
                }
            }
            
            AiProcessingResult(type, confidence, events)
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinAiAgent", "AI 응답 파싱 실패", e)
            AiProcessingResult(
                type = "note",
                confidence = 0.0,
                events = emptyList()
            )
        }
    }
}

// ===== 데이터 클래스 =====

@Serializable
private data class OpenAiRequest(
    val model: String,
    val messages: List<AiMessage>,
    val temperature: Double,
    @SerialName("max_tokens")
    val maxTokens: Int
)

@Serializable
private data class AiMessage(
    val role: String,
    val content: String
)

@Serializable
private data class OpenAiResponse(
    val choices: List<OpenAiChoice>
)

@Serializable
private data class OpenAiChoice(
    val message: AiMessage
)


/**
 * AI 처리 결과 (여러 이벤트 지원)
 */
data class AiProcessingResult(
    val type: String,  // "event", "contact", "note"
    val confidence: Double,
    val events: List<Map<String, JsonElement?>>  // 여러 이벤트를 배열로 저장
)

