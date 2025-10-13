package com.example.agent_app.data.search

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Generates simple deterministic embeddings for text using hashing.
 * This is a lightweight placeholder that can be swapped for a real model later.
 * 한국어 처리를 개선한 버전.
 */
class EmbeddingGenerator(
    private val dimension: Int = DEFAULT_DIMENSION,
) : EmbeddingGeneratorInterface {
    override suspend fun generateEmbedding(text: String): FloatArray {
        if (text.isBlank()) {
            return FloatArray(dimension)
        }
        val tokens = tokenize(text)
        val vector = FloatArray(dimension)
        val digest = MessageDigest.getInstance("SHA-256")
        tokens.forEach { token ->
            val hash = digest.digest(token.toByteArray())
            val buffer = ByteBuffer.wrap(hash).order(ByteOrder.BIG_ENDIAN)
            val ints = IntArray(hash.size / Int.SIZE_BYTES) { idx ->
                buffer.getInt(idx * Int.SIZE_BYTES)
            }
            repeat(dimension) { index ->
                val value = ints[index % ints.size]
                vector[index] += (value % 1000) / 1000f
            }
        }
        return normalize(vector)
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        vector.forEach { value -> sumSquares += value * value }
        val magnitude = sqrt(sumSquares)
        if (magnitude == 0.0) {
            return vector
        }
        for (i in vector.indices) {
            vector[i] = (vector[i] / magnitude).toFloat()
        }
        return vector
    }

    override fun dimension(): Int = dimension

    private fun tokenize(text: String): List<String> {
        val lower = text.lowercase()
        
        // 한국어 특수 패턴 처리
        val koreanPatterns = mutableListOf<String>()
        
        // 구문 패턴 추출
        if (lower.contains("gmail") || lower.contains("이메일") || lower.contains("메일")) {
            koreanPatterns.add("gmail")
        }
        if (lower.contains("github")) {
            koreanPatterns.add("github")
        }
        if (lower.contains("google")) {
            koreanPatterns.add("google")
        }
        if (lower.contains("steam")) {
            koreanPatterns.add("steam")
        }
        if (lower.contains("openai")) {
            koreanPatterns.add("openai")
        }
        
        // 기존 토큰화
        val tokens = lower
            .split(" ", "\n", "\t", ",", ".", "?", "!", ":", ";", "에서", "온", "이", "가", "을", "를", "에", "의")
            .mapNotNull { token ->
                val trimmed = token.trim()
                trimmed.takeIf { it.length >= 2 }
            }
        
        return (koreanPatterns + tokens).distinct()
    }

    companion object {
        private const val DEFAULT_DIMENSION = 64
    }
}
