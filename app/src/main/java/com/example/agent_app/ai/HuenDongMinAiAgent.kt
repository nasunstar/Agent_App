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
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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
        
        val currentDate = java.time.Instant.ofEpochMilli(receivedTimestamp)
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
            당신은 사용자의 개인 데이터를 지능적으로 관리하는 AI 비서 "HuenDongMin"입니다.
            
            ⚠️⚠️⚠️ 절대적으로 중요: 현재 시간 기준 (한국 표준시 KST, Asia/Seoul, UTC+9) ⚠️⚠️⚠️
            - 현재 연도: ${currentDate.year}년
            - 현재 월: ${currentDate.monthValue}월
            - 현재 일: ${currentDate.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            - 전체 시간: $currentDate
            - Epoch ms: ${receivedTimestamp}ms (한국 시간 기준)
            
            🔴🔴🔴 최우선 규칙: 명시적 날짜 인식 (반드시 지켜야 함!) 🔴🔴🔴
            
            메일 본문에서 다음 날짜 형식을 찾으면 **절대적으로 우선 인식**하세요:
            
            1. **점(.) 구분 날짜**: "9.30", "10.16", "12.25" 등
               → "9.30" = 2025년 9월 30일
               → "10.16" = 2025년 10월 16일
            
            2. **슬래시(/) 구분 날짜**: "9/30", "10/16" 등
               → "9/30" = 2025년 9월 30일
            
            3. **한글 날짜**: "10월 16일", "9월 30일" 등
               → "10월 16일" = 2025년 10월 16일
            
            4. **전체 날짜**: "2025년 10월 16일", "2025.10.16", "2025-10-16" 등
               → 그대로 인식
            
            5. **날짜 + 요일**: "9.30(화)", "10월 16일 목요일" 등
               → "9.30(화)" = 2025년 9월 30일 화요일
               → 요일은 검증용으로만 사용
            
            ⚠️⚠️⚠️ 시간 계산의 기준 시점 결정 (매우 중요!) ⚠️⚠️⚠️
            
            **단계 1: 기준 시점 찾기**
            - 메일 본문에서 명시적 날짜를 **먼저** 찾으세요
            - 명시적 날짜가 **있으면**: 그 날짜를 기준 시점으로 사용
            - 명시적 날짜가 **없으면**: 현재 시간(${receivedTimestamp}ms)을 기준으로 사용
            
            **단계 2: 상대적 표현 계산**
            - "내일", "모레", "다음주", "담주", "이번 주" 등은 **1단계의 기준 시점**을 기준으로 계산
            
            🔍 **실제 예시 (반드시 참고하세요):**
            
            예시 1: 메일 본문 "2025년 10월 16일 ... 다음주 수요일 미팅"
            → 기준 시점: 10월 16일 (명시적 날짜)
            → 다음주 수요일: 10월 16일 기준 다음주 수요일 = **10월 22일** ✅
            → ❌ 틀린 계산: 현재 시간 기준 다음주 수요일 (절대 금지!)
            
            예시 2: 메일 본문 "9.30(화) 14시 회의"
            → 기준 시점: 9월 30일 (명시적 날짜)
            → 시작 시간: **2025년 9월 30일 14:00** ✅
            → ❌ 틀린 계산: 10월 28일 또는 다른 날짜 (절대 금지!)
            
            예시 3: 메일 본문 "내일 오후 3시" (날짜 없음)
            → 기준 시점: 현재 시간 (${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일)
            → 내일: 현재 기준 내일 ✅
            
            **단계 3: 연도 추론**
            - 연도가 명시되지 않은 날짜는 현재 연도(${currentDate.year})를 사용
            - 계산된 날짜가 과거면 다음 해로 조정
        """.trimIndent()
        
        val userPrompt = """
            다음 Gmail 메일을 분석하여 약속/일정이 있는지 확인하고, 있다면 구조화된 JSON으로 반환하세요.
            
            📧 제목: ${emailSubject ?: "(없음)"}
            
            📧 본문:
            ${emailBody ?: ""}
            
            📅 현재 기준 시간:
            - 연도: ${currentDate.year}년
            - 월: ${currentDate.monthValue}월
            - 일: ${currentDate.dayOfMonth}일
            - 요일: $dayOfWeekKorean
            - Epoch ms: ${receivedTimestamp}ms
            
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
            - 1단계에서 명시적 날짜가 **없으면**: 현재 시간을 기준 시점으로 사용
            
            🔍 예시:
            - 메일에 "9.30(화)"가 있음 → 기준 시점: 2025년 9월 30일 ✅
            - 메일에 "2025년 10월 16일"이 있음 → 기준 시점: 2025년 10월 16일 ✅
            - 메일에 날짜 없음 → 기준 시점: 현재 시간(${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일) ✅
            
            **3단계: 상대적 표현 계산**
            
            "내일", "모레", "다음주", "담주" 등은 **2단계의 기준 시점**을 기준으로 계산
            
            🔍 예시:
            - 기준 시점: 10월 16일, 표현: "다음주 수요일" → 10월 16일 기준 다음주 수요일 = **10월 22일** ✅
            - 기준 시점: 9월 30일, 표현: "14시" → **9월 30일 14:00** ✅
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
        
        // IngestItem 저장 (원본 보관, 첫 번째 이벤트 정보 사용)
        val firstEvent = result.events.firstOrNull()
        val ingestItem = IngestItem(
            id = originalEmailId,
            source = "gmail",
            type = result.type,
            title = emailSubject,
            body = emailBody,
            timestamp = receivedTimestamp,
            dueDate = firstEvent?.get("startAt")?.jsonPrimitive?.longOrNull,
            confidence = result.confidence,
            metaJson = null
        )
        ingestRepository.upsert(ingestItem)
        
        // Event 저장 (일정인 경우, 여러 개 지원)
        if (result.type == "event" && result.events.isNotEmpty()) {
            result.events.forEachIndexed { index, eventData ->
                val originalStartAt = eventData["startAt"]?.jsonPrimitive?.longOrNull
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
        
        android.util.Log.d("HuenDongMinAiAgent", "📱 현재 시간(ms): $currentTimestamp")
        android.util.Log.d("HuenDongMinAiAgent", "📅 현재 날짜: ${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일 $dayOfWeekKorean")
        android.util.Log.d("HuenDongMinAiAgent", "⚠️ AI에게 전달: ${currentDate.year}년 ${currentDate.monthValue}월을 기준으로 해석하라고 명령!")
        android.util.Log.d("HuenDongMinAiAgent", "🕐 전체 날짜 정보: $currentDate")
        
        val systemPrompt = """
            당신은 이미지에서 일정을 추출하는 AI 비서 "HuenDongMin"입니다.
            
            ⚠️⚠️⚠️ 절대적으로 중요: 현재 시간 기준 (한국 표준시 KST, Asia/Seoul, UTC+9) ⚠️⚠️⚠️
            - 현재 연도: ${currentDate.year}년
            - 현재 월: ${currentDate.monthValue}월
            - 현재 일: ${currentDate.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            - 전체 시간: $currentDate
            - Epoch ms: ${currentTimestamp}ms (한국 시간 기준)
            
            🔴🔴🔴 카카오톡 OCR 특별 규칙 (반드시 준수!) 🔴🔴🔴
            
            카카오톡 대화창은 보통 다음 구조를 가집니다:
            - **상단**: "2025년 10월 16일 목요일" (날짜 헤더)
            - **대화**: "담주 수욜", "내일", "모레" 등 (상대적 표현)
            
            ⚠️ **절대 규칙:** 
            - 카톡 상단의 날짜를 **기준 시점**으로 사용!
            - 현재 시간(${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일)을 절대 사용하지 말 것!
            
            예: "2025년 10월 16일 ... 담주 수욜" 
            → 기준: 10월 16일 → 담주 수욜: **10월 22일** ✅
            → ❌ 현재 시간 기준 계산 금지!
            
            🔴🔴🔴 최우선 규칙: 명시적 날짜 인식 (반드시 지켜야 함!) 🔴🔴🔴
            
            ⚠️⚠️⚠️ 중요: 현재 연도는 ${currentDate.year}년입니다! 모든 날짜는 ${currentDate.year}년 기준입니다! ⚠️⚠️⚠️
            
            OCR 텍스트에서 다음 날짜 형식을 찾으면 **절대적으로 우선 인식**하세요:
            
            1. **점(.) 구분 날짜**: "9.30", "10.16", "12.25" 등
               → "9.30" = ${currentDate.year}년 9월 30일
               → "10.16" = ${currentDate.year}년 10월 16일
               → "12.25" = ${currentDate.year}년 12월 25일
            
            2. **슬래시(/) 구분 날짜**: "9/30", "10/16" 등
               → "9/30" = ${currentDate.year}년 9월 30일
            
            3. **한글 날짜**: "10월 16일", "9월 30일" 등
               → "10월 16일" = ${currentDate.year}년 10월 16일
            
            4. **전체 날짜**: "2025년 10월 16일", "2025.10.16", "2025-10-16" 등
               → 그대로 인식
            
            5. **날짜 + 요일**: "9.30(화)", "10월 16일 목요일" 등
               → "9.30(화)" = 2025년 9월 30일 화요일
               → 요일은 검증용으로만 사용
            
            ⚠️⚠️⚠️ 시간 계산의 기준 시점 결정 (매우 중요!) ⚠️⚠️⚠️
            
            **단계 1: 기준 시점 찾기**
            - OCR 텍스트에서 명시적 날짜를 **먼저** 찾으세요
            - 명시적 날짜가 **있으면**: 그 날짜를 기준 시점으로 사용
            - 명시적 날짜가 **없으면**: 현재 시간(${currentTimestamp}ms)을 기준으로 사용
            
            **단계 2: 상대적 표현 계산**
            - "내일", "모레", "다음주", "담주", "이번 주" 등은 **1단계의 기준 시점**을 기준으로 계산
            
            **단계 3: 연도 추론**
            - 연도가 명시되지 않은 날짜는 현재 연도(${currentDate.year})를 사용
            - 계산된 날짜가 과거면 다음 해로 조정
            
            **한글 OCR 오인식 대응:**
            - "모레 오 T 3 시" → "모레 오후 3시"
            - "담주 수욜" → "다음주 수요일"
            - "9 . 3 0" → "9.30"
        """.trimIndent()
        
        // Few-shot 예시 (하드코딩 - 리소스 로딩 문제 우회)
        val fewShotExamples = """
            
            
            🎯 **Few-shot Learning 예시:**
            
            **예시: 카카오톡 상대적 날짜**
            OCR: "2025년 10월 16일 목요일
            강흔
            담주 수욜 동성로 ㄱㄱ"
            
            **사고 과정:**
            1. "2025년 10월 16일 목요일" 발견 → 기준 시점: 10월 16일(목)
            2. "담주 수욜" = 다음주 수요일
            3. 10월 16일(목) 기준으로 다음주 수요일 계산:
               - 10월 16일이 속한 주: 10월 13일(월) ~ 10월 19일(일)
               - 다음주: 10월 20일(월) ~ 10월 26일(일)
               - 다음주 수요일: 10월 22일(수)
            4. 시간 없음 → 00:00:00 기본값
            5. Epoch 계산: 2025-10-22 00:00:00 KST = 1729526400000
            
            **결과:**
            - 기준 날짜: 2025-10-16
            - 계산된 날짜: 2025-10-22
            - startAt: 1729526400000
            - 제목: 동성로 약속
            - 장소: 동성로
            
            ⛔ **절대 금지:**
            ❌ 현재 시간(10월 28일) 기준으로 "담주 수요일" 계산 → 11월 5일 (틀림!)
            ❌ OCR 상단의 "10월 16일" 무시하고 현재 기준 계산
            ❌ "담주 수욜"을 "10월 28일"로 해석
            
            ✅ 카톡은 반드시 상단 날짜(10월 16일)를 기준으로 계산!
            
            **예시: 점(.) 구분 날짜 + 시간**
            OCR: "▶일시 : 9.30(화) 14시~15시
            ▶장소 : 미래융합과학관 102호
            ▶내용 : SL 설명회"
            
            **사고 과정:**
            1. "9.30(화)" 발견 → 명시적 날짜: 9월 30일
            2. 연도 결정: 현재가 2025년이므로 → 2025년 9월 30일
            3. "14시~15시" 발견 → 시작: 14:00, 종료: 15:00
            4. Epoch 계산:
               - 2025-09-30 14:00:00 KST = 1727672400000
               - 2025-09-30 15:00:00 KST = 1727676000000
            
            **결과:**
            - startAt: 1727672400000
            - endAt: 1727676000000
            - 제목: SL 설명회
            
            ⛔ **절대 금지:**
            ❌ "9.30"을 "내일"로 해석
            ❌ "9.30"을 "10월 28일"로 해석
            ❌ "9.30"을 2024년으로 계산
            
            ✅ "9.30"은 무조건 2025년 9월 30일!
            
            🔴 **공통 규칙:**
            - 🔴 모든 날짜는 2025년 기준입니다 (2024년 절대 금지!)
            - ⏰ 시간이 없으면 00:00:00 기본값
            - 🌏 epoch milliseconds는 한국 시간(KST, UTC+9) 기준
            - 💬 카카오톡은 상단 날짜를 기준 시점으로 사용 (현재 시간 사용 금지!)
            - 📅 명시적 날짜가 있으면 그것을 우선 사용
        """.trimIndent()
        
        val fullSystemPrompt = systemPrompt + fewShotExamples
        
        android.util.Log.d("HuenDongMinAiAgent", "📊 기본 System Prompt 길이: ${systemPrompt.length}자")
        android.util.Log.d("HuenDongMinAiAgent", "📊 Few-shot 추가 길이: ${fewShotExamples.length}자")
        android.util.Log.d("HuenDongMinAiAgent", "📊 최종 System Prompt 길이: ${fullSystemPrompt.length}자")
        
        val userPrompt = """
            다음 OCR 텍스트에서 일정 정보를 추출하세요.
            
            📱 OCR 텍스트:
            ${ocrText}
            
            📅 현재 기준 시간:
            - 연도: ${currentDate.year}년
            - 월: ${currentDate.monthValue}월
            - 일: ${currentDate.dayOfMonth}일
            - 요일: $dayOfWeekKorean
            - Epoch ms: ${currentTimestamp}ms
            
            🔴🔴🔴 처리 순서 (반드시 이 순서대로 따르세요!) 🔴🔴🔴
            
            **1단계: 명시적 날짜 찾기 (최우선!)**
            
            OCR 텍스트에서 다음 패턴을 찾으세요:
            - "9.30", "10.16" 등 점(.) 구분 → 9월 30일, 10월 16일
            - "9/30", "10/16" 등 슬래시(/) 구분 → 9월 30일, 10월 16일
            - "10월 16일", "9월 30일" 등 한글 → 그대로 인식
            - "2025년 10월 16일" 등 전체 날짜 → 그대로 인식
            - "9.30(화)", "10.16(목)" 등 날짜+요일 → 날짜 우선, 요일은 검증용
            
            **2단계: 기준 시점 결정**
            
            - 1단계에서 명시적 날짜를 **찾았으면**: 그 날짜를 기준 시점으로 사용
            - 1단계에서 명시적 날짜가 **없으면**: 현재 시간을 기준 시점으로 사용
            
            🔍 예시:
            - OCR에 "9.30(화)"가 있음 → 기준 시점: 2025년 9월 30일 ✅
            - OCR에 "2025년 10월 16일"이 있음 → 기준 시점: 2025년 10월 16일 ✅
            - OCR에 날짜 없음 → 기준 시점: 현재 시간(${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일) ✅
            
            **3단계: 상대적 표현 계산**
            
            "내일", "모레", "다음주", "담주" 등은 **2단계의 기준 시점**을 기준으로 계산
            
            🔍 예시:
            - 기준 시점: 10월 16일, 표현: "담주 수요일" → 10월 16일 기준 다음주 수요일 = **10월 22일** ✅
            - 기준 시점: 9월 30일, 표현: "14시" → **9월 30일 14:00** ✅
            - 기준 시점: 현재, 표현: "내일" → 현재 기준 내일 ✅
            
            **4단계: epoch milliseconds 변환**
            
            - 3단계에서 계산한 날짜/시간을 epoch milliseconds로 변환
            - 한국 시간(KST, UTC+9) 기준으로 계산
            
            🎯 **완전한 실전 예시 (현재: ${currentDate.year}년 ${currentDate.monthValue}월 ${currentDate.dayOfMonth}일):**
            
            OCR: "▶일시 : 9.30(화) 14시~15시"
            
            **사고 과정:**
            1. "9.30(화)" 발견 → 명시적 날짜: **9월 30일**
            2. 연도 결정: 현재가 ${currentDate.year}년이므로 → **${currentDate.year}년 9월 30일**
            3. 시간: 14시~15시
            4. Epoch 계산:
               - ${currentDate.year}년 9월 30일 14:00 (KST) = 1759208400000
               - ${currentDate.year}년 9월 30일 15:00 (KST) = 1759212000000
            
            **JSON 출력:**
            ```json
            {
              "title": "SL 설명회",
              "startAt": 1759208400000,
              "endAt": 1759212000000
            }
            ```
            
            ⛔ **절대 금지:**
            - ❌ "9.30"을 "내일"이나 "10월 28일"로 해석
            - ❌ "9.30"을 2024년으로 계산 (현재는 ${currentDate.year}년!)
            - ❌ "9.30"을 현재 날짜 기준으로 상대 계산
            - ✅ "9.30"은 **${currentDate.year}년 9월 30일**!
            
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
                  "body": "원본 OCR 텍스트를 한 줄로 요약"
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
            1. 모든 시간은 한국 표준시(KST, UTC+9) 기준으로 계산하세요!
               - epoch milliseconds는 한국 시간으로 변환한 값입니다
               - 예: 2025년 10월 28일 15:00 (한국 시간) = 1761631200000
            
            2. startAt과 endAt은 반드시 계산된 숫자여야 합니다!
               ❌ 나쁜 예: "startAt": 1761050295871 + (7 * 24 * 60 * 60 * 1000)
               ✅ 좋은 예: "startAt": 1761655895871
            
            3. 시간이 명시되지 않은 경우 오전 12시(00:00:00)를 기준으로 하세요!
               - "내일" → 내일 00:00:00
               - "10월 30일" → 10월 30일 00:00:00
               - "담주 수욜" → 다음주 수요일 00:00:00
               - "모레" → 모레 00:00:00
            
            4. body는 줄바꿈 없이 한 줄로 작성하세요!
               ❌ 나쁜 예: "body": "첫줄\두번째줄\세번째줄"
               ✅ 좋은 예: "body": "OCR 텍스트 요약 - 이유섭형과 강흔이의 대화"
            
            5. 여러 일정이 있으면 반드시 events 배열에 모두 포함하세요!
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
        
        // IngestItem 저장 (첫 번째 이벤트 정보 사용)
        val firstEvent = result.events.firstOrNull()
        val ingestItem = IngestItem(
            id = originalOcrId,
            source = "ocr",
            type = result.type,
            title = firstEvent?.get("title")?.jsonPrimitive?.content,
            body = ocrText,
            timestamp = currentTimestamp,
            dueDate = firstEvent?.get("startAt")?.jsonPrimitive?.longOrNull,
            confidence = result.confidence,
            metaJson = null
        )
        ingestRepository.upsert(ingestItem)
        
        // Event 저장 (여러 개 지원)
        if (result.type == "event" && result.events.isNotEmpty()) {
            result.events.forEachIndexed { index, eventData ->
                val originalStartAt = eventData["startAt"]?.jsonPrimitive?.longOrNull
                android.util.Log.d("HuenDongMinAiAgent", "OCR Event ${index + 1} - AI 추출 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                
                // ⚠️ correctPastDate 제거: AI가 정확하게 날짜를 추출하도록 프롬프트를 강화했으므로
                // AI의 응답을 그대로 신뢰합니다.
                
                // 모든 Event는 같은 IngestItem을 참조 (원본 데이터 추적용)
                val event = createEventFromAiData(eventData, originalOcrId, "ocr")
                eventDao.upsert(event)
                android.util.Log.d("HuenDongMinAiAgent", "OCR Event ${index + 1} 저장 완료 - ${event.title}, sourceId: $originalOcrId, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
            }
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
        android.util.Log.d("HuenDongMinAiAgent", "📡 callOpenAi 시작")
        
        try {
            val apiKey = BuildConfig.OPENAI_API_KEY
            android.util.Log.d("HuenDongMinAiAgent", "🔑 API Key 확인: ${if (apiKey.isNotBlank()) "존재 (${apiKey.length}자)" else "없음!"}")
            require(apiKey.isNotBlank()) { "OpenAI API 키가 설정되지 않았습니다." }
            
            android.util.Log.d("HuenDongMinAiAgent", "📦 요청 객체 생성 시작 (messages 개수: ${messages.size})")
            android.util.Log.d("HuenDongMinAiAgent", "  - model: gpt-4o-mini")
            android.util.Log.d("HuenDongMinAiAgent", "  - temperature: 0.3")
            android.util.Log.d("HuenDongMinAiAgent", "  - maxTokens: 1000")
            
            // Serialization 우회: JSON을 직접 문자열로 생성
            android.util.Log.d("HuenDongMinAiAgent", "📝 JSON 직접 생성 시작")
            
            // JSON 이스케이프 함수
            fun String.escapeJson(): String = this
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            
            android.util.Log.d("HuenDongMinAiAgent", "  메시지 이스케이프 중...")
            val systemContent = messages[0].content.escapeJson()
            val userContent = messages[1].content.escapeJson()
            android.util.Log.d("HuenDongMinAiAgent", "  이스케이프 완료")
            
            android.util.Log.d("HuenDongMinAiAgent", "  JSON 문자열 조합 중...")
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
                
                android.util.Log.d("HuenDongMinAiAgent", "🔄 응답 파싱 중 (정규식 사용)...")
                android.util.Log.d("HuenDongMinAiAgent", "  응답 미리보기: ${responseBody.take(200)}")
                
                // Serialization 완전 우회: 정규식으로 직접 content 추출
                // OpenAI 응답 형식: {"choices":[{"message":{"content":"..."}}]}
                android.util.Log.d("HuenDongMinAiAgent", "  정규식으로 content 추출 시도...")
                
                // content 값을 추출하는 정규식 (escaped 문자 포함)
                val contentRegex = """"content"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
                val matchResult = contentRegex.find(responseBody)
                
                if (matchResult == null) {
                    android.util.Log.e("HuenDongMinAiAgent", "❌ content를 찾을 수 없습니다")
                    android.util.Log.e("HuenDongMinAiAgent", "응답 전체: $responseBody")
                    throw Exception("OpenAI 응답에서 content를 찾을 수 없습니다")
                }
                
                android.util.Log.d("HuenDongMinAiAgent", "  content 매칭 성공!")
                
                // escaped 문자를 원래대로 복원
                val content = matchResult.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                
                android.util.Log.d("HuenDongMinAiAgent", "✅ AI 응답 성공 (${content.length}자)")
                android.util.Log.d("HuenDongMinAiAgent", "  응답 내용 미리보기: ${content.take(100)}")
                
                content
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
 * AI 처리 결과 (여러 이벤트 지원)
 */
data class AiProcessingResult(
    val type: String,  // "event", "contact", "note"
    val confidence: Double,
    val events: List<Map<String, JsonElement?>>  // 여러 이벤트를 배열로 저장
)

