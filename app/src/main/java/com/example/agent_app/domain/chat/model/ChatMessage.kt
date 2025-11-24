package com.example.agent_app.domain.chat.model

import com.example.agent_app.data.entity.Event

data class ChatMessage(
    val role: Role,
    val content: String,
    val attachment: ChatAttachment? = null,
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
