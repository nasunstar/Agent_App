package com.example.agent_app.backend.utils

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * 암호화 유틸리티
 * AES-256을 사용하여 refresh_token을 암호화/복호화합니다.
 * 
 * 주의: 프로덕션 환경에서는 환경 변수나 키 관리 시스템에서 암호화 키를 안전하게 관리해야 합니다.
 */
object CryptoUtils {
    // TODO: 프로덕션에서는 환경 변수나 KeyStore에서 키를 가져와야 합니다.
    private val encryptionKey: SecretKey by lazy {
        // 개발용: 고정 키 (프로덕션에서는 안전하게 관리)
        val keyString = System.getenv("ENCRYPTION_KEY") 
            ?: "MySecretKey123456789012345678901234567890" // 32바이트 필요
        SecretKeySpec(keyString.toByteArray().sliceArray(0..31), "AES")
    }
    
    private val algorithm = "AES"
    private val transformation = "AES/ECB/PKCS5Padding"
    
    /**
     * 평문 텍스트를 암호화합니다.
     * @param plainText 암호화할 텍스트
     * @return Base64로 인코딩된 암호화된 문자열
     */
    fun encrypt(plainText: String): String {
        try {
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(encryptedBytes)
        } catch (e: Exception) {
            throw RuntimeException("암호화 실패: ${e.message}", e)
        }
    }
    
    /**
     * 암호화된 텍스트를 복호화합니다.
     * @param encryptedText Base64로 인코딩된 암호화된 문자열
     * @return 복호화된 평문 텍스트
     */
    fun decrypt(encryptedText: String): String {
        try {
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey)
            val encryptedBytes = Base64.getDecoder().decode(encryptedText)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw RuntimeException("복호화 실패: ${e.message}", e)
        }
    }
    
    /**
     * 새로운 암호화 키를 생성합니다 (테스트/개발용)
     */
    fun generateKey(): String {
        val keyGenerator = KeyGenerator.getInstance(algorithm)
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()
        return Base64.getEncoder().encodeToString(secretKey.encoded)
    }
}

