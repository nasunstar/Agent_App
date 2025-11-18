@file:OptIn(InternalSerializationApi::class)

package com.example.agent_app.ai

import com.example.agent_app.BuildConfig
import com.example.agent_app.util.JsonCleaner
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.3,
    @SerialName("max_tokens")
    val maxTokens: Int = 500
)

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIResponse(
    val choices: List<OpenAIChoice>
)

@Serializable
data class OpenAIChoice(
    val message: OpenAIMessage
)

@Serializable
data class ClassificationResult(
    val type: String, // "contact", "event", "note", "ingest"
    val confidence: Double,
    val extractedData: Map<String, JsonElement?> = emptyMap()
)

class OpenAIClassifier {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun classifyEmail(subject: String?, body: String?): ClassificationResult {
        val content = buildString {
            append("이메일을 분석해서 다음 카테고리 중 하나로 분류해주세요. 가능한 한 구체적으로 분류해주세요:\n\n")
            append("📞 CONTACT (연락처):\n")
            append("- 새로운 사람과의 연락 (소개, 인사, 연락처 교환)\n")
            append("- 비즈니스 연락 (영업, 협업 제안, 파트너십)\n")
            append("- 연락처 정보 교환 (전화번호, 이메일 주소 등)\n")
            append("- 단순한 인사나 안부 문의 (약속이나 일정이 없는 경우)\n\n")
            append("📅 EVENT (일정/이벤트):\n")
            append("- 회의, 미팅, 약속 (시간과 장소가 명시된 경우)\n")
            append("- 이벤트 초대 (생일파티, 결혼식, 회식 등)\n")
            append("- 일정 관련 알림 (리마인더, 스케줄 변경)\n")
            append("- 만나자, 만날까, 약속, 미팅 등의 표현이 포함된 경우\n\n")
            append("📝 NOTE (노트/메모):\n")
            append("- 중요한 정보나 알림 (계정 보안, 결제, 업데이트)\n")
            append("- 할 일이나 작업 관련 내용\n")
            append("- 개인적인 메모나 기록할 내용\n")
            append("- 서비스 알림이나 시스템 메시지\n")
            append("- 뉴스레터, 마케팅 이메일\n")
            append("- 기타 모든 이메일 (위 카테고리에 해당하지 않는 경우)\n\n")
            append("제목: ${subject ?: "없음"}\n")
            append("내용: ${body ?: "없음"}\n\n")
            append("⚠️ 중요: 가능한 한 구체적으로 분류하고, null 대신 실제 내용을 추출해주세요.\n")
            append("🚨 특히 주의: '만나자', '약속', '미팅', '회의' 등의 표현이 있으면 반드시 EVENT로 분류하세요!\n")
            append("JSON 형태로 응답해주세요:\n")
            append("{\n")
            append("  \"type\": \"분류결과\",\n")
            append("  \"confidence\": 0.0-1.0,\n")
            append("  \"extractedData\": {\n")
            append("    \"name\": \"이름 (contact인 경우, 없으면 null)\",\n")
            append("    \"email\": \"이메일 (contact인 경우, 없으면 null)\",\n")
            append("    \"phone\": \"전화번호 (contact인 경우, 없으면 null)\",\n")
            append("    \"title\": \"제목 (event/note인 경우, 없으면 null)\",\n")
            append("    \"startAt\": \"시작시간 epoch ms (event인 경우, 없으면 null) - 주석 없이 숫자만 입력\",\n")
            append("    \"endAt\": \"종료시간 epoch ms (event인 경우, 없으면 null) - 주석 없이 숫자만 입력\",\n")
            append("    \"location\": \"장소 (event인 경우, 없으면 null)\",\n")
            append("    \"type\": \"이벤트 타입 (event인 경우, 없으면 null)\",\n")
            append("    \"body\": \"내용 (note인 경우, 핵심 내용 추출)\"\n")
            append("  }\n")
            append("}\n")
            append("\n⚠️ 중요: JSON 응답에 주석(//)이나 설명을 포함하지 마세요. 순수한 JSON만 반환하세요.")
        }

        val request = OpenAIRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                OpenAIMessage(
                    role = "system",
                    content = "당신은 이메일 분류 전문가입니다. 이메일을 분석해서 가능한 한 구체적으로 분류해주세요. 'ingest'는 절대 사용하지 마세요. 모든 이메일을 contact, event, note 중 하나로 분류해주세요. 서비스 알림, 시스템 메시지, 뉴스레터, 마케팅 이메일 등은 모두 'note'로 분류해주세요."
                ),
                OpenAIMessage(
                    role = "user",
                    content = content
                )
            ),
            maxTokens = 500
        )

        return executeClassification(request)
    }

    suspend fun classifyPushNotification(title: String?, body: String?): ClassificationResult {
        val content = buildString {
            append("푸시 알림을 분석해서 다음 카테고리 중 하나로 분류해주세요:\n")
            append("- contact: 연락처 정보 (이름, 이메일, 전화번호 등)\n")
            append("- event: 일정/이벤트 (회의, 약속, 이벤트 등)\n")
            append("- note: 노트/메모 (중요한 정보, 할 일 등)\n")
            append("- ingest: 일반 알림 (분류 불가능한 경우)\n\n")
            append("알림 제목: ${title ?: "없음"}\n")
            append("알림 내용: ${body ?: "없음"}\n\n")
            append("JSON 형태로 응답해주세요:\n")
            append("{\n")
            append("  \"type\": \"분류결과\",\n")
            append("  \"confidence\": 0.0-1.0,\n")
            append("  \"extractedData\": {\n")
            append("    \"name\": \"이름 (contact인 경우)\",\n")
            append("    \"email\": \"이메일 (contact인 경우)\",\n")
            append("    \"phone\": \"전화번호 (contact인 경우)\",\n")
            append("    \"title\": \"제목 (event/note인 경우)\",\n")
            append("    \"startAt\": \"시작시간 epoch ms (event인 경우)\",\n")
            append("    \"endAt\": \"종료시간 epoch ms (event인 경우)\",\n")
            append("    \"location\": \"장소 (event인 경우)\",\n")
            append("    \"type\": \"이벤트 타입 (event인 경우)\",\n")
            append("    \"body\": \"내용 (note인 경우)\"\n")
            append("  }\n")
            append("}")
        }

        val request = OpenAIRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                OpenAIMessage(
                    role = "system",
                    content = "당신은 푸시 알림 분류 전문가입니다. 푸시 알림 내용을 분석해서 적절한 카테고리로 분류하고 관련 정보를 추출해주세요."
                ),
                OpenAIMessage(
                    role = "user",
                    content = content
                )
            ),
            maxTokens = 500
        )

        return executeClassification(request)
    }

    suspend fun parseScheduleFromText(rawText: String): ClassificationResult {
        val content = buildString {
            append("다음은 사용자가 다른 앱에서 공유한 이미지에서 OCR로 추출한 텍스트입니다.\n")
            append("텍스트 안에서 일정 제목, 시작 시간, 종료 시간, 장소, 추가 메모를 구조화된 데이터로 추출해주세요.\n")
            append("가능하다면 Asia/Seoul (UTC+9) 기준으로 시간을 해석하여 epoch millisecond 값으로 제공하고, 정보가 없다면 null을 사용하세요.\n")
            append("날짜와 시간이 범위로 주어지면 시작과 종료를 모두 추정하고, 하나만 있으면 나머지는 null로 둡니다.\n")
            append("텍스트:\n")
            append(rawText.ifBlank { "(내용 없음)" })
            append("\n\nJSON 형태로만 응답하고, 아래 형식을 반드시 지켜주세요:\n")
            append("{\n")
            append("  \"type\": \"event\",\n")
            append("  \"confidence\": 0.0-1.0,\n")
            append("  \"extractedData\": {\n")
            append("    \"title\": \"일정 제목 또는 핵심 문구\",\n")
            append("    \"startAt\": \"시작 시간 epoch ms (없으면 null)\",\n")
            append("    \"endAt\": \"종료 시간 epoch ms (없으면 null)\",\n")
            append("    \"location\": \"장소 (없으면 null)\",\n")
            append("    \"type\": \"이벤트 타입 또는 카테고리 (없으면 null)\",\n")
            append("    \"body\": \"추가 메모나 설명 (없으면 null)\"\n")
            append("  }\n")
            append("}\n")
            append("⚠️ 중요: 출력에는 설명이나 주석을 포함하지 말고, 모든 문자열은 따옴표로 감싸세요.")
        }

        val request = OpenAIRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                OpenAIMessage(
                    role = "system",
                    content = "당신은 일정 추출 비서입니다. OCR로 얻은 자유 형식의 텍스트에서 회의나 약속 정보를 찾아 구조화된 JSON으로 반환하세요."
                ),
                OpenAIMessage(
                    role = "user",
                    content = content
                )
            ),
            maxTokens = 500
        )

        return executeClassification(request)
    }

    private suspend fun executeClassification(request: OpenAIRequest): ClassificationResult {
        val requestJson = json.encodeToString(OpenAIRequest.serializer(), request)
        println("OpenAI Request JSON: $requestJson")
        val requestBody = requestJson.toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                // 에러 응답 JSON 파싱 시도
                val errorMessage = try {
                    val errorJson = Json.parseToJsonElement(responseBody) as JsonObject
                    val errorObj = errorJson["error"] as? JsonObject
                    val message = (errorObj?.get("message") as? JsonPrimitive)?.content
                    
                    when (response.code) {
                        429 -> {
                            if (message?.contains("quota", ignoreCase = true) == true) {
                                "OpenAI API 할당량을 초과했습니다. 계정의 요금제와 결제 정보를 확인해주세요."
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
                        else -> "OpenAI API 오류: ${response.code}"
                    }
                }
                
                throw Exception(errorMessage)
            }

            val openAIResponse = json.decodeFromString(OpenAIResponse.serializer(), responseBody)
            val aiResponse = openAIResponse.choices.firstOrNull()?.message?.content
                ?: throw Exception("No response from AI")

            try {
                val cleanJson = JsonCleaner.cleanJson(aiResponse)
                println("Cleaned JSON: $cleanJson")
                json.decodeFromString(ClassificationResult.serializer(), cleanJson)
            } catch (e: Exception) {
                println("JSON parsing failed: ${e.message}")
                ClassificationResult(
                    type = "ingest",
                    confidence = 0.5,
                    extractedData = mapOf("raw_response" to JsonPrimitive(aiResponse))
                )
            }
        } catch (e: Exception) {
            ClassificationResult(
                type = "ingest",
                confidence = 0.0,
                extractedData = mapOf("error" to JsonPrimitive(e.message ?: "Unknown error"))
            )
        }
    }
}
