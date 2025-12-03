package com.example.agent_app.util

/**
 * 전화번호 정규화 유틸리티
 */
object PhoneNumberUtils {
    /**
     * 전화번호를 정규화 (하이픈 제거, 공백 제거)
     * 예: "010-1234-5678" -> "01012345678"
     */
    fun normalize(phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank()) return ""
        return phoneNumber.replace(Regex("[^0-9]"), "")
    }
    
    /**
     * 전화번호가 일치하는지 확인 (정규화 후 비교)
     */
    fun matches(phone1: String?, phone2: String?): Boolean {
        if (phone1.isNullOrBlank() || phone2.isNullOrBlank()) return false
        return normalize(phone1) == normalize(phone2)
    }
    
    /**
     * 전화번호가 포함되어 있는지 확인 (정규화 후 비교)
     */
    fun contains(phoneNumber: String?, searchNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank() || searchNumber.isNullOrBlank()) return false
        val normalizedPhone = normalize(phoneNumber)
        val normalizedSearch = normalize(searchNumber)
        return normalizedPhone.contains(normalizedSearch) || normalizedSearch.contains(normalizedPhone)
    }
}

