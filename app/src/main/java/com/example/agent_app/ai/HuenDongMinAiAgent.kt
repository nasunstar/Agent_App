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
import kotlinx.serialization.json.JsonNull
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
     * 시간 분석 결과 데이터 클래스
     */
    data class TimeAnalysisResult(
        val hasExplicitDate: Boolean,  // 명시적 날짜가 있는지
        val explicitDate: String?,  // 명시적 날짜 (예: "2025-10-16", "10월 16일")
        val hasRelativeTime: Boolean,  // 상대적 시간 표현이 있는지
        val relativeTimeExpressions: List<String>,  // 상대적 시간 표현 목록 (예: ["내일", "다음주 수요일"])
        val hasTime: Boolean,  // 시간이 명시되어 있는지
        val time: String?,  // 시간 (예: "14:00", "오후 3시")
        val finalDate: String?,  // LLM이 계산한 최종 시작 날짜 (예: "2025-11-12") - YYYY-MM-DD 형식
        val finalTime: String?,  // LLM이 계산한 최종 시작 시간 (예: "12:00") - HH:mm 형식
        val finalEndDate: String?,  // LLM이 계산한 최종 종료 날짜 (예: "2025-11-12") - YYYY-MM-DD 형식 (없으면 null)
        val finalEndTime: String?,  // LLM이 계산한 최종 종료 시간 (예: "14:00") - HH:mm 형식 (없으면 null)
        val referenceTimestamp: Long,  // 기준 시점 (메일 수신 시간 등)
        val currentTimestamp: Long,  // 현재 시간
        val timezone: String = "Asia/Seoul",  // 시간대
        val resolvedStartEpoch: Long? = null,
        val resolvedEndEpoch: Long? = null,
        val derivedFromRule: Boolean = false
    )
    
    /**
     * 텍스트에서 시간 정보를 추출하고 분석하는 함수 (AI tool 사용)
     * 
     * @param text 분석할 텍스트
     * @param referenceTimestamp 기준 시점 (메일 수신 시간, SMS 수신 시간 등)
     * @param sourceType 데이터 소스 타입 ("gmail", "sms", "ocr", "push_notification")
     * @return TimeAnalysisResult 시간 분석 결과
     */
    private suspend fun analyzeTimeFromText(
        text: String?,
        referenceTimestamp: Long,
        sourceType: String
    ): TimeAnalysisResult = withContext(dispatcher) {
        val zoneId = java.time.ZoneId.of("Asia/Seoul")
        val now = java.time.Instant.now().atZone(zoneId)
        val referenceDate = java.time.Instant.ofEpochMilli(referenceTimestamp).atZone(zoneId)
        val normalizedText = text?.trim().orEmpty()

        if (normalizedText.isNotEmpty()) {
            val expressions = EventTimeParser.extractTimeExpressions(normalizedText)
            val resolved = EventTimeParser.resolveExpressions(
                normalizedText,
                expressions,
                ResolveContext(referenceTimestamp, "Asia/Seoul")
            )

            if (resolved.isNotEmpty()) {
                val primary = resolved.first()
                return@withContext buildTimeAnalysisResultFromWindow(
                    expressions = expressions,
                    window = primary,
                    referenceTimestamp = referenceTimestamp,
                    now = now
                )
            }
        }

        android.util.Log.d("HuenDongMinAiAgent", "규칙 기반 분석 실패, LLM 보조 호출 ($sourceType)")

        val systemPrompt = """
            당신은 한국어 텍스트에서 시간 정보를 추출하는 보조 도구입니다.
            모든 계산은 KST(Asia/Seoul) 기준이며, 반드시 ISO 포맷(YYYY-MM-DD, HH:mm)을 지켜 주세요.
            
            ⚠️ **중요**: epoch milliseconds를 계산하지 마세요! 날짜와 시간 문자열만 반환하세요.
            시스템이 자동으로 epoch milliseconds로 변환합니다.
        """.trimIndent()

        // OCR 전용 Few-shot 예시 추가
        val fewShotExamples = if (sourceType == "ocr") {
            """
            
            🎯 **Few-shot 예시 (OCR 전용):**
            
            **예시 1: 명시적 날짜**
            기준 시각: 2025-11-24 10:00
            텍스트: "2025,10,30.(목) 11:30 회의"
            
            결과:
            {
              "hasExplicitDate": true,
              "explicitDate": "2025-10-30",
              "hasRelativeTime": false,
              "relativeTimeExpressions": [],
              "hasTime": true,
              "time": "11:30",
              "finalDate": "2025-10-30",
              "finalTime": "11:30",
              "finalEndDate": null,
              "finalEndTime": null
            }
            
            **예시 2: 한글 날짜**
            기준 시각: 2025-11-24 10:00
            텍스트: "10월 30일 14시 회의"
            
            결과:
            {
              "hasExplicitDate": true,
              "explicitDate": "2025-10-30",
              "hasRelativeTime": false,
              "relativeTimeExpressions": [],
              "hasTime": true,
              "time": "14:00",
              "finalDate": "2025-10-30",
              "finalTime": "14:00",
              "finalEndDate": null,
              "finalEndTime": null
            }
            
            **예시 3: 시간 없음**
            기준 시각: 2025-11-24 10:00
            텍스트: "11월 15일 행사"
            
            결과:
            {
              "hasExplicitDate": true,
              "explicitDate": "2025-11-15",
              "hasRelativeTime": false,
              "relativeTimeExpressions": [],
              "hasTime": false,
              "time": null,
              "finalDate": "2025-11-15",
              "finalTime": "00:00",
              "finalEndDate": null,
              "finalEndTime": null
            }
            
            **예시 4: 상대적 날짜 표현 (채팅/메시지)**
            기준 시각: 2025-10-16 16:16
            텍스트: "담주 수욜 동성로"
            
            결과:
            {
              "hasExplicitDate": false,
              "explicitDate": null,
              "hasRelativeTime": true,
              "relativeTimeExpressions": ["담주", "수욜"],
              "hasTime": false,
              "time": null,
              "finalDate": "2025-10-22",
              "finalTime": "00:00",
              "finalEndDate": null,
              "finalEndTime": null
            }
            
            ⚠️ **해석:**
            - "담주" = 다음 주
            - "수욜" = 수요일
            - 기준 시각이 2025-10-16(수요일)이므로, 다음 주 수요일은 2025-10-22
            
            **예시 5: 상대적 날짜 + 시간대 + 구체적 시간 (채팅/메시지)**
            기준 시각: 2025-11-24 17:54
            텍스트: "내일 오후 1시에 점심 고고?"
            
            결과:
            {
              "hasExplicitDate": false,
              "explicitDate": null,
              "hasRelativeTime": true,
              "relativeTimeExpressions": ["내일"],
              "hasTime": true,
              "time": "13:00",
              "finalDate": "2025-11-25",
              "finalTime": "13:00",
              "finalEndDate": null,
              "finalEndTime": null
            }
            
            ⚠️ **해석:**
            - 날짜가 명시적으로 없음 → 현재 날짜(2025-11-24) 기준으로 계산
            - "내일" = 현재 날짜 + 1일 = 2025-11-25
            - "오후 1시" = 13:00 (24시간 형식)
            - "점심" = 시간대 힌트 (12:00~14:00 범위)
            - 최종 시간: "오후 1시"가 명시되어 있으므로 13:00 사용
        """.trimIndent()
        } else {
            ""
        }

        val userPrompt = """
            기준 시각: ${referenceDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}
            텍스트: ${normalizedText.ifBlank { "(내용 없음)" }}

            결과를 JSON으로만 반환하세요:
            {
              "hasExplicitDate": bool,
              "explicitDate": "YYYY-MM-DD" 또는 null,
              "hasRelativeTime": bool,
              "relativeTimeExpressions": ["..."],
              "hasTime": bool,
              "time": "HH:mm" 또는 null,
              "finalDate": "YYYY-MM-DD",
              "finalTime": "HH:mm",
              "finalEndDate": "YYYY-MM-DD" 또는 null,
              "finalEndTime": "HH:mm" 또는 null
            }
            
            ⚠️ **중요**: epoch milliseconds를 계산하지 마세요! 날짜와 시간 문자열만 반환하세요.
        """.trimIndent()
        
        val fullSystemPrompt = systemPrompt + fewShotExamples

        val messages = listOf(
            AiMessage(role = "system", content = fullSystemPrompt),
            AiMessage(role = "user", content = userPrompt)
        )

        val response = callOpenAi(messages)

        android.util.Log.d("HuenDongMinAiAgent", "=== 시간 분석 LLM 응답 ===")
        android.util.Log.d("HuenDongMinAiAgent", response)
        android.util.Log.d("HuenDongMinAiAgent", "=====================================")

        val cleanedJson = response
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonObj = json.parseToJsonElement(cleanedJson).jsonObject

        val hasExplicitDate = jsonObj["hasExplicitDate"]?.jsonPrimitive?.content?.toBoolean() ?: false
        val explicitDate = jsonObj["explicitDate"]?.jsonPrimitive?.content
        val hasTime = jsonObj["hasTime"]?.jsonPrimitive?.content?.toBoolean() ?: false
        val rawTime = jsonObj["time"]?.jsonPrimitive?.content
        val rawFinalTime = jsonObj["finalTime"]?.jsonPrimitive?.content ?: "00:00"
        val rawFinalEndTime = jsonObj["finalEndTime"]?.jsonPrimitive?.content
        val range = parseTimeRangeExpression(rawFinalTime)
        val normalizedFinalTime = range?.first ?: rawFinalTime
        val normalizedFinalEndTime = rawFinalEndTime ?: range?.second

        TimeAnalysisResult(
            hasExplicitDate = hasExplicitDate,
            explicitDate = explicitDate,
            hasRelativeTime = jsonObj["hasRelativeTime"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            relativeTimeExpressions = jsonObj["relativeTimeExpressions"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.content
            } ?: emptyList(),
            hasTime = hasTime,
            time = rawTime,
            finalDate = jsonObj["finalDate"]?.jsonPrimitive?.content,
            finalTime = normalizedFinalTime,
            finalEndDate = jsonObj["finalEndDate"]?.jsonPrimitive?.content,
            finalEndTime = normalizedFinalEndTime,
            referenceTimestamp = referenceTimestamp,
            currentTimestamp = now.toInstant().toEpochMilli(),
            timezone = "Asia/Seoul"
        )
    }

    private fun buildTimeAnalysisResultFromWindow(
        expressions: List<TimeExpression>,
        window: CandidateTimeWindow,
        referenceTimestamp: Long,
        now: java.time.ZonedDateTime
    ): TimeAnalysisResult {
        val hasExplicitDate = expressions.any {
            it.kind == TimeExprKind.ABSOLUTE_DATE || it.kind == TimeExprKind.RANGE
        }
        val explicitDate = if (hasExplicitDate) window.start.formatDateString() else null
        val relativeExpressions = expressions.filter {
            it.kind == TimeExprKind.RELATIVE_DATE || it.kind == TimeExprKind.WEEKDAY
        }.map { it.text }
        val hasTime = expressions.any { it.kind == TimeExprKind.TIME_OF_DAY }

        return TimeAnalysisResult(
            hasExplicitDate = hasExplicitDate,
            explicitDate = explicitDate,
            hasRelativeTime = relativeExpressions.isNotEmpty(),
            relativeTimeExpressions = relativeExpressions,
            hasTime = hasTime,
            time = if (hasTime) window.start.formatTimeString() else null,
            finalDate = window.start.formatDateString(),
            finalTime = window.start.formatTimeString(),
            finalEndDate = window.end?.formatDateString(),
            finalEndTime = window.end?.formatTimeString(),
            referenceTimestamp = referenceTimestamp,
            currentTimestamp = now.toInstant().toEpochMilli(),
            timezone = "Asia/Seoul",
            resolvedStartEpoch = window.startEpochMs,
            resolvedEndEpoch = window.endEpochMs,
            derivedFromRule = true
        )
    }
    
    /**
     * 시간 분석 결과를 JSON 형식의 이벤트 데이터로 변환하는 함수
     * 
     * LLM이 반환한 날짜/시간 문자열을 epoch milliseconds로 변환만 수행
     * 이중 검증 제거: LLM의 날짜/시간 추출 결과를 신뢰하고 함수가 변환만 수행
     * 
     * @param timeAnalysis 시간 분석 결과 (LLM이 finalDate, finalTime 추출 완료)
     * @param title 이벤트 제목
     * @param body 이벤트 본문
     * @param location 장소 (선택)
     * @param sourceType 데이터 소스 타입 ("ocr", "gmail", "sms", "push_notification")
     * @return JSON 형식의 이벤트 데이터 (Map<String, JsonElement?>)
     */
    private fun convertTimeAnalysisToJson(
        timeAnalysis: TimeAnalysisResult,
        title: String,
        body: String,
        location: String? = null,
        sourceType: String = "gmail"
    ): Map<String, JsonElement?> {
        val referenceDate = java.time.Instant.ofEpochMilli(timeAnalysis.referenceTimestamp)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))

        // Rule-based 파서 결과가 있으면 우선 사용
        if (timeAnalysis.derivedFromRule && timeAnalysis.resolvedStartEpoch != null) {
            val startAt = timeAnalysis.resolvedStartEpoch
            val endAt = timeAnalysis.resolvedEndEpoch ?: (startAt + 60 * 60 * 1000)
            return buildEventResultMap(
                title = title,
                body = body,
                location = location,
                startAt = startAt,
                endAt = endAt,
                needsReview = false
            )
        }
        
        // OCR 전용: 이중 검증 없이 LLM이 추출한 날짜/시간 문자열을 직접 변환
        if (sourceType == "ocr") {
            val startAt = parseDateTimeStringToEpoch(
                dateStr = timeAnalysis.finalDate,
                timeStr = timeAnalysis.finalTime,
                referenceDate = referenceDate
            )
            
            if (startAt == null) {
                android.util.Log.e("HuenDongMinAiAgent", "OCR: 날짜/시간 파싱 실패, fallback 사용")
                return createFallbackEvent(referenceDate, title, body, location)
            }
            
            // 종료 시간 계산
            val endAt = if (timeAnalysis.finalEndDate != null && timeAnalysis.finalEndTime != null) {
                parseDateTimeStringToEpoch(
                    dateStr = timeAnalysis.finalEndDate,
                    timeStr = timeAnalysis.finalEndTime,
                    referenceDate = referenceDate
                ) ?: (startAt + 60 * 60 * 1000) // 파싱 실패 시 기본 1시간
            } else {
                startAt + (60 * 60 * 1000) // 기본 1시간
            }
            
            android.util.Log.d("HuenDongMinAiAgent", "OCR 시간 분석 결과 (LLM 추출 + 함수 변환):")
            android.util.Log.d("HuenDongMinAiAgent", "  - LLM 추출 날짜: ${timeAnalysis.finalDate}")
            android.util.Log.d("HuenDongMinAiAgent", "  - LLM 추출 시간: ${timeAnalysis.finalTime}")
            android.util.Log.d("HuenDongMinAiAgent", "  - 변환된 시작 Epoch ms: $startAt")
            android.util.Log.d("HuenDongMinAiAgent", "  - 변환된 종료 Epoch ms: $endAt")
            
            return buildEventResultMap(
                title = title,
                body = body,
                location = location,
                startAt = startAt,
                endAt = endAt,
                needsReview = false
            )
        }
        
        // Gmail/SMS 등 다른 소스: 기존 이중 검증 로직 유지
        // 1단계: LLM 출력 검증 및 파싱
        val (validatedDate, validatedTime) = validateLlmOutput(
            finalDate = timeAnalysis.finalDate,
            finalTime = timeAnalysis.finalTime,
            explicitDate = timeAnalysis.explicitDate,
            relativeExpressions = timeAnalysis.relativeTimeExpressions,
            referenceDate = referenceDate,
            sourceType = sourceType
        )
        
        // 2단계: 이중 검증 (LLM 계산 vs 코드 재계산)
        val crossValidationResult = crossValidateDate(
            llmDate = validatedDate,
            explicitDate = timeAnalysis.explicitDate,
            relativeExpressions = timeAnalysis.relativeTimeExpressions,
            referenceDate = referenceDate,
            sourceType = sourceType
        )
        
        // 3단계: 날짜/시간 파싱 및 범위 검증
        val dateParts = crossValidationResult.finalDate.split("-")
        if (dateParts.size != 3) {
            android.util.Log.e("HuenDongMinAiAgent", "날짜 형식 오류: ${crossValidationResult.finalDate}")
            return createFallbackEvent(referenceDate, title, body, location)
        }
        
        val year = dateParts[0].toIntOrNull() ?: run {
            android.util.Log.e("HuenDongMinAiAgent", "연도 파싱 실패: ${dateParts[0]}")
            return createFallbackEvent(referenceDate, title, body, location)
        }
        val month = dateParts[1].toIntOrNull() ?: run {
            android.util.Log.e("HuenDongMinAiAgent", "월 파싱 실패: ${dateParts[1]}")
            return createFallbackEvent(referenceDate, title, body, location)
        }
        val day = dateParts[2].toIntOrNull() ?: run {
            android.util.Log.e("HuenDongMinAiAgent", "일 파싱 실패: ${dateParts[2]}")
            return createFallbackEvent(referenceDate, title, body, location)
        }
        
        // 날짜 유효성 검증
        if (!isValidDate(year, month, day)) {
            android.util.Log.e("HuenDongMinAiAgent", "유효하지 않은 날짜: $year-$month-$day")
            return createFallbackEvent(referenceDate, title, body, location)
        }
        
        // 시간 파싱 및 범위 검증
        val timeParts = validatedTime.split(":")
        val hour = if (timeParts.size >= 1) {
            val h = timeParts[0].toIntOrNull() ?: 0
            if (h !in 0..23) {
                android.util.Log.w("HuenDongMinAiAgent", "시간 범위 오류: $h, 0으로 설정")
                0
            } else h
        } else 0
        var minute = if (timeParts.size >= 2) {
            val m = timeParts[1].toIntOrNull() ?: 0
            if (m !in 0..59) {
                android.util.Log.w("HuenDongMinAiAgent", "분 범위 오류: $m, 0으로 설정")
                0
            } else m
        } else 0

        if (shouldForceTopOfHour(timeAnalysis)) {
            minute = 0
        }

        // 최종 날짜/시간 생성 및 epoch milliseconds 변환
        val finalDateTime = try {
            java.time.LocalDate.of(year, month, day)
                .atTime(hour, minute)
                .atZone(java.time.ZoneId.of("Asia/Seoul"))
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinAiAgent", "날짜/시간 생성 실패", e)
            return createFallbackEvent(referenceDate, title, body, location)
        }
        
        val startAt = finalDateTime.toInstant().toEpochMilli()
        
        // 종료 시간 계산: LLM이 계산한 종료 시간이 있으면 사용, 없으면 기본 1시간
        var endAt = if (timeAnalysis.finalEndDate != null && timeAnalysis.finalEndTime != null) {
            // LLM이 종료 시간을 계산한 경우
            val (validatedEndDate, validatedEndTime) = validateLlmOutput(
                finalDate = timeAnalysis.finalEndDate,
                finalTime = timeAnalysis.finalEndTime,
                explicitDate = null,  // 종료 날짜는 별도로 명시적 날짜가 없음
                relativeExpressions = emptyList(),
                referenceDate = referenceDate,
                sourceType = sourceType
            )
            
            val endDateParts = validatedEndDate.split("-")
            if (endDateParts.size == 3) {
                val endYear = endDateParts[0].toIntOrNull() ?: year
                val endMonth = endDateParts[1].toIntOrNull() ?: month
                val endDay = endDateParts[2].toIntOrNull() ?: day
                
                val endTimeParts = validatedEndTime.split(":")
                val endHour = if (endTimeParts.size >= 1) {
                    endTimeParts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: hour
                } else hour
                val endMinute = if (endTimeParts.size >= 2) {
                    endTimeParts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: minute
                } else minute
                
                try {
                    val endDateTime = java.time.LocalDate.of(endYear, endMonth, endDay)
                        .atTime(endHour, endMinute)
                        .atZone(java.time.ZoneId.of("Asia/Seoul"))
                    endDateTime.toInstant().toEpochMilli()
                } catch (e: Exception) {
                    android.util.Log.w("HuenDongMinAiAgent", "종료 시간 파싱 실패, 기본 1시간 사용", e)
                    startAt + (60 * 60 * 1000)
                }
            } else {
                android.util.Log.w("HuenDongMinAiAgent", "종료 날짜 형식 오류, 기본 1시간 사용")
                startAt + (60 * 60 * 1000)
            }
        } else {
            // 종료 시간이 없으면 기본 1시간
            startAt + (60 * 60 * 1000)
        }

        if (endAt <= startAt) {
            android.util.Log.w(
                "HuenDongMinAiAgent",
                "종료 시간이 시작 시간보다 빠르거나 같습니다. 기본 1시간으로 보정합니다. startAt=$startAt, endAt=$endAt"
            )
            endAt = startAt + (60 * 60 * 1000)
        }
        
        android.util.Log.d("HuenDongMinAiAgent", "시간 분석 결과 (LLM 계산 + 검증):")
        android.util.Log.d("HuenDongMinAiAgent", "  - 명시적 날짜: ${timeAnalysis.explicitDate}")
        android.util.Log.d("HuenDongMinAiAgent", "  - 상대적 표현: ${timeAnalysis.relativeTimeExpressions}")
        android.util.Log.d("HuenDongMinAiAgent", "  - LLM 계산 최종 시작 날짜: ${timeAnalysis.finalDate}")
        android.util.Log.d("HuenDongMinAiAgent", "  - LLM 계산 최종 시작 시간: ${timeAnalysis.finalTime}")
        android.util.Log.d("HuenDongMinAiAgent", "  - LLM 계산 최종 종료 날짜: ${timeAnalysis.finalEndDate ?: "없음"}")
        android.util.Log.d("HuenDongMinAiAgent", "  - LLM 계산 최종 종료 시간: ${timeAnalysis.finalEndTime ?: "없음"}")
        android.util.Log.d("HuenDongMinAiAgent", "  - 검증된 시작 날짜: ${crossValidationResult.finalDate}")
        android.util.Log.d("HuenDongMinAiAgent", "  - 검증된 시작 시간: $validatedTime")
        android.util.Log.d("HuenDongMinAiAgent", "  - 최종 시작 날짜/시간: $finalDateTime")
        android.util.Log.d("HuenDongMinAiAgent", "  - 시작 Epoch ms: $startAt")
        android.util.Log.d("HuenDongMinAiAgent", "  - 종료 Epoch ms: $endAt")
        if (crossValidationResult.hasMismatch) {
            android.util.Log.w("HuenDongMinAiAgent", "  ⚠️ 이중 검증 불일치: ${crossValidationResult.mismatchReason}")
        }
        
        return buildEventResultMap(
            title = title,
            body = body,
            location = location,
            startAt = startAt,
            endAt = endAt,
            needsReview = false
        )
    }
    
    /**
     * 날짜/시간 문자열을 epoch milliseconds로 변환하는 함수
     * OCR 전용: LLM이 추출한 날짜/시간 문자열을 받아서 epoch milliseconds로 변환
     * 
     * @param dateStr 날짜 문자열 (예: "2025-10-30")
     * @param timeStr 시간 문자열 (예: "14:00")
     * @param referenceDate 기준 날짜 (연도 생략 시 사용)
     * @return epoch milliseconds 또는 null (파싱 실패 시)
     */
    private fun parseDateTimeStringToEpoch(
        dateStr: String?,
        timeStr: String?,
        referenceDate: java.time.ZonedDateTime
    ): Long? {
        if (dateStr == null) return null
        
        // 날짜 파싱
        val dateParts = dateStr.split("-")
        if (dateParts.size != 3) {
            android.util.Log.e("HuenDongMinAiAgent", "날짜 형식 오류: $dateStr")
            return null
        }
        
        val year = dateParts[0].toIntOrNull() ?: run {
            android.util.Log.e("HuenDongMinAiAgent", "연도 파싱 실패: ${dateParts[0]}")
            return null
        }
        val month = dateParts[1].toIntOrNull() ?: run {
            android.util.Log.e("HuenDongMinAiAgent", "월 파싱 실패: ${dateParts[1]}")
            return null
        }
        val day = dateParts[2].toIntOrNull() ?: run {
            android.util.Log.e("HuenDongMinAiAgent", "일 파싱 실패: ${dateParts[2]}")
            return null
        }
        
        // 날짜 유효성 검증
        if (!isValidDate(year, month, day)) {
            android.util.Log.e("HuenDongMinAiAgent", "유효하지 않은 날짜: $year-$month-$day")
            return null
        }
        
        // 시간 파싱 및 범위 검증
        val timeParts = (timeStr ?: "00:00").split(":")
        val hour = if (timeParts.size >= 1) {
            val h = timeParts[0].toIntOrNull() ?: 0
            if (h !in 0..23) {
                android.util.Log.w("HuenDongMinAiAgent", "시간 범위 오류: $h, 0으로 설정")
                0
            } else h
        } else 0
        val minute = if (timeParts.size >= 2) {
            val m = timeParts[1].toIntOrNull() ?: 0
            if (m !in 0..59) {
                android.util.Log.w("HuenDongMinAiAgent", "분 범위 오류: $m, 0으로 설정")
                0
            } else m
        } else 0
        
        // 최종 날짜/시간 생성 및 epoch milliseconds 변환
        return try {
            java.time.LocalDate.of(year, month, day)
                .atTime(hour, minute)
                .atZone(java.time.ZoneId.of("Asia/Seoul"))
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinAiAgent", "날짜/시간 생성 실패", e)
            null
        }
    }
    
    /**
     * LLM 출력 검증 및 정규화
     */
    private fun validateLlmOutput(
        finalDate: String?,
        finalTime: String?,
        explicitDate: String?,
        relativeExpressions: List<String>,
        referenceDate: java.time.ZonedDateTime,
        sourceType: String
    ): Pair<String, String> {
        // 날짜 형식 검증 및 정규화
        val validatedDate = when {
            finalDate != null && finalDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> {
                // YYYY-MM-DD 형식 검증 통과
                finalDate
            }
            finalDate != null -> {
                // 형식이 맞지 않으면 정규화 시도
                android.util.Log.w("HuenDongMinAiAgent", "날짜 형식 정규화 필요: $finalDate")
                normalizeDateString(finalDate, referenceDate) ?: run {
                    // 정규화 실패 시 fallback
                    createFallbackDate(explicitDate, relativeExpressions, referenceDate, sourceType)
                }
            }
            else -> {
                // LLM 출력이 없으면 fallback
                createFallbackDate(explicitDate, relativeExpressions, referenceDate, sourceType)
            }
        }
        
        // 시간 형식 검증 및 정규화
        val validatedTime = when {
            finalTime != null && finalTime.matches(Regex("\\d{1,2}:\\d{2}")) -> {
                // HH:mm 형식 검증 통과
                val parts = finalTime.split(":")
                val h = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: 0
                val m = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: 0
                "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
            }
            finalTime != null -> {
                // 형식이 맞지 않으면 정규화 시도
                android.util.Log.w("HuenDongMinAiAgent", "시간 형식 정규화 필요: $finalTime")
                normalizeTimeString(finalTime) ?: inferTimeFromKeyword(finalTime, finalDate)
            }
            else -> inferTimeFromKeyword(null, finalDate)
        }
        
        return Pair(validatedDate, validatedTime)
    }
    
    /**
     * 이중 검증 결과 데이터 클래스
     */
    private data class CrossValidationResult(
        val finalDate: String,
        val hasMismatch: Boolean,
        val llmDate: String,
        val codeDate: String,
        val chosenSource: String, // "llm", "code", "explicit", "match"
        val mismatchReason: String? = null
    )
    
    /**
     * 이중 검증: LLM 계산 결과와 코드 재계산 결과 비교
     * 불일치 시 상세 피드백 정보 반환
     */
    private fun crossValidateDate(
        llmDate: String,
        explicitDate: String?,
        relativeExpressions: List<String>,
        referenceDate: java.time.ZonedDateTime,
        sourceType: String
    ): CrossValidationResult {
        // 코드로 재계산
        val codeCalculatedDate = createFallbackDate(explicitDate, relativeExpressions, referenceDate, sourceType)
        
        // LLM 계산과 코드 계산 비교
        if (llmDate != codeCalculatedDate) {
            // 상세 로깅: 불일치 패턴 분석
            android.util.Log.w("HuenDongMinAiAgent", "⚠️⚠️⚠️ 날짜 불일치 감지! ⚠️⚠️⚠️")
            android.util.Log.w("HuenDongMinAiAgent", "  📊 소스 타입: $sourceType")
            android.util.Log.w("HuenDongMinAiAgent", "  🤖 LLM 계산: $llmDate")
            android.util.Log.w("HuenDongMinAiAgent", "  💻 코드 계산: $codeCalculatedDate")
            android.util.Log.w("HuenDongMinAiAgent", "  📅 명시적 날짜: ${explicitDate ?: "없음"}")
            android.util.Log.w("HuenDongMinAiAgent", "  ⏰ 상대적 표현: ${relativeExpressions.joinToString(", ").takeIf { it.isNotEmpty() } ?: "없음"}")
            android.util.Log.w("HuenDongMinAiAgent", "  📍 기준 시점: ${referenceDate.year}-${referenceDate.monthValue.toString().padStart(2, '0')}-${referenceDate.dayOfMonth.toString().padStart(2, '0')}")
            
            // 명시적 날짜가 있으면 그것을 우선 사용
            if (explicitDate != null) {
                val explicitParsed = parseExplicitDate(explicitDate, referenceDate)
                val explicitDateStr = "${explicitParsed.year}-${explicitParsed.monthValue.toString().padStart(2, '0')}-${explicitParsed.dayOfMonth.toString().padStart(2, '0')}"
                
                // 명시적 날짜와 LLM 계산 비교
                if (llmDate == explicitDateStr) {
                    android.util.Log.d("HuenDongMinAiAgent", "✅ LLM 계산이 명시적 날짜와 일치, LLM 결과 사용")
                    return CrossValidationResult(
                        finalDate = llmDate,
                        hasMismatch = true,
                        llmDate = llmDate,
                        codeDate = codeCalculatedDate,
                        chosenSource = "explicit",
                        mismatchReason = "LLM이 명시적 날짜와 일치하지만 코드 계산과는 불일치"
                    )
                } else {
                    android.util.Log.w("HuenDongMinAiAgent", "⚠️ LLM 계산이 명시적 날짜와 불일치, 코드 계산 사용")
                    android.util.Log.w("HuenDongMinAiAgent", "  📅 명시적 날짜 파싱 결과: $explicitDateStr")
                    return CrossValidationResult(
                        finalDate = codeCalculatedDate,
                        hasMismatch = true,
                        llmDate = llmDate,
                        codeDate = codeCalculatedDate,
                        chosenSource = "code",
                        mismatchReason = "LLM 계산이 명시적 날짜($explicitDateStr)와 불일치하여 코드 계산 사용"
                    )
                }
            }
            
            // OCR의 경우 LLM 계산 우선, 다른 소스는 코드 계산 우선
            if (sourceType == "ocr") {
                android.util.Log.d("HuenDongMinAiAgent", "✅ OCR: LLM 계산 결과 사용 (문맥 이해 우선)")
                android.util.Log.w("HuenDongMinAiAgent", "  ⚠️ 주의: 코드 계산($codeCalculatedDate)과 다르지만 LLM 결과($llmDate)를 신뢰")
                return CrossValidationResult(
                    finalDate = llmDate,
                    hasMismatch = true,
                    llmDate = llmDate,
                    codeDate = codeCalculatedDate,
                    chosenSource = "llm",
                    mismatchReason = "OCR 소스: LLM의 문맥 이해를 우선시하여 LLM 결과 사용"
                )
            } else {
                android.util.Log.d("HuenDongMinAiAgent", "✅ 다른 소스: 코드 계산 결과 사용 (정확성 우선)")
                android.util.Log.w("HuenDongMinAiAgent", "  ⚠️ 주의: LLM 계산($llmDate)과 다르지만 코드 결과($codeCalculatedDate)를 신뢰")
                return CrossValidationResult(
                    finalDate = codeCalculatedDate,
                    hasMismatch = true,
                    llmDate = llmDate,
                    codeDate = codeCalculatedDate,
                    chosenSource = "code",
                    mismatchReason = "${sourceType} 소스: 코드 계산의 정확성을 우선시하여 코드 결과 사용"
                )
            }
        }
        
        android.util.Log.d("HuenDongMinAiAgent", "✅ LLM 계산과 코드 계산 일치: $llmDate")
        return CrossValidationResult(
            finalDate = llmDate,
            hasMismatch = false,
            llmDate = llmDate,
            codeDate = codeCalculatedDate,
            chosenSource = "match",
            mismatchReason = null
        )
    }
    
    /**
     * Fallback 날짜 생성 (기존 로직 사용)
     */
    private fun createFallbackDate(
        explicitDate: String?,
        relativeExpressions: List<String>,
        referenceDate: java.time.ZonedDateTime,
        sourceType: String
    ): String {
        val baseDate = if (explicitDate != null) {
            parseExplicitDate(explicitDate, referenceDate)
        } else {
            referenceDate
        }
        
        val targetDate = if (relativeExpressions.isNotEmpty()) {
            if (sourceType == "ocr") {
                // OCR: 명시적 날짜를 기준으로 상대적 표현 계산
                processRelativeTimeExpressions(relativeExpressions, baseDate)
            } else {
                // 다른 소스: 명시적 날짜가 없을 때만 상대적 표현 처리
                if (explicitDate == null) {
                    processRelativeTimeExpressions(relativeExpressions, baseDate)
                } else {
                    baseDate
                }
            }
        } else {
            baseDate
        }
        
        return "${targetDate.year}-${targetDate.monthValue.toString().padStart(2, '0')}-${targetDate.dayOfMonth.toString().padStart(2, '0')}"
    }
    
    /**
     * 날짜 문자열 정규화
     */
    private fun normalizeDateString(dateStr: String, referenceDate: java.time.ZonedDateTime): String? {
        return try {
            // "2025/11/12" → "2025-11-12"
            if (dateStr.contains("/")) {
                val parts = dateStr.split("/")
                if (parts.size == 3) {
                    val year = parts[0].toIntOrNull() ?: referenceDate.year
                    val month = parts[1].toIntOrNull() ?: return null
                    val day = parts[2].toIntOrNull() ?: return null
                    "${year}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                } else null
            }
            // "2025.11.12" → "2025-11-12"
            else if (dateStr.contains(".")) {
                val parts = dateStr.split(".")
                if (parts.size == 3) {
                    val year = parts[0].toIntOrNull() ?: referenceDate.year
                    val month = parts[1].toIntOrNull() ?: return null
                    val day = parts[2].toIntOrNull() ?: return null
                    "${year}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                } else null
            }
            else null
        } catch (e: Exception) {
            android.util.Log.w("HuenDongMinAiAgent", "날짜 정규화 실패: $dateStr", e)
            null
        }
    }
    
    /**
     * 시간 문자열 정규화
     */
    private fun normalizeTimeString(timeStr: String): String? {
        return try {
            val trimmed = timeStr.trim()
            if (trimmed.matches(Regex("\\d{1,2}:\\d{2}"))) {
                val parts = trimmed.split(":")
                val h = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: return null
                val m = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: return null
                return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
            }
            
            val lower = trimmed.lowercase()
            val isPm = lower.contains("오후") || lower.contains("pm")
            val isAm = lower.contains("오전") || lower.contains("am")
            
            var hour: Int? = null
            var minute: Int? = null
            
            if (trimmed.contains(":")) {
                val parts = trimmed.split(":")
                hour = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull()
                val minuteStr = parts.getOrNull(1)?.filter { it.isDigit() }
                minute = minuteStr?.toIntOrNull()
            }
            
            if (hour == null && trimmed.contains("시")) {
                hour = Regex("(\\d{1,2})시").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
            }
            if (minute == null && trimmed.contains("분")) {
                minute = Regex("(\\d{1,2})분").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
            }
            
            if (hour == null) {
                hour = Regex("(\\d{1,2})").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
            }
            if (hour == null) return null
            
            if (isPm && hour < 12) {
                hour += 12
            } else if (isPm && hour == 12) {
                hour = 12
            } else if (isAm && hour == 12) {
                hour = 0
            }
            
            val finalMinute = minute?.coerceIn(0, 59) ?: 0
            "${hour.coerceIn(0, 23).toString().padStart(2, '0')}:${finalMinute.toString().padStart(2, '0')}"
        } catch (e: Exception) {
            android.util.Log.w("HuenDongMinAiAgent", "시간 정규화 실패: $timeStr", e)
            null
        }
    }
    
    /**
     * 날짜 유효성 검증
     */
    private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        return try {
            java.time.LocalDate.of(year, month, day)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Fallback 이벤트 생성
     */
    private fun createFallbackEvent(
        referenceDate: java.time.ZonedDateTime,
        title: String,
        body: String,
        location: String?
    ): Map<String, JsonElement?> {
        val fallbackDateTime = referenceDate.withHour(0).withMinute(0).withSecond(0).withNano(0)
        val startAt = fallbackDateTime.toInstant().toEpochMilli()
        val endAt = startAt + (60 * 60 * 1000)
        
        android.util.Log.w("HuenDongMinAiAgent", "⚠️ Fallback 이벤트 생성: ${fallbackDateTime}")
        
        return mapOf(
            "title" to JsonPrimitive(title),
            "startAt" to JsonPrimitive(startAt.toString()),
            "endAt" to JsonPrimitive(endAt.toString()),
            "location" to (location?.let { JsonPrimitive(it) } ?: JsonPrimitive("")),
            "type" to JsonPrimitive("이벤트"),
            "body" to JsonPrimitive(body)
        )
    }
    
    /**
     * 명시적 날짜 파싱 (예: "2025-10-16", "10월 16일", "10/16")
     */
    private fun parseExplicitDate(
        dateString: String,
        referenceDate: java.time.ZonedDateTime
    ): java.time.ZonedDateTime {
        return try {
            // "2025-10-16" 형식
            if (dateString.matches(Regex("\\d{4}-\\d{1,2}-\\d{1,2}"))) {
                val parts = dateString.split("-")
                java.time.LocalDate.of(
                    parts[0].toInt(),
                    parts[1].toInt(),
                    parts[2].toInt()
                ).atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
            }
            // "10월 16일" 형식
            else if (dateString.contains("월") && dateString.contains("일")) {
                val monthMatch = Regex("(\\d{1,2})월").find(dateString)
                val dayMatch = Regex("(\\d{1,2})일").find(dateString)
                val yearMatch = Regex("(\\d{4})년").find(dateString)
                
                val year = yearMatch?.groupValues?.get(1)?.toInt() ?: referenceDate.year
                val month = monthMatch?.groupValues?.get(1)?.toInt() ?: referenceDate.monthValue
                val day = dayMatch?.groupValues?.get(1)?.toInt() ?: referenceDate.dayOfMonth
                
                java.time.LocalDate.of(year, month, day)
                    .atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
            }
            // "10/16~17" 또는 "11.11~12" 같은 범위 형식 (시작 날짜 사용)
            else if (dateString.matches(Regex("\\d{1,2}[/.]\\d{1,2}~\\d{1,2}"))) {
                val rangeParts = dateString.split("~")
                val startDatePart = rangeParts[0]  // "11.11" 또는 "10/16"
                val parts = startDatePart.split("/", ".")
                val month = parts[0].toInt()
                val day = parts[1].toInt()
                java.time.LocalDate.of(referenceDate.year, month, day)
                    .atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
            }
            // "10/16" 또는 "10.16" 형식
            else if (dateString.matches(Regex("\\d{1,2}[/.]\\d{1,2}"))) {
                val parts = dateString.split("/", ".")
                val month = parts[0].toInt()
                val day = parts[1].toInt()
                java.time.LocalDate.of(referenceDate.year, month, day)
                    .atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
            }
            // 기본값: 기준 날짜
            else {
                referenceDate
            }
        } catch (e: Exception) {
            android.util.Log.w("HuenDongMinAiAgent", "날짜 파싱 실패: $dateString", e)
            referenceDate
        }
    }
    
    /**
     * 상대적 시간 표현 처리 (예: "내일", "다음주 수요일")
     */
    private fun processRelativeTimeExpressions(
        expressions: List<String>,
        baseDate: java.time.ZonedDateTime
    ): java.time.ZonedDateTime {
        var result = baseDate
        
        for (expr in expressions) {
            when {
                expr.contains("내일") -> result = result.plusDays(1)
                expr.contains("모레") -> result = result.plusDays(2)
                expr.contains("다음주") || expr.contains("담주") -> {
                    // 다음 주 월요일 찾기
                    val daysUntilMonday = when (result.dayOfWeek) {
                        java.time.DayOfWeek.MONDAY -> 7L
                        java.time.DayOfWeek.TUESDAY -> 6L
                        java.time.DayOfWeek.WEDNESDAY -> 5L
                        java.time.DayOfWeek.THURSDAY -> 4L
                        java.time.DayOfWeek.FRIDAY -> 3L
                        java.time.DayOfWeek.SATURDAY -> 2L
                        java.time.DayOfWeek.SUNDAY -> 1L
                    }
                    result = result.plusDays(daysUntilMonday)
                    
                    // 요일이 지정된 경우 추가 계산
                    val dayOfWeekMap = mapOf(
                        "월요일" to java.time.DayOfWeek.MONDAY,
                        "화요일" to java.time.DayOfWeek.TUESDAY,
                        "수요일" to java.time.DayOfWeek.WEDNESDAY,
                        "목요일" to java.time.DayOfWeek.THURSDAY,
                        "금요일" to java.time.DayOfWeek.FRIDAY,
                        "토요일" to java.time.DayOfWeek.SATURDAY,
                        "일요일" to java.time.DayOfWeek.SUNDAY
                    )
                    
                    for ((koreanDay, dayOfWeek) in dayOfWeekMap) {
                        if (expr.contains(koreanDay)) {
                            val currentDayOfWeek = result.dayOfWeek.value
                            val targetDayOfWeek = dayOfWeek.value
                            val daysToAdd = (targetDayOfWeek - currentDayOfWeek + 7) % 7
                            if (daysToAdd > 0) {
                                result = result.plusDays(daysToAdd.toLong())
                            }
                            break
                        }
                    }
                }
            }
        }
        
        return result
    }
    
    /**
     * 시간 파싱 (예: "14:00", "오후 3시", "15:00")
     */
    private fun parseTime(timeString: String): Int {
        return try {
            when {
                // "14:00" 형식
                timeString.matches(Regex("\\d{1,2}:\\d{2}")) -> {
                    timeString.split(":")[0].toInt()
                }
                // "오후 3시", "PM 3시" 등 - AM/PM 표기를 우선 처리
                timeString.contains("오후") || timeString.contains("PM") || timeString.contains("pm") -> {
                    val hour = Regex("(\\d{1,2})").find(timeString)?.groupValues?.get(1)?.toInt() ?: 0
                    when {
                        hour == 12 -> 12
                        hour in 1..11 -> hour + 12
                        else -> 12
                    }
                }
                // "오전" 형식
                timeString.contains("오전") || timeString.contains("AM") || timeString.contains("am") -> {
                    val hour = Regex("(\\d{1,2})").find(timeString)?.groupValues?.get(1)?.toInt() ?: 0
                    if (hour == 12) 0 else hour
                }
                // "14시" 형식
                timeString.contains("시") -> {
                    Regex("(\\d{1,2})시").find(timeString)?.groupValues?.get(1)?.toInt() ?: 0
                }
                else -> 0
            }
        } catch (e: Exception) {
            android.util.Log.w("HuenDongMinAiAgent", "시간 파싱 실패: $timeString", e)
            0
        }
    }
    
    /**
     * 분 파싱
     */
    private fun parseMinute(timeString: String): Int {
        return try {
            // "14:30" 형식
            if (timeString.matches(Regex("\\d{1,2}:\\d{2}"))) {
                timeString.split(":")[1].toInt()
            }
            // "30분" 형식
            else if (timeString.contains("분")) {
                Regex("(\\d{1,2})분").find(timeString)?.groupValues?.get(1)?.toInt() ?: 0
            }
            else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun shouldForceTopOfHour(timeAnalysis: TimeAnalysisResult): Boolean {
        if (!timeAnalysis.hasTime) return false
        val originalTime = timeAnalysis.time ?: return false
        return !containsMinuteHint(originalTime)
    }

    private fun containsMinuteHint(timeString: String): Boolean {
        val normalized = timeString.lowercase()
        if (normalized.contains("분") || normalized.contains(":") || normalized.contains("반")) {
            return true
        }
        val minutePattern = Regex("\\d{1,2}\\s*분")
        val hourMinutePattern = Regex("\\d{1,2}\\s*시\\s*\\d{1,2}")
        return minutePattern.containsMatchIn(normalized) || hourMinutePattern.containsMatchIn(normalized)
    }

    private fun parseTimeRangeExpression(raw: String?): Pair<String, String>? {
        if (raw.isNullOrBlank()) return null
        val sanitized = raw
            .replace("에서", "~")
            .replace("부터", "~")
            .replace("까지", "")
            .replace("~ ~", "~")
        val delimiter = when {
            sanitized.contains("~") -> "~"
            sanitized.contains("-") -> "-"
            else -> return null
        }
        val parts = sanitized.split(delimiter, limit = 2).map { it.trim() }
        if (parts.size < 2) return null
        val start = normalizeTimeString(parts[0]) ?: return null
        val end = normalizeTimeString(parts[1]) ?: return null
        return start to end
    }

    private suspend fun isDuplicateEvent(event: Event): Boolean {
        val existing = eventDao.findDuplicateEvent(
            title = event.title,
            startAt = event.startAt,
            location = event.location
        )
        return existing != null
    }
    
    private fun inferTimeFromKeyword(rawTime: String?, referenceDate: String?): String {
        val normalized = rawTime?.lowercase().orEmpty()
        val keywordTimeMap = listOf(
            "새벽" to "05:00",
            "이른 아침" to "06:00",
            "아침" to "09:00",
            "점심" to "12:00",
            "오후" to "15:00",
            "저녁" to "18:00",
            "밤" to "20:00",
            "자정" to "00:00"
        )
        keywordTimeMap.firstOrNull { (keyword, _) -> normalized.contains(keyword) }
            ?.let { return it.second }
        if (normalized.matches(Regex("\\d{1,2}"))) {
            val hour = normalized.toIntOrNull()?.coerceIn(0, 23) ?: 0
            return "${hour.toString().padStart(2, '0')}:00"
        }
        return "00:00"
    }

    private val intentKeywords = listOf(
        "회의", "미팅", "약속", "캘린더", "일정", "면접", "상담",
        "lunch", "dinner", "meeting", "schedule", "appointment", "call",
        "zoom", "teams", "google meet", "conference", "seminar"
    )

    private val locationHints = listOf(
        "층", "호", "빌딩", "타워", "센터", "역", "park", "hall", "room", "호실"
    )

    private fun calculateConfidenceScore(
        baseConfidence: Double?,
        timeAnalysis: TimeAnalysisResult,
        result: AiProcessingResult,
        sourceText: String?,
        sourceType: String
    ): Double {
        var score = baseConfidence ?: 0.5

        // 1) 이벤트 여부 및 개수
        if (result.type.equals("event", ignoreCase = true)) {
            score += 0.2
        } else {
            score -= 0.05
        }
        if (result.events.size > 1) {
            score += 0.05
        }
        if (result.events.isEmpty()) {
            score -= 0.2
        }

        // 2) 시간 분석 신뢰도
        if (timeAnalysis.hasExplicitDate) score += 0.12
        if (timeAnalysis.hasRelativeTime) score += 0.05
        if (timeAnalysis.hasTime) score += 0.12 else score -= 0.05
        if (timeAnalysis.derivedFromRule) score += 0.03
        if (timeAnalysis.resolvedStartEpoch == null) score -= 0.05

        // 3) 텍스트 기반 가중치
        val normalizedText = sourceText?.lowercase().orEmpty()
        val keywordHits = intentKeywords.count { normalizedText.contains(it.lowercase()) }
        score += (keywordHits * 0.03).coerceAtMost(0.12)

        val locationHits = locationHints.count { normalizedText.contains(it.lowercase()) }
        score += (locationHits * 0.02).coerceAtMost(0.06)

        val numberCount = Regex("\\d{1,4}").findAll(normalizedText).count()
        if (numberCount >= 3) score += 0.05 else if (numberCount == 0) score -= 0.05

        when {
            normalizedText.length > 200 -> score += 0.05
            normalizedText.length < 40 -> score -= 0.05
        }
        if (normalizedText.isBlank()) score -= 0.1

        // 4) 소스별 보정
        score += when (sourceType.lowercase()) {
            "gmail" -> 0.05
            "sms" -> 0.03
            "ocr" -> 0.02
            "push_notification" -> -0.05
            "chat" -> 0.04
            else -> 0.0
        }

        // 5) 보정 결과 범위 & 스무딩
        score = score.coerceIn(0.0, 1.0)
        // Confidence 값이 극단적으로 몰리지 않도록 소수점 첫째자리까지 노이즈 추가
        val bucket = (score * 100).toInt()
        val smoothed = bucket / 100.0

        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                "ConfidenceScore",
                "base=${baseConfidence ?: 0.5}, adjusted=$smoothed, source=$sourceType, keywords=$keywordHits, numbers=$numberCount"
            )
        }

        return smoothed
    }

    private fun java.time.ZonedDateTime.formatDateString(): String =
        "%04d-%02d-%02d".format(year, monthValue, dayOfMonth)

    private fun java.time.ZonedDateTime.formatTimeString(): String =
        "%02d:%02d".format(hour, minute)

    private fun buildEventResultMap(
        title: String,
        body: String,
        location: String?,
        startAt: Long,
        endAt: Long,
        needsReview: Boolean
    ): MutableMap<String, JsonElement?> {
        return mutableMapOf<String, JsonElement?>(
            "title" to JsonPrimitive(title),
            "startAt" to JsonPrimitive(startAt.toString()),
            "endAt" to JsonPrimitive(endAt.toString()),
            "location" to (location?.let { JsonPrimitive(it) } ?: JsonPrimitive("")),
            "type" to JsonPrimitive("이벤트"),
            "body" to JsonPrimitive(body)
        ).apply {
            if (needsReview) {
                this["needsReview"] = JsonPrimitive("true")
            }
        }
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
        
        val fullText = "${emailSubject ?: ""}\n${emailBody ?: ""}".trim()
        
        // 먼저 일정 요약 추출로 일정 개수 확인
        val eventSummaries = extractEventSummary(
            text = fullText,
            referenceTimestamp = receivedTimestamp,
            sourceType = "gmail"
        )
        
        android.util.Log.d("HuenDongMinAiAgent", "일정 요약 추출 완료: ${eventSummaries.size}개")

        // 1단계: 시간 분석 (새로운 파이프라인)
        val timeAnalysis = analyzeTimeFromText(
            text = emailBody,
            referenceTimestamp = receivedTimestamp,
            sourceType = "gmail"
        )
        
        // 일정이 2개 이상이면 2단계 방식 사용
        if (eventSummaries.size >= 2) {
            android.util.Log.d("HuenDongMinAiAgent", "일정이 2개 이상이므로 2단계 방식 사용")
            
            // 2단계: 각 일정별로 상세 정보 생성
            val events = eventSummaries.map { summary ->
                createEventFromSummary(
                    summary = summary,
                    originalText = fullText,
                    referenceTimestamp = receivedTimestamp,
                    sourceType = "gmail"
                )
            }.filter { it.isNotEmpty() }
            
            android.util.Log.d("HuenDongMinAiAgent", "2단계 처리 완료: ${events.size}개 일정 생성")

            val baseResult = AiProcessingResult(
                type = "event",
                confidence = 0.9,
                events = events
            )
            val adjustedConfidence = calculateConfidenceScore(
                baseConfidence = baseResult.confidence,
                timeAnalysis = timeAnalysis,
                result = baseResult,
                sourceText = fullText,
                sourceType = "gmail"
            )
            val adjustedResult = baseResult.copy(confidence = adjustedConfidence)

            val firstEvent = adjustedResult.events.firstOrNull()
            val ingestItem = IngestItem(
                id = originalEmailId,
                source = "gmail",
                type = adjustedResult.type ?: "event",
                title = emailSubject,
                body = emailBody,
                timestamp = receivedTimestamp,
                dueDate = firstEvent?.get("startAt")?.jsonPrimitive?.content?.toLongOrNull(),
                confidence = adjustedResult.confidence,
                metaJson = null
            )
            ingestRepository.upsert(ingestItem)

            adjustedResult.events.forEachIndexed { index, eventData ->
                val originalStartAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
                android.util.Log.d("HuenDongMinAiAgent", "Gmail Event ${index + 1} - 최종 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                
                // 모든 Event는 같은 IngestItem을 참조 (원본 데이터 추적용)
                val event = createEventFromAiData(eventData, originalEmailId, "gmail")
                eventDao.upsert(event)
                android.util.Log.d("HuenDongMinAiAgent", "Gmail Event ${index + 1} 저장 완료 - ${event.title}, sourceId: $originalEmailId, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
            }
            
            return@withContext adjustedResult
        }
        
        // 일정이 1개 이하이면 기존 1단계 방식 사용
        android.util.Log.d("HuenDongMinAiAgent", "일정이 1개 이하이므로 기존 1단계 방식 사용")
        
        // 1단계: 시간 분석 (새로운 파이프라인)
        android.util.Log.d("HuenDongMinAiAgent", "시간 분석 완료:")
        android.util.Log.d("HuenDongMinAiAgent", "  - 명시적 날짜: ${timeAnalysis.explicitDate}")
        android.util.Log.d("HuenDongMinAiAgent", "  - 상대적 표현: ${timeAnalysis.relativeTimeExpressions}")
        android.util.Log.d("HuenDongMinAiAgent", "  - 시간: ${timeAnalysis.time}")
        
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
            
            ⏰ 시간 분석 결과 (이미 완료됨):
            - 명시적 날짜: ${timeAnalysis.explicitDate ?: "없음"}
            - 상대적 표현: ${timeAnalysis.relativeTimeExpressions.joinToString(", ") { it }.takeIf { it.isNotEmpty() } ?: "없음"}
            - 시간: ${timeAnalysis.time ?: "없음"}
            
            🔴🔴🔴 Gmail 일정 추출 원칙 🔴🔴🔴

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
        
        // 시간 분석 결과를 사용하여 이벤트 시간 보정
        val correctedEvents = if (result.type == "event" && result.events.isNotEmpty() && 
            (timeAnalysis.hasExplicitDate || timeAnalysis.hasRelativeTime || timeAnalysis.hasTime)) {
            // 시간 분석 결과가 있으면 이를 사용하여 이벤트 시간 보정
            result.events.mapIndexed { index, eventData ->
                val title = eventData["title"]?.jsonPrimitive?.content ?: emailSubject ?: "일정"
                val body = eventData["body"]?.jsonPrimitive?.content ?: emailBody ?: ""
                val location = eventData["location"]?.jsonPrimitive?.content
                
                // 시간 분석 결과를 사용하여 JSON 변환
                val correctedEventData = convertTimeAnalysisToJson(
                    timeAnalysis = timeAnalysis,
                    title = title,
                    body = body,
                    location = location,
                    sourceType = "gmail"
                )
                
                android.util.Log.d("HuenDongMinAiAgent", "Gmail Event ${index + 1} - 시간 분석 결과로 보정됨")
                correctedEventData
            }
        } else {
            // 시간 분석 결과가 없으면 AI 응답 그대로 사용
            result.events
        }
        
        // 보정된 이벤트로 결과 업데이트
        val finalResult = AiProcessingResult(
            type = result.type,
            confidence = result.confidence,
            events = correctedEvents
        )
        val adjustedConfidence = calculateConfidenceScore(
            baseConfidence = finalResult.confidence,
            timeAnalysis = timeAnalysis,
            result = finalResult,
            sourceText = fullText,
            sourceType = "gmail"
        )
        val adjustedResult = finalResult.copy(confidence = adjustedConfidence)
        
        // 모든 Gmail 메시지를 IngestItem으로 저장
        val firstEvent = adjustedResult.events.firstOrNull()
        val ingestItem = IngestItem(
            id = originalEmailId,
            source = "gmail",
            type = adjustedResult.type ?: "note",
            title = emailSubject,
            body = emailBody,
            timestamp = receivedTimestamp,
            dueDate = firstEvent?.get("startAt")?.jsonPrimitive?.content?.toLongOrNull(),
            confidence = adjustedResult.confidence,
            metaJson = null
        )
        ingestRepository.upsert(ingestItem)
        android.util.Log.d("HuenDongMinAiAgent", "Gmail IngestItem 저장 완료 (Type: ${adjustedResult.type}, Events: ${adjustedResult.events.size}개)")
        
        // Event 저장 (일정이 있는 경우만)
        if (adjustedResult.type == "event" && adjustedResult.events.isNotEmpty()) {
            
            // Event 저장 (여러 개 지원)
            adjustedResult.events.forEachIndexed { index: Int, eventData: Map<String, JsonElement?> ->
                val originalStartAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
                android.util.Log.d("HuenDongMinAiAgent", "Gmail Event ${index + 1} - 최종 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                
                // 모든 Event는 같은 IngestItem을 참조 (원본 데이터 추적용)
                val event = createEventFromAiData(eventData, originalEmailId, "gmail")
                eventDao.upsert(event)
                android.util.Log.d("HuenDongMinAiAgent", "Gmail Event ${index + 1} 저장 완료 - ${event.title}, sourceId: $originalEmailId, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
            }
        }
        
        adjustedResult
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
        
        // 먼저 일정 요약 추출로 일정 개수 확인
        val eventSummaries = extractEventSummary(
            text = smsBody,
            referenceTimestamp = receivedTimestamp,
            sourceType = "sms"
        )
        
        android.util.Log.d("HuenDongMinAiAgent", "일정 요약 추출 완료: ${eventSummaries.size}개")

        val timeAnalysis = analyzeTimeFromText(
            text = smsBody,
            referenceTimestamp = receivedTimestamp,
            sourceType = "sms"
        )
        
        // 일정이 2개 이상이면 2단계 방식 사용
        if (eventSummaries.size >= 2) {
            android.util.Log.d("HuenDongMinAiAgent", "일정이 2개 이상이므로 2단계 방식 사용")
            
            // 2단계: 각 일정별로 상세 정보 생성
            val events = eventSummaries.map { summary ->
                createEventFromSummary(
                    summary = summary,
                    originalText = smsBody,
                    referenceTimestamp = receivedTimestamp,
                    sourceType = "sms"
                )
            }.filter { it.isNotEmpty() }
            
            android.util.Log.d("HuenDongMinAiAgent", "2단계 처리 완료: ${events.size}개 일정 생성")

            val baseResult = AiProcessingResult(
                type = "event",
                confidence = 0.9,
                events = events
            )
            val adjustedConfidence = calculateConfidenceScore(
                baseConfidence = baseResult.confidence,
                timeAnalysis = timeAnalysis,
                result = baseResult,
                sourceText = smsBody,
                sourceType = "sms"
            )
            val adjustedResult = baseResult.copy(confidence = adjustedConfidence)

            val firstEvent = adjustedResult.events.firstOrNull()
            val ingestItem = IngestItem(
                id = originalSmsId,
                source = "sms",
                type = adjustedResult.type ?: "event",
                title = smsAddress,
                body = smsBody,
                timestamp = receivedTimestamp,
                dueDate = firstEvent?.get("startAt")?.jsonPrimitive?.content?.toLongOrNull(),
                confidence = adjustedResult.confidence,
                metaJson = null
            )
            ingestRepository.upsert(ingestItem)
            
            // Event 저장
            adjustedResult.events.forEachIndexed { index, eventData ->
                val originalStartAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
                android.util.Log.d("HuenDongMinAiAgent", "SMS Event ${index + 1} - 최종 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                
                // 모든 Event는 같은 IngestItem을 참조 (원본 데이터 추적용)
                val event = createEventFromAiData(eventData, originalSmsId, "sms")
                if (isDuplicateEvent(event)) {
                    android.util.Log.d("HuenDongMinAiAgent", "SMS Event 중복 감지, 건너뜀 - ${event.title}")
                } else {
                    eventDao.upsert(event)
                    android.util.Log.d("HuenDongMinAiAgent", "SMS Event ${index + 1} 저장 완료 - ${event.title}, sourceId: $originalSmsId, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                }
            }
            
            return@withContext adjustedResult
        }
        
        // 일정이 1개 이하이면 기존 1단계 방식 사용
        android.util.Log.d("HuenDongMinAiAgent", "일정이 1개 이하이므로 기존 1단계 방식 사용")
        
        // 1단계: 시간 분석 (새로운 파이프라인)
        android.util.Log.d("HuenDongMinAiAgent", "시간 분석 완료:")
        android.util.Log.d("HuenDongMinAiAgent", "  - 명시적 날짜: ${timeAnalysis.explicitDate}")
        android.util.Log.d("HuenDongMinAiAgent", "  - 상대적 표현: ${timeAnalysis.relativeTimeExpressions}")
        android.util.Log.d("HuenDongMinAiAgent", "  - 시간: ${timeAnalysis.time}")
        
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
            
            📱 SMS 수신 정보 (참고용):
            - SMS 수신 연도: ${smsReceivedDate.year}년
            - SMS 수신 월: ${smsReceivedDate.monthValue}월
            - SMS 수신 일: ${smsReceivedDate.dayOfMonth}일
            - SMS 수신 Epoch ms: ${receivedTimestamp}ms
            
            📅 현재 시간 (참고용):
            - 현재 연도: ${now.year}년
            - 현재 월: ${now.monthValue}월
            - 현재 일: ${now.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            - 현재 Epoch ms: ${now.toInstant().toEpochMilli()}ms
            
            ⏰ 시간 분석 결과 (이미 완료됨):
            - 명시적 날짜: ${timeAnalysis.explicitDate ?: "없음"}
            - 상대적 표현: ${timeAnalysis.relativeTimeExpressions.joinToString(", ") { it }.takeIf { it.isNotEmpty() } ?: "없음"}
            - 시간: ${timeAnalysis.time ?: "없음"}
            
            🔴🔴🔴 SMS 일정 추출 원칙 🔴🔴🔴
            
            **당신의 역할:**
            - SMS 본문에서 일정/약속 정보를 추출하고 구조화된 JSON으로 반환하세요.
            - 시간 계산은 이미 완료되었으므로, 일정 정보(제목, 장소, 본문 등)에 집중하세요.
            - 시간 정보는 시간 분석 결과를 참고하되, 최종 시간 계산은 시스템에서 처리합니다.
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
        
        // 시간 분석 결과를 사용하여 이벤트 시간 보정
        val correctedEvents = if (result.type == "event" && result.events.isNotEmpty() && 
            (timeAnalysis.hasExplicitDate || timeAnalysis.hasRelativeTime || timeAnalysis.hasTime)) {
            // 시간 분석 결과가 있으면 이를 사용하여 이벤트 시간 보정
            result.events.mapIndexed { index, eventData ->
                val title = eventData["title"]?.jsonPrimitive?.content ?: "일정"
                val body = eventData["body"]?.jsonPrimitive?.content ?: smsBody
                val location = eventData["location"]?.jsonPrimitive?.content
                
                // 시간 분석 결과를 사용하여 JSON 변환
                val correctedEventData = convertTimeAnalysisToJson(
                    timeAnalysis = timeAnalysis,
                    title = title,
                    body = body,
                    location = location,
                    sourceType = "sms"
                )
                
                android.util.Log.d("HuenDongMinAiAgent", "SMS Event ${index + 1} - 시간 분석 결과로 보정됨")
                correctedEventData
            }
        } else {
            // 시간 분석 결과가 없으면 AI 응답 그대로 사용
            result.events
        }
        
        // 보정된 이벤트로 결과 업데이트
        val finalResult = AiProcessingResult(
            type = result.type,
            confidence = result.confidence,
            events = correctedEvents
        )
        val adjustedConfidence = calculateConfidenceScore(
            baseConfidence = finalResult.confidence,
            timeAnalysis = timeAnalysis,
            result = finalResult,
            sourceText = smsBody,
            sourceType = "sms"
        )
        val adjustedResult = finalResult.copy(confidence = adjustedConfidence)
        
        // 모든 SMS 메시지를 IngestItem으로 저장 (일정이 없어도 저장)
        val firstEvent = adjustedResult.events.firstOrNull()
        
        // SMS 카테고리 정보 추출 (SmsMessage에서 전달받음)
        // smsAddress에서 카테고리 정보를 추출하기 위해 SmsReader의 분류 함수를 재사용
        val smsCategory = classifySmsCategory(smsAddress, smsBody)
        
        val metaJson = buildString {
            append("{")
            append("\"category\":\"${smsCategory.name}\",")
            append("\"address\":\"$smsAddress\"")
            if (adjustedResult.type == "event" && firstEvent != null) {
                append(",\"event\":true")
            }
            append("}")
        }
        
        val ingestItem = IngestItem(
            id = originalSmsId,
            source = "sms",
            type = adjustedResult.type ?: "note",
            title = smsAddress,
            body = smsBody,
            timestamp = receivedTimestamp,
            dueDate = firstEvent?.get("startAt")?.jsonPrimitive?.content?.toLongOrNull(),
            confidence = adjustedResult.confidence,
            metaJson = metaJson
        )
        ingestRepository.upsert(ingestItem)
        android.util.Log.d("HuenDongMinAiAgent", "SMS IngestItem 저장 완료 (Type: ${adjustedResult.type}, Category: $smsCategory)")
        
        // Event 저장 (일정이 있는 경우만)
        if (adjustedResult.type == "event" && adjustedResult.events.isNotEmpty()) {
            
            // Event 저장 (여러 개 지원)
            adjustedResult.events.forEachIndexed { index: Int, eventData: Map<String, JsonElement?> ->
                val originalStartAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
                android.util.Log.d("HuenDongMinAiAgent", "SMS Event ${index + 1} - 최종 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                
                // 모든 Event는 같은 IngestItem을 참조 (원본 데이터 추적용)
                val event = createEventFromAiData(eventData, originalSmsId, "sms")
                eventDao.upsert(event)
                android.util.Log.d("HuenDongMinAiAgent", "SMS Event ${index + 1} 저장 완료 - ${event.title}, sourceId: $originalSmsId, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
            }
        }
        
        adjustedResult
    }
    
    /**
     * 푸시 알림에서 일정 추출 (Tool: processPushNotificationForEvent)
     */
    suspend fun processPushNotificationForEvent(
        appName: String?,
        notificationTitle: String?,
        notificationText: String?,
        notificationSubText: String?,
        receivedTimestamp: Long,
        originalNotificationId: String
    ): AiProcessingResult = withContext(dispatcher) {
        
        android.util.Log.d("HuenDongMinAiAgent", "푸시 알림 처리 시작 - ID: $originalNotificationId")
        
        // 알림 본문 구성 (제목 + 본문 + 서브텍스트)
        val fullText = buildString {
            notificationTitle?.let { append(it) }
            notificationText?.let { 
                if (isNotEmpty()) append(" - ")
                append(it) 
            }
            notificationSubText?.let { 
                if (isNotEmpty()) append(" - ")
                append(it) 
            }
        }
        
        // 1단계: 시간 분석 (새로운 파이프라인)
        val timeAnalysis = analyzeTimeFromText(
            text = fullText,
            referenceTimestamp = receivedTimestamp,
            sourceType = "push_notification"
        )
        
        android.util.Log.d("HuenDongMinAiAgent", "시간 분석 완료:")
        android.util.Log.d("HuenDongMinAiAgent", "  - 명시적 날짜: ${timeAnalysis.explicitDate}")
        android.util.Log.d("HuenDongMinAiAgent", "  - 상대적 표현: ${timeAnalysis.relativeTimeExpressions}")
        android.util.Log.d("HuenDongMinAiAgent", "  - 시간: ${timeAnalysis.time}")
        
        // 실제 현재 시간 (한국시간)
        val now = java.time.Instant.now().atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        // 알림 수신 시간 (한국시간)
        val notificationReceivedDate = java.time.Instant.ofEpochMilli(receivedTimestamp)
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
            
            ⚠️⚠️⚠️ 절대적으로 중요: 푸시 알림 수신 시간 기준 (한국 표준시 KST, Asia/Seoul, UTC+9) ⚠️⚠️⚠️
            
            📱 푸시 알림 수신 정보 (참고용):
            - 알림 수신 연도: ${notificationReceivedDate.year}년
            - 알림 수신 월: ${notificationReceivedDate.monthValue}월
            - 알림 수신 일: ${notificationReceivedDate.dayOfMonth}일
            - 알림 수신 Epoch ms: ${receivedTimestamp}ms
            
            📅 현재 시간 (참고용):
            - 현재 연도: ${now.year}년
            - 현재 월: ${now.monthValue}월
            - 현재 일: ${now.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            - 현재 Epoch ms: ${now.toInstant().toEpochMilli()}ms
            
            ⏰ 시간 분석 결과 (이미 완료됨):
            - 명시적 날짜: ${timeAnalysis.explicitDate ?: "없음"}
            - 상대적 표현: ${timeAnalysis.relativeTimeExpressions.joinToString(", ") { it }.takeIf { it.isNotEmpty() } ?: "없음"}
            - 시간: ${timeAnalysis.time ?: "없음"}
            
            🔴🔴🔴 푸시 알림 일정 추출 원칙 🔴🔴🔴
            
            **당신의 역할:**
            - 푸시 알림에서 일정/약속 정보를 추출하고 구조화된 JSON으로 반환하세요.
            - 시간 계산은 이미 완료되었으므로, 일정 정보(제목, 장소, 본문 등)에 집중하세요.
            - 시간 정보는 시간 분석 결과를 참고하되, 최종 시간 계산은 시스템에서 처리합니다.
        """.trimIndent()
        
        val userPrompt = """
            다음 푸시 알림을 분석하여 약속/일정이 있는지 확인하고, 있다면 구조화된 JSON으로 반환하세요.
            
            📱 앱 이름: ${appName ?: "알 수 없음"}
            📱 제목: ${notificationTitle ?: "(없음)"}
            📱 본문: ${notificationText ?: "(없음)"}
            📱 서브텍스트: ${notificationSubText ?: "(없음)"}
            
            📅 알림 수신 시간 (모든 시간 계산의 기준):
            - 연도: ${notificationReceivedDate.year}년
            - 월: ${notificationReceivedDate.monthValue}월
            - 일: ${notificationReceivedDate.dayOfMonth}일
            - 요일: ${when (notificationReceivedDate.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "월요일"
                java.time.DayOfWeek.TUESDAY -> "화요일"
                java.time.DayOfWeek.WEDNESDAY -> "수요일"
                java.time.DayOfWeek.THURSDAY -> "목요일"
                java.time.DayOfWeek.FRIDAY -> "금요일"
                java.time.DayOfWeek.SATURDAY -> "토요일"
                java.time.DayOfWeek.SUNDAY -> "일요일"
            }}
            - 알림 수신 Epoch ms: ${receivedTimestamp}ms
            
            📅 현재 시간 (참고용):
            - 현재 연도: ${now.year}년
            - 현재 월: ${now.monthValue}월
            - 현재 일: ${now.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            
            🔴🔴🔴 푸시 알림 처리 순서 (명시적 날짜 우선!) 🔴🔴🔴
            
            **1단계: 명시적 날짜 찾기 (최우선!)**
            
            알림 본문에서 다음 패턴을 찾으세요:
            - "9.30", "10.16" 등 점(.) 구분 → 9월 30일, 10월 16일
            - "9/30", "10/16" 등 슬래시(/) 구분 → 9월 30일, 10월 16일
            - "10월 16일", "9월 30일" 등 한글 → 그대로 인식
            - "2025년 10월 16일" 등 전체 날짜 → 그대로 인식
            - "9.30(화)", "10.16(목)" 등 날짜+요일 → 날짜 우선
            
            🔍 예시:
            - 알림에 "9.30(화) 14시 회의" → ${now.year}년 9월 30일 14:00 ✅
            - 알림에 "10월 16일 오후 3시" → ${now.year}년 10월 16일 15:00 ✅
            
            **2단계: 기준 시점 결정**
            
            - 1단계에서 명시적 날짜를 **찾았으면**: 그 날짜를 기준 시점으로 사용
            - 1단계에서 명시적 날짜가 **없으면**: 알림 수신 시간(${notificationReceivedDate.year}년 ${notificationReceivedDate.monthValue}월 ${notificationReceivedDate.dayOfMonth}일)을 기준 시점으로 사용
            
            🔍 예시:
            - 알림에 "10월 16일 ... 다음주 수요일" → 10월 16일 기준 다음주 수요일 ✅
            - 알림에 날짜 없고 "내일 오후 3시" → 알림 수신일 기준 다음날 15:00 ✅
            
            **3단계: 일정 추출**
            
            - 일정이 있으면 type: "event", events 배열에 추가
            - 일정이 없으면 type: "note", events: []
            
            예시:
            - "안녕하세요. 잘 지내시나요?" → type: "note", events: [] ✅
            - "내일 3시에 만나요" → type: "event", events: [...] ✅
            - "9월 30일 회의 있습니다" → type: "event", events: [...] ✅
            - "다음주 수요일 오후 2시 약속" → type: "event", events: [...] ✅
            
            일반 규칙:
            1. 모든 시간은 한국 표준시(KST, UTC+9) 기준으로 계산하세요!
            2. startAt과 endAt은 반드시 계산된 숫자여야 합니다!
            3. 시간이 명시되지 않은 경우 오전 12시(00:00:00)를 기준으로 하세요!
            4. body는 줄바꿈 없이 한 줄로 작성하세요!
            5. 여러 일정이 있으면 반드시 events 배열에 모두 포함하세요!
        """.trimIndent()
        
        val messages = listOf(
            AiMessage(role = "system", content = systemPrompt),
            AiMessage(role = "user", content = userPrompt)
        )
        
        val response = callOpenAi(messages)
        
        android.util.Log.d("HuenDongMinAiAgent", "=== 푸시 알림 AI 원본 응답 ===")
        android.util.Log.d("HuenDongMinAiAgent", response)
        android.util.Log.d("HuenDongMinAiAgent", "=====================================")
        
        val result = parseAiResponse(response)
        
        android.util.Log.d("HuenDongMinAiAgent", "=== 푸시 알림 AI 응답 분석 ===")
        android.util.Log.d("HuenDongMinAiAgent", "Type: ${result.type}, Confidence: ${result.confidence}")
        android.util.Log.d("HuenDongMinAiAgent", "추출된 이벤트 개수: ${result.events.size}개")
        
        // 시간 분석 결과를 사용하여 이벤트 시간 보정
        val correctedEvents = if (result.type == "event" && result.events.isNotEmpty() && 
            (timeAnalysis.hasExplicitDate || timeAnalysis.hasRelativeTime || timeAnalysis.hasTime)) {
            // 시간 분석 결과가 있으면 이를 사용하여 이벤트 시간 보정
            result.events.mapIndexed { index, eventData ->
                val title = eventData["title"]?.jsonPrimitive?.content ?: (notificationTitle ?: "일정")
                val body = eventData["body"]?.jsonPrimitive?.content ?: fullText
                val location = eventData["location"]?.jsonPrimitive?.content
                
                // 시간 분석 결과를 사용하여 JSON 변환
                val correctedEventData = convertTimeAnalysisToJson(
                    timeAnalysis = timeAnalysis,
                    title = title,
                    body = body,
                    location = location,
                    sourceType = "push_notification"
                )
                
                android.util.Log.d("HuenDongMinAiAgent", "푸시 알림 Event ${index + 1} - 시간 분석 결과로 보정됨")
                correctedEventData
            }
        } else {
            // 시간 분석 결과가 없으면 AI 응답 그대로 사용
            result.events
        }
        
        // 보정된 이벤트로 결과 업데이트
        val finalResult = AiProcessingResult(
            type = result.type,
            confidence = result.confidence,
            events = correctedEvents
        )

        val adjustedConfidence = calculateConfidenceScore(
            baseConfidence = finalResult.confidence,
            timeAnalysis = timeAnalysis,
            result = finalResult,
            sourceText = fullText,
            sourceType = "push_notification"
        )
        val adjustedResult = finalResult.copy(confidence = adjustedConfidence)
        
        // 신뢰도 기준 필터 (0.8 미만이면 저장하지 않음)
        val confidence = adjustedResult.confidence
        if (confidence < 0.8) {
            android.util.Log.d(
                "HuenDongMinAiAgent",
                "푸시 알림 신뢰도 낮음(${String.format("%.2f", confidence)}), 저장 및 표시를 건너뜁니다."
            )
            return@withContext adjustedResult
        }

        // 모든 푸시 알림을 IngestItem으로 저장 (신뢰도 기준 충족)
        val firstEvent = adjustedResult.events.firstOrNull()
        
        val metaJson = buildString {
            append("{")
            append("\"app_name\":\"${appName ?: ""}\",")
            append("\"package_name\":\"\"")
            if (finalResult.type == "event" && firstEvent != null) {
                append(",\"event\":true")
            }
            append("}")
        }
        
        val ingestItem = IngestItem(
            id = originalNotificationId,
            source = "push_notification",
            type = adjustedResult.type ?: "note",
            title = notificationTitle ?: appName ?: "푸시 알림",
            body = fullText,
            timestamp = receivedTimestamp,
            dueDate = firstEvent?.get("startAt")?.jsonPrimitive?.content?.toLongOrNull(),
            confidence = adjustedResult.confidence,
            metaJson = metaJson
        )
        ingestRepository.upsert(ingestItem)
        android.util.Log.d("HuenDongMinAiAgent", "푸시 알림 IngestItem 저장 완료 (Type: ${adjustedResult.type})")
        
        // Event 저장 (일정이 있는 경우만)
        if (adjustedResult.type == "event" && adjustedResult.events.isNotEmpty()) {
            
            // Event 저장 (여러 개 지원)
            adjustedResult.events.forEachIndexed { index: Int, eventData: Map<String, JsonElement?> ->
                val originalStartAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
                android.util.Log.d("HuenDongMinAiAgent", "푸시 알림 Event ${index + 1} - 최종 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                
                // 모든 Event는 같은 IngestItem을 참조 (원본 데이터 추적용)
                val event = createEventFromAiData(eventData, originalNotificationId, "push_notification")
                if (isDuplicateEvent(event)) {
                    android.util.Log.d("HuenDongMinAiAgent", "푸시 알림 Event 중복 감지, 건너뜀 - ${event.title}")
                } else {
                    eventDao.upsert(event)
                    android.util.Log.d("HuenDongMinAiAgent", "푸시 알림 Event ${index + 1} 저장 완료 - ${event.title}, sourceId: $originalNotificationId, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                }
            }
        }
        
        adjustedResult
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
        
        // 먼저 일정 요약 추출로 일정 개수 확인
        val eventSummaries = extractEventSummary(
            text = ocrText,
            referenceTimestamp = currentTimestamp,
            sourceType = "ocr"
        )
        
        android.util.Log.d("HuenDongMinAiAgent", "일정 요약 추출 완료: ${eventSummaries.size}개")
        
        // 일정이 2개 이상이면 2단계 방식 사용
        if (eventSummaries.size >= 2) {
            android.util.Log.d("HuenDongMinAiAgent", "일정이 2개 이상이므로 2단계 방식 사용")
            
            // 2단계: 각 일정별로 상세 정보 생성
            val events = eventSummaries.map { summary ->
                createEventFromSummary(
                    summary = summary,
                    originalText = ocrText,
                    referenceTimestamp = currentTimestamp,
                    sourceType = "ocr"
                )
            }.filter { it.isNotEmpty() }
            
            android.util.Log.d("HuenDongMinAiAgent", "2단계 처리 완료: ${events.size}개 일정 생성")

            // 시간 분석 (2단계 처리에서도 신뢰도 계산을 위해 필요)
            val timeAnalysis = analyzeTimeFromText(
                text = ocrText,
                referenceTimestamp = currentTimestamp,
                sourceType = "ocr"
            )

            val baseResult = AiProcessingResult(
                type = "event",
                confidence = 0.9,
                events = events
            )
            val adjustedConfidence = calculateConfidenceScore(
                baseConfidence = baseResult.confidence,
                timeAnalysis = timeAnalysis,
                result = baseResult,
                sourceText = ocrText,
                sourceType = "ocr"
            )
            val adjustedResult = baseResult.copy(confidence = adjustedConfidence)

            val firstEvent = adjustedResult.events.firstOrNull()
            val ingestItem = IngestItem(
                id = originalOcrId,
                source = "ocr",
                type = adjustedResult.type ?: "event",
                title = firstEvent?.get("title")?.jsonPrimitive?.content ?: "OCR 일정",
                body = ocrText,
                timestamp = currentTimestamp,
                dueDate = firstEvent?.get("startAt")?.jsonPrimitive?.content?.toLongOrNull(),
                confidence = adjustedResult.confidence,
                metaJson = null
            )
            ingestRepository.upsert(ingestItem)
            
            // Event 저장
            adjustedResult.events.forEachIndexed { index, eventData ->
                val originalStartAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
                android.util.Log.d("HuenDongMinAiAgent", "OCR Event ${index + 1} - 최종 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                
                // 모든 Event는 같은 IngestItem을 참조 (원본 데이터 추적용)
                val event = createEventFromAiData(eventData, originalOcrId, "ocr")
                if (isDuplicateEvent(event)) {
                    android.util.Log.d("HuenDongMinAiAgent", "OCR Event 중복 감지, 건너뜀 - ${event.title}")
                } else {
                    eventDao.upsert(event)
                    android.util.Log.d("HuenDongMinAiAgent", "OCR Event ${index + 1} 저장 완료 - ${event.title}, sourceId: $originalOcrId, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                }
            }
            
            return@withContext adjustedResult
        }
        
        // 일정이 1개 이하이면 기존 1단계 방식 사용
        android.util.Log.d("HuenDongMinAiAgent", "일정이 1개 이하이므로 기존 1단계 방식 사용")
        
        // 1단계: 시간 분석 (새로운 파이프라인)
        val timeAnalysis = analyzeTimeFromText(
            text = ocrText,
            referenceTimestamp = currentTimestamp,
            sourceType = "ocr"
        )
        
        android.util.Log.d("HuenDongMinAiAgent", "시간 분석 완료:")
        android.util.Log.d("HuenDongMinAiAgent", "  - 명시적 날짜: ${timeAnalysis.explicitDate}")
        android.util.Log.d("HuenDongMinAiAgent", "  - 상대적 표현: ${timeAnalysis.relativeTimeExpressions}")
        android.util.Log.d("HuenDongMinAiAgent", "  - 시간: ${timeAnalysis.time}")
        
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
            
            ⏰ 시간 분석 결과 (이미 완료됨):
            - 명시적 날짜: ${timeAnalysis.explicitDate ?: "없음"}
            - 상대적 표현: ${timeAnalysis.relativeTimeExpressions.joinToString(", ") { it }.takeIf { it.isNotEmpty() } ?: "없음"}
            - 시간: ${timeAnalysis.time ?: "없음"}
            
            🔴🔴🔴 OCR 일정 추출 원칙 🔴🔴🔴
            
            **당신의 역할:**
            - OCR 텍스트에서 일정/약속 정보를 추출하고 구조화된 JSON으로 반환하세요.
            - **중요**: 시간 계산은 하지 마세요! 날짜와 시간 문자열만 추출하세요.
            - 시스템이 날짜/시간 문자열을 epoch milliseconds로 변환합니다.
            - 일정 정보(제목, 장소, 본문 등)에 집중하세요.
            
            **출력 형식:**
            - 날짜: "YYYY-MM-DD" 형식 (예: "2025-10-30")
            - 시간: "HH:mm" 형식 (예: "14:00")
            - epoch milliseconds는 계산하지 마세요!
        """.trimIndent()
        
        // Few-shot 예시 (하드코딩 - 리소스 로딩 문제 우회)
        val fewShotExamples = """
            
            🎯 **Few-shot 예시:**
            
            **예시 1: 명시적 날짜 (매우 중요!)**
            OCR 텍스트: "2025,10,30.(목) 11:30 회의"
            
            **당신이 해야 할 일:**
            1. "2025,10,30.(목)" 발견 → 날짜 문자열: "2025-10-30" ✅
            2. "11:30" 발견 → 시간 문자열: "11:30" ✅
            3. "회의" 발견 → 제목: "회의"
            
            **출력 (epoch milliseconds 계산하지 않음!):**
            ```json
            {
              "type": "event",
              "confidence": 0.9,
              "events": [{
                "title": "회의",
                "date": "2025-10-30",
                "time": "11:30",
                "location": "",
                "type": "회의",
                "body": "2025년 10월 30일 목요일 11:30 회의"
              }]
            }
            ```
            
            ⚠️ **절대 금지:**
            - ❌ epoch milliseconds 계산 (예: "startAt": 1761631200000)
            - ❌ "2025,10,30"을 "2025,10,29"로 변경
            - ❌ "10월 30일"을 "10월 29일"로 해석
            - ❌ 명시적 날짜를 상대적으로 계산
            
            **예시 2: 한글 날짜**
            OCR 텍스트: "10월 30일 14시 회의"
            
            **당신이 해야 할 일:**
            1. "10월 30일" 발견 → 날짜 문자열: "${now.year}-10-30" ✅ (연도는 현재 연도 사용)
            2. "14시" 발견 → 시간 문자열: "14:00" ✅
            3. "회의" 발견 → 제목: "회의"
            
            **출력:**
            ```json
            {
              "type": "event",
              "confidence": 0.9,
              "events": [{
                "title": "회의",
                "date": "${now.year}-10-30",
                "time": "14:00",
                "location": "",
                "type": "회의",
                "body": "10월 30일 14시 회의"
              }]
            }
            ```
            
            **예시 3: 시간이 없는 경우**
            OCR 텍스트: "11월 15일 행사"
            
            **당신이 해야 할 일:**
            1. "11월 15일" 발견 → 날짜 문자열: "${now.year}-11-15" ✅
            2. 시간 없음 → 시간 문자열: "00:00" (기본값) ✅
            3. "행사" 발견 → 제목: "행사"
            
            **출력:**
            ```json
            {
              "type": "event",
              "confidence": 0.9,
              "events": [{
                "title": "행사",
                "date": "${now.year}-11-15",
                "time": "00:00",
                "location": "",
                "type": "행사",
                "body": "11월 15일 행사"
              }]
            }
            ```
            
            **예시 4: 상대적 날짜 표현 (채팅/메시지)**
            OCR 텍스트: "담주 수욜 동성로 거기서 만나자"
            
            **당신이 해야 할 일:**
            1. "담주 수욜" 발견 → 상대적 날짜 표현
               - "담주" = 다음 주
               - "수욜" = 수요일 (한글 줄임말)
               - 기준 시각이 ${now.year}년 ${now.monthValue}월 ${now.dayOfMonth}일($dayOfWeekKorean)이므로
               - 다음 주 수요일 계산 필요 (시스템이 자동 계산)
               - 날짜 문자열: "YYYY-MM-DD" 형식으로 반환 (예: "2025-10-22") ✅
            2. 시간 없음 → 시간 문자열: "00:00" (기본값) ✅
            3. "동성로" 발견 → 장소: "동성로"
            4. "만나자" 발견 → 제목: "만남" 또는 "약속"
            
            **출력:**
            ```json
            {
              "type": "event",
              "confidence": 0.9,
              "events": [{
                "title": "만남",
                "date": "2025-10-22",
                "time": "00:00",
                "location": "동성로",
                "type": "약속",
                "body": "담주 수욜 동성로에서 만나기로 함"
              }]
            }
            ```
            
            ⚠️ **해석 가이드:**
            - "담주" = 다음 주 (현재 주 + 1주)
            - "수욜" = 수요일 (한글 줄임말: 수요일 → 수욜)
            - "동성로" = 장소명
            - "만나자" = 만남 약속 의도
            - 상대적 날짜는 시스템이 자동으로 계산하므로, 정확한 날짜 계산보다는 표현을 올바르게 인식하는 것이 중요
            
            **예시 5: 상대적 날짜 + 시간대 + 구체적 시간 (채팅/메시지)**
            OCR 텍스트: "내일 오후 1시에 점심 고고? 그러자. 뭐먹고싶어? 짜장면 먹자. ㅇㅋㅇㅋ 내일 1시에 봐"
            
            **당신이 해야 할 일:**
            1. "내일" 발견 → 상대적 날짜 표현
               - 날짜가 명시적으로 없음 → 현재 날짜(${now.year}년 ${now.monthValue}월 ${now.dayOfMonth}일) 기준으로 계산
               - "내일" = 현재 날짜 + 1일
               - 날짜 문자열: "${now.plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))}" ✅
            2. "오후 1시" 또는 "1시" 발견 → 시간 문자열: "13:00" ✅
               - "오후 1시" = 13:00 (24시간 형식)
               - "점심" = 시간대 힌트이지만, "1시"가 명시되어 있으므로 13:00 사용
            3. "점심" 발견 → 제목: "점심 약속" 또는 "점심"
            4. "짜장면" 발견 → 본문에 포함
            
            **출력:**
            ```json
            {
              "type": "event",
              "confidence": 0.9,
              "events": [{
                "title": "점심 약속",
                "date": "${now.plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))}",
                "time": "13:00",
                "location": "",
                "type": "약속",
                "body": "내일 오후 1시에 점심 약속 (짜장면)"
              }]
            }
            ```
            
            ⚠️ **중요 규칙:**
            - 날짜가 명시적으로 없으면 → 현재 날짜 기준으로 계산
            - "내일" = 현재 날짜 + 1일
            - "오후 1시" = 13:00 (24시간 형식)
            - "점심"은 시간대 힌트일 뿐, 구체적 시간("1시")이 있으면 그것을 우선 사용
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
            - "11:30" → "11:30" (문자열로 반환)
            - "14시" → "14:00" (문자열로 반환)
            - "오후 3시" → "15:00" (문자열로 반환)
            - 시간이 없으면 "00:00" 사용
            
            ⚠️ **중요**: epoch milliseconds를 계산하지 마세요! 날짜와 시간 문자열만 반환하세요.
            시스템이 자동으로 epoch milliseconds로 변환합니다.
            
            출력 형식 (JSON만):
            {
              "type": "event",
              "confidence": 0.9,
              "events": [
                {
                  "title": "일정 제목",
                  "date": "2025-10-30",
                  "time": "14:00",
                  "location": "장소",
                  "type": "회의",
                  "body": "OCR 텍스트 요약"
                }
              ]
            }
            
            ⚠️ **주의사항:**
            - "startAt", "endAt" 필드를 사용하지 마세요!
            - "date"와 "time" 필드만 사용하세요!
            - epoch milliseconds 계산은 시스템이 처리합니다!
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
        
        // 시간 분석 결과를 사용하여 이벤트 시간 보정
        val correctedEvents = if (result.type == "event" && result.events.isNotEmpty() && 
            (timeAnalysis.hasExplicitDate || timeAnalysis.hasRelativeTime || timeAnalysis.hasTime)) {
            // 시간 분석 결과가 있으면 이를 사용하여 이벤트 시간 보정
            result.events.mapIndexed { index, eventData ->
                val title = eventData["title"]?.jsonPrimitive?.content ?: "일정"
                val body = eventData["body"]?.jsonPrimitive?.content ?: ocrText
                val location = eventData["location"]?.jsonPrimitive?.content
                
                // 시간 분석 결과를 사용하여 JSON 변환 (OCR만 특별 처리)
                val correctedEventData = convertTimeAnalysisToJson(
                    timeAnalysis = timeAnalysis,
                    title = title,
                    body = body,
                    location = location,
                    sourceType = "ocr"
                )
                
                android.util.Log.d("HuenDongMinAiAgent", "OCR Event ${index + 1} - 시간 분석 결과로 보정됨")
                correctedEventData
            }
        } else {
            // 시간 분석 결과가 없으면 AI 응답 그대로 사용
            result.events
        }
        
        // 보정된 이벤트로 결과 업데이트
        val finalResult = AiProcessingResult(
            type = result.type,
            confidence = result.confidence,
            events = correctedEvents
        )
        val adjustedConfidence = calculateConfidenceScore(
            baseConfidence = finalResult.confidence,
            timeAnalysis = timeAnalysis,
            result = finalResult,
            sourceText = ocrText,
            sourceType = "ocr"
        )
        val adjustedResult = finalResult.copy(confidence = adjustedConfidence)
        
        // Event 저장 (일정인 경우만 IngestItem과 Event 저장)
        if (adjustedResult.type == "event" && adjustedResult.events.isNotEmpty()) {
            // 일정이 있는 경우에만 IngestItem 저장 (원본 보관, 첫 번째 이벤트 정보 사용)
            val firstEvent = adjustedResult.events.firstOrNull()
            val ingestItem = IngestItem(
                id = originalOcrId,
                source = "ocr",
                type = adjustedResult.type,
                title = firstEvent?.get("title")?.jsonPrimitive?.content,
                body = ocrText,
                timestamp = currentTimestamp,
                dueDate = firstEvent?.get("startAt")?.jsonPrimitive?.content?.toLongOrNull(),
                confidence = adjustedResult.confidence,
                metaJson = null
            )
            ingestRepository.upsert(ingestItem)
            android.util.Log.d("HuenDongMinAiAgent", "OCR IngestItem 저장 완료 (일정 있음)")
            
            // Event 저장 (여러 개 지원)
            adjustedResult.events.forEachIndexed { index: Int, eventData: Map<String, JsonElement?> ->
                val originalStartAt = eventData["startAt"]?.jsonPrimitive?.content?.toLongOrNull()
                android.util.Log.d("HuenDongMinAiAgent", "OCR Event ${index + 1} - 최종 시간: ${originalStartAt?.let { java.time.Instant.ofEpochMilli(it) }}")
                
                // 모든 Event는 같은 IngestItem을 참조 (원본 데이터 추적용)
                val event = createEventFromAiData(eventData, originalOcrId, "ocr")
                eventDao.upsert(event)
                android.util.Log.d("HuenDongMinAiAgent", "OCR Event ${index + 1} 저장 완료 - ${event.title}, sourceId: $originalOcrId, 시작: ${event.startAt?.let { java.time.Instant.ofEpochMilli(it) }}")
            }
        }
        
        adjustedResult
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
        val eventType = getOrCreateEventTypeInternal(typeName)
        
        // 불일치 정보가 있으면 body에 JSON으로 저장
        val originalBody = extractedData["body"]?.jsonPrimitive?.content ?: ""
        val validationMismatch = extractedData["validationMismatch"]?.jsonPrimitive?.content == "true"
        val bodyWithMismatchInfo = if (validationMismatch && sourceType == "ocr") {
            val mismatchJson = buildString {
                append("{")
                append("\"originalBody\":\"${originalBody.replace("\"", "\\\"")}\",")
                append("\"validationMismatch\":true,")
                extractedData["llmCalculatedTime"]?.jsonPrimitive?.content?.let {
                    append("\"llmCalculatedTime\":$it,")
                }
                extractedData["codeCalculatedTime"]?.jsonPrimitive?.content?.let {
                    append("\"codeCalculatedTime\":$it,")
                }
                extractedData["chosenSource"]?.jsonPrimitive?.content?.let {
                    append("\"chosenSource\":\"$it\",")
                }
                extractedData["mismatchReason"]?.jsonPrimitive?.content?.let {
                    append("\"mismatchReason\":\"${it.replace("\"", "\\\"")}\"")
                }
                append("}")
            }
            mismatchJson
        } else {
            originalBody
        }
        
        return Event(
            userId = 1L,
            typeId = eventType.id,
            title = extractedData["title"]?.jsonPrimitive?.content ?: "제목 없음",
            body = bodyWithMismatchInfo.takeIf { it.isNotEmpty() },
            startAt = extractedData["startAt"]?.jsonPrimitive?.content?.toLongOrNull(),
            endAt = extractedData["endAt"]?.jsonPrimitive?.content?.toLongOrNull(),
            location = extractedData["location"]?.jsonPrimitive?.content,
            status = if (validationMismatch && sourceType == "ocr") "needs_review" else "pending",
            sourceType = sourceType,
            sourceId = sourceId
        )
    }
    
    private suspend fun getOrCreateEventTypeInternal(typeName: String): EventType {
        val existing = eventTypeDao.getByName(typeName)
        if (existing != null) return existing
        
        val newType = EventType(typeName = typeName)
        val id = eventTypeDao.upsert(newType)
        return newType.copy(id = id)
    }
    
    /**
     * EventType 가져오기 또는 생성 (public)
     */
    suspend fun getOrCreateEventType(typeName: String): EventType = withContext(dispatcher) {
        getOrCreateEventTypeInternal(typeName)
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
    /**
     * 1단계: 텍스트에서 일정 요약 추출
     * 여러 일정이 있을 때 각 일정을 명확히 구분하여 추출
     */
    private suspend fun extractEventSummary(
        text: String,
        referenceTimestamp: Long,
        sourceType: String
    ): List<EventSummary> = withContext(dispatcher) {
        val now = java.time.Instant.now().atZone(java.time.ZoneId.of("Asia/Seoul"))
        val referenceDate = java.time.Instant.ofEpochMilli(referenceTimestamp)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        val systemPrompt = """
            당신은 텍스트에서 모든 일정을 찾아서 간단하게 요약하는 전문가입니다.
            
            📅 기준 시점:
            - 기준 연도: ${referenceDate.year}년
            - 기준 월: ${referenceDate.monthValue}월
            - 기준 일: ${referenceDate.dayOfMonth}일
            
            📅 현재 시간:
            - 현재 연도: ${now.year}년
            - 현재 월: ${now.monthValue}월
            - 현재 일: ${now.dayOfMonth}일
            
            **당신의 역할:**
            - 텍스트에서 모든 일정을 찾아서 간단한 형식으로 요약하세요.
            - 각 일정은 날짜와 제목만 추출하면 됩니다.
            - 시간 계산은 하지 마세요. 날짜만 추출하세요.
            
            **출력 형식:**
            ```json
            {
              "events": [
                {
                  "date": "2025-11-17",
                  "title": "채용 설명회",
                  "timeHint": "14:00"  // 시간이 있으면, 없으면 null
                },
                {
                  "date": "2025-11-16",
                  "title": "사전신청",
                  "timeHint": null
                }
              ]
            }
            ```
            
            **중요 규칙:**
            1. 여러 일정이 있으면 반드시 모두 추출하세요!
            2. 날짜 형식: "YYYY-MM-DD" (예: "2025-11-17")
            3. 연도가 생략된 날짜는 현재 연도(${now.year})를 사용하세요.
            4. "11월 17일" → "2025-11-17"
            5. "11.17" 또는 "11.17(일)" → "2025-11-17"
            6. "~11.16" 또는 "~11.16(일)" → "2025-11-16" (기간의 마감일 사용)
            7. "11월 17일부터" → "2025-11-17" (시작일 추출)
            8. "신청 기간 : ~11.16" → 날짜: "2025-11-16", 제목: "신청 기간" 또는 "사전신청"
            9. 같은 날짜에 여러 일정이 있으면 각각 별도로 추출하세요!
            
            **예시:**
            - "11월 17일부터 진행하는 <대학원 동문선배 멘토링>" → date: "2025-11-17", title: "대학원 동문선배 멘토링"
            - "신청 기간 : ~11.16(일)" → date: "2025-11-16", title: "신청 기간" 또는 "사전신청"
        """.trimIndent()
        
        val userPrompt = """
            다음 텍스트에서 모든 일정을 찾아서 요약하세요.
            
            📝 텍스트:
            $text
        """.trimIndent()
        
        val messages = listOf(
            AiMessage(role = "system", content = systemPrompt),
            AiMessage(role = "user", content = userPrompt)
        )
        
        val response = callOpenAi(messages)
        
        android.util.Log.d("HuenDongMinAiAgent", "=== 1단계: 일정 요약 추출 ===")
        android.util.Log.d("HuenDongMinAiAgent", response)
        
        return@withContext try {
            val cleanedJson = response
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            
            val jsonObj = json.parseToJsonElement(cleanedJson).jsonObject
            val eventsArray = jsonObj["events"]?.jsonArray ?: emptyList()
            
            eventsArray.map { eventElement ->
                val eventObj = eventElement.jsonObject
                EventSummary(
                    date = eventObj["date"]?.jsonPrimitive?.content ?: "",
                    title = eventObj["title"]?.jsonPrimitive?.content ?: "",
                    timeHint = eventObj["timeHint"]?.jsonPrimitive?.content
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("HuenDongMinAiAgent", "일정 요약 추출 실패", e)
            emptyList()
        }
    }
    
    /**
     * 2단계: 각 일정별로 시간 계산 및 상세 정보 생성
     */
    private suspend fun createEventFromSummary(
        summary: EventSummary,
        originalText: String,
        referenceTimestamp: Long,
        sourceType: String
    ): Map<String, JsonElement?> = withContext(dispatcher) {
        val now = java.time.Instant.now().atZone(java.time.ZoneId.of("Asia/Seoul"))
        val referenceDate = java.time.Instant.ofEpochMilli(referenceTimestamp)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        // 날짜 파싱
        val eventDate = try {
            val dateParts = summary.date.split("-")
            if (dateParts.size == 3) {
                java.time.LocalDate.of(
                    dateParts[0].toInt(),
                    dateParts[1].toInt(),
                    dateParts[2].toInt()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("HuenDongMinAiAgent", "날짜 파싱 실패: ${summary.date}", e)
            null
        }
        
        if (eventDate == null) {
            android.util.Log.w("HuenDongMinAiAgent", "날짜 파싱 실패, 기본값 사용")
            return@withContext emptyMap()
        }
        
        // 시간 계산
        val timeStr = summary.timeHint ?: "00:00"
        val timeParts = timeStr.split(":")
        val hour = if (timeParts.size >= 1) timeParts[0].toIntOrNull() ?: 0 else 0
        val minute = if (timeParts.size >= 2) timeParts[1].toIntOrNull() ?: 0 else 0
        
        val eventDateTime = eventDate
            .atTime(hour, minute)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
        
        val startAt = eventDateTime.toInstant().toEpochMilli()
        val endAt = startAt + (60 * 60 * 1000) // 기본 1시간
        
        // 원본 텍스트에서 해당 일정의 상세 정보 추출
        val systemPrompt = """
            당신은 특정 일정에 대한 상세 정보를 추출하는 전문가입니다.
            
            📅 일정 정보:
            - 날짜: ${summary.date}
            - 제목: ${summary.title}
            - 시간 힌트: ${summary.timeHint ?: "없음"}
            
            **당신의 역할:**
            - 원본 텍스트에서 이 일정과 관련된 상세 정보를 추출하세요.
            - 장소, 설명, 추가 정보 등을 찾으세요.
            
            **출력 형식:**
            ```json
            {
              "location": "장소 정보 또는 빈 문자열",
              "body": "일정에 대한 상세 설명",
              "type": "일정 유형 (회의, 약속, 행사 등)"
            }
            ```
        """.trimIndent()
        
        val userPrompt = """
            다음 텍스트에서 "${summary.title}" (${summary.date}) 일정에 대한 상세 정보를 추출하세요.
            
            📝 원본 텍스트:
            $originalText
        """.trimIndent()
        
        val messages = listOf(
            AiMessage(role = "system", content = systemPrompt),
            AiMessage(role = "user", content = userPrompt)
        )
        
        val response = callOpenAi(messages)
        
        android.util.Log.d("HuenDongMinAiAgent", "=== 2단계: 일정 상세 정보 추출 ===")
        android.util.Log.d("HuenDongMinAiAgent", "일정: ${summary.title} (${summary.date})")
        android.util.Log.d("HuenDongMinAiAgent", response)
        
        val detailInfo = try {
            val cleanedJson = response
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            
            json.parseToJsonElement(cleanedJson).jsonObject
        } catch (e: Exception) {
            android.util.Log.w("HuenDongMinAiAgent", "상세 정보 추출 실패, 기본값 사용", e)
            json.parseToJsonElement("""{"location":"","body":"${summary.title}","type":"일정"}""").jsonObject
        }
        
        // 최종 이벤트 데이터 생성
        mapOf(
            "title" to JsonPrimitive(summary.title),
            "startAt" to JsonPrimitive(startAt.toString()),
            "endAt" to JsonPrimitive(endAt.toString()),
            "location" to (detailInfo["location"] ?: JsonPrimitive("")),
            "type" to (detailInfo["type"] ?: JsonPrimitive("일정")),
            "body" to (detailInfo["body"] ?: JsonPrimitive(summary.title))
        )
    }
    
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

/**
 * 1단계에서 추출한 일정 요약 정보
 */
private data class EventSummary(
    val date: String,  // "YYYY-MM-DD" 형식
    val title: String,
    val timeHint: String?  // 시간 힌트 (예: "14:00"), 없으면 null
)

