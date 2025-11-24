package com.example.agent_app.domain.chat.model

import com.example.agent_app.data.entity.Event

// MOA-Chat-Source: 답변에 사용된 근거 데이터 모델
data class ContextSourceDto(
    val sourceType: String, // "sms", "gmail", "ocr", "push"
    val title: String,
    val snippet: String,
    val timestamp: Long,
    val id: String,
)

data class ChatMessage(
    val role: Role,
    val content: String,
    val attachment: ChatAttachment? = null,
    // MOA-Chat-Source: 답변 생성에 사용된 상위 1~2개의 context 출처
    val sources: List<ContextSourceDto>? = null,
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

/**
 * 챗봇 메시지에 첨부할 수 있는 다양한 타입의 첨부물
 */
sealed class ChatAttachment {
    /**
     * 일정 미리보기 카드
     */
    data class EventPreview(val event: Event) : ChatAttachment()
    
    // 향후 확장 가능: FileAttachment, ImageAttachment 등
}
