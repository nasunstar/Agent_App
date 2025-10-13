package com.example.agent_app.util

/**
 * JSON 문자열에서 주석과 불필요한 문자를 제거하는 유틸리티
 */
object JsonCleaner {
    
    /**
     * JSON 문자열을 정리합니다.
     * - 코드 블록 마커 제거 (```json, ```)
     * - 주석 제거 (//로 시작하는 라인)
     * - 라인 끝 주석 제거
     */
    fun cleanJson(jsonString: String): String {
        return jsonString
            .replace("```json", "")
            .replace("```", "")
            .trim()
            .lines()
            .filter { !it.trim().startsWith("//") }
            .joinToString("\n")
            .replace(Regex(",\\s*//.*"), ",") // 라인 끝 주석 제거
    }
}
