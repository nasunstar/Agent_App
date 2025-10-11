package com.example.agent_app.openai

import com.example.agent_app.BuildConfig
import com.example.agent_app.domain.chat.model.ChatMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val model: String = DEFAULT_MODEL,
) {
    suspend fun createChatCompletion(messages: List<ChatMessage>): ChatMessage {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            return ChatMessage(
                role = ChatMessage.Role.ASSISTANT,
                content = "OpenAI API 키가 설정되어 있지 않아, 로컬 컨텍스트 요약만 제공합니다."
            )
        }
        val payload = ChatCompletionRequest(
            model = model,
            messages = messages.map { message ->
                ChatCompletionMessage(role = message.role.name.lowercase(), content = message.content)
            },
            temperature = 0.3,
            maxTokens = 600,
        )
        val body = json.encodeToString(ChatCompletionRequest.serializer(), payload)
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                    ?: return fallbackResponse("Empty response from OpenAI")
                if (!response.isSuccessful) {
                    return fallbackResponse("OpenAI error: ${response.code}")
                }
                val completion = json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
                val content = completion.choices.firstOrNull()?.message?.content
                    ?: return fallbackResponse("OpenAI 응답에 메시지가 없습니다.")
                ChatMessage(ChatMessage.Role.ASSISTANT, content.trim())
            }
        } catch (exception: IOException) {
            fallbackResponse("네트워크 오류: ${exception.message}")
        }
    }

    private fun fallbackResponse(message: String): ChatMessage = ChatMessage(
        role = ChatMessage.Role.ASSISTANT,
        content = "$message\n\n컨텍스트 요약을 참고해 주세요."
    )

    companion object {
        private const val DEFAULT_MODEL = "gpt-4o-mini"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }
}

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatCompletionMessage>,
    val temperature: Double,
    @SerialName("max_tokens")
    val maxTokens: Int,
)

@Serializable
private data class ChatCompletionMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<ChatCompletionChoice>,
)

@Serializable
private data class ChatCompletionChoice(
    val message: ChatCompletionMessage,
)
