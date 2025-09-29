package com.example.agent_app.ai

import com.example.agent_app.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    val extractedData: Map<String, String?> = emptyMap()
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
            append("이메일을 분석해서 다음 카테고리 중 하나로 분류해주세요:\n")
            append("- contact: 연락처 정보 (이름, 이메일, 전화번호 등)\n")
            append("- event: 일정/이벤트 (회의, 약속, 이벤트 등)\n")
            append("- note: 노트/메모 (중요한 정보, 할 일, 알림 등)\n")
            append("- ingest: 일반 이메일 (분류 불가능한 경우)\n\n")
            append("제목: ${subject ?: "없음"}\n")
            append("내용: ${body ?: "없음"}\n\n")
            append("중요: 가능한 한 많은 정보를 추출해주세요. null 대신 실제 내용을 넣어주세요.\n")
            append("JSON 형태로 응답해주세요:\n")
            append("{\n")
            append("  \"type\": \"분류결과\",\n")
            append("  \"confidence\": 0.0-1.0,\n")
            append("  \"extractedData\": {\n")
            append("    \"name\": \"이름 (contact인 경우, 없으면 null)\",\n")
            append("    \"email\": \"이메일 (contact인 경우, 없으면 null)\",\n")
            append("    \"phone\": \"전화번호 (contact인 경우, 없으면 null)\",\n")
            append("    \"title\": \"제목 (event/note인 경우, 없으면 null)\",\n")
            append("    \"startAt\": \"시작시간 epoch ms (event인 경우, 없으면 null)\",\n")
            append("    \"endAt\": \"종료시간 epoch ms (event인 경우, 없으면 null)\",\n")
            append("    \"location\": \"장소 (event인 경우, 없으면 null)\",\n")
            append("    \"type\": \"이벤트 타입 (event인 경우, 없으면 null)\",\n")
            append("    \"body\": \"내용 (note인 경우, 핵심 내용 추출)\"\n")
            append("  }\n")
            append("}")
        }

        val request = OpenAIRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                OpenAIMessage(
                    role = "system",
                    content = "당신은 이메일 분류 전문가입니다. 이메일 내용을 분석해서 적절한 카테고리로 분류하고 관련 정보를 추출해주세요."
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
                // ```json``` 코드 블록 제거
                val cleanJson = aiResponse
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()
                
                println("Cleaned JSON: $cleanJson") // 디버깅용 로그
                json.decodeFromString(ClassificationResult.serializer(), cleanJson)
            } catch (e: Exception) {
                println("JSON parsing failed: ${e.message}") // 디버깅용 로그
                // JSON 파싱 실패 시 기본값 반환
                ClassificationResult(
                    type = "ingest",
                    confidence = 0.5,
                    extractedData = mapOf("raw_response" to aiResponse)
                )
            }
        } catch (e: Exception) {
            // API 호출 실패 시 기본값 반환
            ClassificationResult(
                type = "ingest",
                confidence = 0.0,
                extractedData = mapOf("error" to (e.message ?: "Unknown error"))
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
                // ```json``` 코드 블록 제거
                val cleanJson = aiResponse
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()
                
                println("Cleaned JSON: $cleanJson") // 디버깅용 로그
                json.decodeFromString(ClassificationResult.serializer(), cleanJson)
            } catch (e: Exception) {
                println("JSON parsing failed: ${e.message}") // 디버깅용 로그
                // JSON 파싱 실패 시 기본값 반환
                ClassificationResult(
                    type = "ingest",
                    confidence = 0.5,
                    extractedData = mapOf("raw_response" to aiResponse)
                )
            }
        } catch (e: Exception) {
            // API 호출 실패 시 기본값 반환
            ClassificationResult(
                type = "ingest",
                confidence = 0.0,
                extractedData = mapOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }
}
