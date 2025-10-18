package com.example.agent_app.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray

/**
 * AI 에이전트 "HuenDongMin"을 위한 Tool 정의
 * 
 * 세 가지 핵심 기능:
 * 1. processGmailForEvent - Gmail 약속 분석
 * 2. createEventFromImage - OCR 기반 일정 생성
 * 3. searchPersonalData - 개인 데이터 검색
 */
object HuenDongMinAiTools {
    
    /**
     * Tool 1: Gmail 약속 분석 도구
     * 
     * 목표: Gmail 메시지에서 약속/일정을 감지하고 구조화된 Event로 변환
     */
    fun getProcessGmailForEventTool() = ToolDefinition(
        type = "function",
        function = FunctionDefinition(
            name = "processGmailForEvent",
            description = """
                새로운 Gmail 메시지를 분석하여 약속이나 일정을 감지하고, 이를 구조화된 Event 데이터로 변환합니다.
                
                핵심 원칙:
                1. 시간 인식: received_timestamp를 기준으로 모든 상대 시간("내일", "다음 주")을 절대 시간으로 변환
                2. 명확한 근거: 입력 텍스트에 명확한 근거가 있어야 함
                3. 신뢰도: 추측이 필요한 경우 confidence 점수를 낮게 설정
                
                출력 형식:
                {
                  "type": "event" | "contact" | "note",
                  "confidence": 0.0 ~ 1.0,
                  "extractedData": {
                    "title": "일정 제목",
                    "startAt": epoch_milliseconds,
                    "endAt": epoch_milliseconds | null,
                    "location": "장소" | null,
                    "body": "요약"
                  }
                }
            """.trimIndent(),
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "email_subject" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("이메일 제목")
                    )),
                    "email_body" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("발신자, 수신자 정보가 포함된 이메일 전체 본문")
                    )),
                    "received_timestamp" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("이메일 수신 시각 (Epoch Milliseconds). 모든 상대 시간 계산의 기준점")
                    )),
                    "original_email_id" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("Gmail 메시지의 고유 ID")
                    ))
                )),
                "required" to JsonArray(listOf(
                    JsonPrimitive("email_subject"),
                    JsonPrimitive("email_body"),
                    JsonPrimitive("received_timestamp"),
                    JsonPrimitive("original_email_id")
                ))
            ))
        )
    )
    
    /**
     * Tool 2: 이미지(OCR) 기반 일정 생성 도구
     * 
     * 목표: 스크린샷 이미지에서 약속/일정 정보를 인식하고 구조화된 Event로 변환
     */
    fun getCreateEventFromImageTool() = ToolDefinition(
        type = "function",
        function = FunctionDefinition(
            name = "createEventFromImage",
            description = """
                사용자가 공유한 스크린샷 이미지에서 약속/일정 정보를 인식하고 구조화된 Event 데이터로 변환합니다.
                
                실행 논리:
                1. [1순위] 이미지 직접 분석: 멀티모달 능력을 활용하여 이미지 내 텍스트와 구조(표, 대화창)를 종합 이해
                2. [2순위] OCR 텍스트 분석: OCR 텍스트가 제공된 경우 이를 분석
                3. 한글 인식 문제: OCR 오인식이 있어도 문맥 파악하여 정보 추출 (예: "모레 오 T 3 시" → "모레 오후 3시")
                4. current_timestamp 기준으로 상대 시간을 절대 시간으로 변환
                
                출력 형식:
                {
                  "type": "event",
                  "confidence": 0.0 ~ 1.0,
                  "extractedData": {
                    "title": "일정 제목",
                    "startAt": epoch_milliseconds,
                    "endAt": epoch_milliseconds | null,
                    "location": "장소" | null,
                    "body": "원본 OCR 텍스트 전체"
                  }
                }
            """.trimIndent(),
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "ocr_text" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("이미지에서 추출된 OCR 텍스트")
                    )),
                    "current_timestamp" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("현재 시각 (Epoch Milliseconds). 모든 상대 시간 계산의 기준점")
                    )),
                    "original_ocr_id" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("OCR 데이터의 고유 ID")
                    ))
                )),
                "required" to JsonArray(listOf(
                    JsonPrimitive("ocr_text"),
                    JsonPrimitive("current_timestamp"),
                    JsonPrimitive("original_ocr_id")
                ))
            ))
        )
    )
    
    /**
     * Tool 3: 개인 데이터 검색 및 응답 챗봇 도구
     * 
     * 목표: 사용자 질문 의도 파악 → 로컬 DB 검색 → 자연어 답변 생성
     */
    fun getSearchPersonalDataTool() = ToolDefinition(
        type = "function",
        function = FunctionDefinition(
            name = "searchPersonalData",
            description = """
                사용자의 자연어 질문 의도를 파악하여 로컬 데이터베이스를 검색하고, 검색 결과를 바탕으로 답변합니다.
                
                실행 논리:
                1. user_query 분석하여 QueryFilters 생성:
                   - 시간 필터: "내일", "이번 주" → current_timestamp 기준 절대 시간 변환
                   - 키워드 필터: 핵심 명사 추출 (예: "김철수", "프로젝트", "회의")
                   - 소스 필터: "이메일에서", "문자 온 거" → source 지정
                
                2. 생성된 QueryFilters로 HybridSearchEngine 검색
                   (RDB 필터 + FTS 텍스트 검색 + 벡터 유사도 검색)
                
                3. 검색 결과를 질문과 관련성 높은 순으로 정렬
                
                4. 자연스러운 한국어 답변 생성 (반드시 검색 데이터에 근거)
                
                출력 예시:
                - "내일(10월 19일) 오후 3시에 '프로젝트 회의' 일정이 있습니다."
                - "김철수 님으로부터 어제 오후 5시 12분에 '회의록 공유' 이메일을 받았습니다."
                - "죄송하지만 관련 정보를 찾을 수 없었습니다."
            """.trimIndent(),
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "user_query" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("사용자의 자연어 질문 (예: '내일 뭐해?', '김철수한테 받은 연락 있어?')")
                    )),
                    "current_timestamp" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("현재 시각 (Epoch Milliseconds). 시간 표현 변환의 기준점")
                    )),
                    "search_filters" to JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "description" to JsonPrimitive("검색 필터 (시간 범위, 키워드, 소스 등)"),
                        "properties" to JsonObject(mapOf(
                            "start_time_millis" to JsonObject(mapOf(
                                "type" to JsonPrimitive("integer"),
                                "description" to JsonPrimitive("검색 시작 시간 (Epoch ms)")
                            )),
                            "end_time_millis" to JsonObject(mapOf(
                                "type" to JsonPrimitive("integer"),
                                "description" to JsonPrimitive("검색 종료 시간 (Epoch ms)")
                            )),
                            "keywords" to JsonObject(mapOf(
                                "type" to JsonPrimitive("array"),
                                "items" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                                "description" to JsonPrimitive("검색 키워드 목록")
                            )),
                            "source" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("데이터 출처 필터 (gmail, ocr 등)")
                            ))
                        ))
                    ))
                )),
                "required" to JsonArray(listOf(
                    JsonPrimitive("user_query"),
                    JsonPrimitive("current_timestamp")
                ))
            ))
        )
    )
    
    /**
     * 모든 Tool 목록 반환
     */
    fun getAllTools() = listOf(
        getProcessGmailForEventTool(),
        getCreateEventFromImageTool(),
        getSearchPersonalDataTool()
    )
}

// Tool 관련 데이터 클래스들
@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

