package com.example.agent_app.data.search

import com.example.agent_app.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI Embeddings API를 사용한 한국어 최적화 임베딩 생성기
 */
class OpenAIEmbeddingGenerator(
    private val httpClient: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val model: String = DEFAULT_MODEL,
) : EmbeddingGeneratorInterface {
    
    override suspend fun generateEmbedding(text: String): FloatArray {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            // API 키가 없으면 폴백으로 기존 해시 기반 임베딩 사용
            return fallbackEmbeddingGenerator.generateEmbedding(text)
        }
        
        val payload = EmbeddingRequest(
            model = model,
            input = text,
            encodingFormat = "float"
        )
        
        val body = json.encodeToString(EmbeddingRequest.serializer(), payload)
            .toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("https://api.openai.com/v1/embeddings")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        
        return try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                    ?: return fallbackEmbeddingGenerator.generateEmbedding(text)
                
                if (!response.isSuccessful) {
                    android.util.Log.w("OpenAIEmbeddingGenerator", "API 호출 실패: ${response.code}")
                    return fallbackEmbeddingGenerator.generateEmbedding(text)
                }
                
                val embeddingResponse = json.decodeFromString(EmbeddingResponse.serializer(), responseBody)
                val embedding = embeddingResponse.data.firstOrNull()?.embedding
                    ?: return fallbackEmbeddingGenerator.generateEmbedding(text)
                
                embedding.map { it.toFloat() }.toFloatArray()
            }
        } catch (exception: IOException) {
            android.util.Log.e("OpenAIEmbeddingGenerator", "네트워크 오류", exception)
            fallbackEmbeddingGenerator.generateEmbedding(text)
        } catch (exception: Exception) {
            android.util.Log.e("OpenAIEmbeddingGenerator", "임베딩 생성 오류", exception)
            fallbackEmbeddingGenerator.generateEmbedding(text)
        }
    }
    
    override fun dimension(): Int = DEFAULT_DIMENSION
    
    companion object {
        private const val DEFAULT_MODEL = "text-embedding-3-small" // 한국어 지원 모델
        private const val DEFAULT_DIMENSION = 1536 // text-embedding-3-small의 차원
        
        private val fallbackEmbeddingGenerator = EmbeddingGenerator()
        
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
private data class EmbeddingRequest(
    val model: String,
    val input: String,
    val encodingFormat: String = "float"
)

@Serializable
private data class EmbeddingResponse(
    val data: List<EmbeddingData>
)

@Serializable
private data class EmbeddingData(
    val embedding: List<Double>
)
