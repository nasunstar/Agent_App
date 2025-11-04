package com.example.agent_app.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Google OAuth 토큰 암호화/복호화 유틸리티
 * 
 * Android Keystore를 사용하여 토큰을 안전하게 암호화/복호화합니다.
 * AES-256-GCM 암호화 방식을 사용합니다.
 */
class TokenEncryption(private val context: Context) {
    private val keyAlias = "token_encryption_key"
    private val tag = "TokenEncryption"
    
    init {
        // 키가 없으면 생성
        ensureKeyExists()
    }
    
    /**
     * Android Keystore에 암호화 키가 존재하는지 확인하고 없으면 생성
     */
    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
                Log.d(tag, "암호화 키 생성 완료")
            }
        } catch (e: Exception) {
            Log.e(tag, "키 생성/확인 실패", e)
        }
    }
    
    /**
     * 토큰을 암호화합니다.
     * 
     * @param plainText 암호화할 평문 텍스트
     * @return 암호화된 Base64 문자열 (실패 시 원본 반환)
     */
    fun encrypt(plainText: String?): String? {
        if (plainText.isNullOrBlank()) {
            return null
        }
        
        return try {
            val secretKey = getSecretKey() ?: return plainText
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            // IV + 암호화된 데이터를 Base64로 인코딩
            // 형식: "ENC:" + Base64(IV + 암호화된데이터)
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            
            "ENC:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(tag, "토큰 암호화 실패", e)
            // 실패 시 원본 반환 (기존 호환성 유지)
            plainText
        }
    }
    
    /**
     * 암호화된 토큰을 복호화합니다.
     * 
     * @param encryptedText 암호화된 문자열 (ENC: 접두사 포함 또는 없음)
     * @return 복호화된 평문 텍스트 (실패 시 원본 반환)
     */
    fun decrypt(encryptedText: String?): String? {
        if (encryptedText.isNullOrBlank()) {
            return null
        }
        
        return try {
            // "ENC:" 접두사가 없으면 암호화되지 않은 기존 데이터 (마이그레이션)
            if (!encryptedText.startsWith("ENC:")) {
                return encryptedText
            }
            
            val secretKey = getSecretKey() ?: return encryptedText
            val base64Data = encryptedText.removePrefix("ENC:")
            val combined = Base64.decode(base64Data, Base64.NO_WRAP)
            
            val iv = ByteArray(12) // GCM IV는 12바이트
            val encrypted = ByteArray(combined.size - iv.size)
            System.arraycopy(combined, 0, iv, 0, iv.size)
            System.arraycopy(combined, iv.size, encrypted, 0, encrypted.size)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(tag, "토큰 복호화 실패", e)
            // 실패 시 원본 반환 (기존 호환성 유지)
            encryptedText
        }
    }
    
    /**
     * Android Keystore에서 암호화 키 가져오기
     */
    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.getKey(keyAlias, null) as? SecretKey
        } catch (e: Exception) {
            Log.e(tag, "암호화 키 가져오기 실패", e)
            null
        }
    }
}

