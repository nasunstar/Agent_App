package com.example.agent_app.data.search

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Generates simple deterministic embeddings for text using hashing.
 * This is a lightweight placeholder that can be swapped for a real model later.
 */
class EmbeddingGenerator(
    private val dimension: Int = DEFAULT_DIMENSION,
) {
    fun generateEmbedding(text: String): FloatArray {
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

    fun dimension(): Int = dimension

    private fun tokenize(text: String): List<String> = text
        .lowercase()
        .split(" ", "\n", "\t", ",", ".", "?", "!", ":", ";")
        .mapNotNull { token ->
            val trimmed = token.trim()
            trimmed.takeIf { it.length >= 2 }
        }

    companion object {
        private const val DEFAULT_DIMENSION = 64
    }
}
