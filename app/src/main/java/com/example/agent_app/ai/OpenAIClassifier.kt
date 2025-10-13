package com.example.agent_app.ai

import com.example.agent_app.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.agent_app.util.JsonCleaner
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 500
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
            )
        )

        val requestJson = json.encodeToString(OpenAIRequest.serializer(), request)
        println("OpenAI Request JSON: $requestJson") // 디버깅용 로그
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
            
            val openAIResponse = json.decodeFromString(OpenAIResponse.serializer(), responseBody)
            val aiResponse = openAIResponse.choices.firstOrNull()?.message?.content
                ?: throw Exception("No response from AI")

            // JSON 파싱 시도
            try {
                // JSON 정리 (주석 제거 포함)
                val cleanJson = JsonCleaner.cleanJson(aiResponse)
                
                println("Cleaned JSON: $cleanJson") // 디버깅용 로그
                json.decodeFromString(ClassificationResult.serializer(), cleanJson)
            } catch (e: Exception) {
                println("JSON parsing failed: ${e.message}") // 디버깅용 로그
                // JSON 파싱 실패 시 기본값 반환
                ClassificationResult(
                    type = "ingest",
                    confidence = 0.5,
                    extractedData = mapOf("raw_response" to JsonPrimitive(aiResponse))
                )
            }
        } catch (e: Exception) {
            // API 호출 실패 시 기본값 반환
            ClassificationResult(
                type = "ingest",
                confidence = 0.0,
                extractedData = mapOf("error" to JsonPrimitive(e.message ?: "Unknown error"))
            )
        }
    }
    
    // 푸시 알림 분류 메서드 추가
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
            )
        )

        val requestJson = json.encodeToString(OpenAIRequest.serializer(), request)
        println("OpenAI Request JSON: $requestJson") // 디버깅용 로그
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
            
            val openAIResponse = json.decodeFromString(OpenAIResponse.serializer(), responseBody)
            val aiResponse = openAIResponse.choices.firstOrNull()?.message?.content
                ?: throw Exception("No response from AI")

            // JSON 파싱 시도
            try {
                // JSON 정리 (주석 제거 포함)
                val cleanJson = JsonCleaner.cleanJson(aiResponse)
                
                println("Cleaned JSON: $cleanJson") // 디버깅용 로그
                json.decodeFromString(ClassificationResult.serializer(), cleanJson)
            } catch (e: Exception) {
                println("JSON parsing failed: ${e.message}") // 디버깅용 로그
                // JSON 파싱 실패 시 기본값 반환
                ClassificationResult(
                    type = "ingest",
                    confidence = 0.5,
                    extractedData = mapOf("raw_response" to JsonPrimitive(aiResponse))
                )
            }
        } catch (e: Exception) {
            // API 호출 실패 시 기본값 반환
            ClassificationResult(
                type = "ingest",
                confidence = 0.0,
                extractedData = mapOf("error" to JsonPrimitive(e.message ?: "Unknown error"))
            )
        }
    }
}
