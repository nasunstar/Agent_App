package com.example.agent_app.gmail

import android.util.Base64

/**
 * Gmail 메시지에서 본문을 추출하는 유틸리티
 */
object GmailBodyExtractor {
    
    /**
     * Gmail 메시지에서 본문을 추출합니다.
     * text/plain을 우선하고, 없으면 text/html을 사용합니다.
     * 
     * @param message Gmail 메시지
     * @return 추출된 본문 텍스트, 없으면 snippet 반환
     */
    fun extractBody(message: GmailMessage): String {
        val payload = message.payload ?: return message.snippet ?: ""
        
        // 1. 직접 body가 있는 경우 (단순 메시지)
        if (payload.body?.data != null) {
            return decodeBase64(payload.body.data)
        }
        
        // 2. parts가 있는 경우 (멀티파트 메시지)
        if (payload.parts != null) {
            // text/plain 우선 탐색
            val plainText = findBodyByMimeType(payload.parts, "text/plain")
            if (plainText != null) return plainText
            
            // text/html 탐색
            val htmlText = findBodyByMimeType(payload.parts, "text/html")
            if (htmlText != null) return stripHtml(htmlText)
        }
        
        // 3. 본문을 찾지 못한 경우 snippet 반환
        return message.snippet ?: ""
    }
    
    /**
     * 재귀적으로 parts를 탐색하여 특정 MIME 타입의 본문을 찾습니다.
     */
    private fun findBodyByMimeType(parts: List<GmailMessagePart>, mimeType: String): String? {
        for (part in parts) {
            // 현재 part의 MIME 타입이 일치하는 경우
            if (part.mimeType == mimeType && part.body?.data != null) {
                return decodeBase64(part.body.data)
            }
            
            // 중첩된 parts가 있는 경우 재귀 탐색
            if (part.parts != null) {
                val result = findBodyByMimeType(part.parts, mimeType)
                if (result != null) return result
            }
        }
        return null
    }
    
    /**
     * Base64 URL-safe 인코딩된 문자열을 디코딩합니다.
     * Gmail API는 URL-safe Base64를 사용합니다 (- 대신 _, / 대신 +)
     */
    private fun decodeBase64(encoded: String): String {
        return try {
            val decoded = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP)
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("GmailBodyExtractor", "Base64 디코딩 실패", e)
            ""
        }
    }
    
    /**
     * HTML 태그를 제거합니다 (간단한 구현)
     */
    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "") // HTML 태그 제거
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }
}

