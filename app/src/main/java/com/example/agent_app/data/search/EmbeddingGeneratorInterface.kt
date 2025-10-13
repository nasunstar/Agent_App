package com.example.agent_app.data.search

/**
 * 임베딩 생성기 인터페이스
 * 다양한 임베딩 생성 구현체를 통합하기 위한 공통 인터페이스
 */
interface EmbeddingGeneratorInterface {
    /**
     * 주어진 텍스트에 대한 임베딩 벡터를 생성합니다.
     * @param text 임베딩을 생성할 텍스트
     * @return 임베딩 벡터 (FloatArray)
     */
    suspend fun generateEmbedding(text: String): FloatArray
    
    /**
     * 임베딩 벡터의 차원을 반환합니다.
     * @return 임베딩 벡터의 차원 수
     */
    fun dimension(): Int
}
