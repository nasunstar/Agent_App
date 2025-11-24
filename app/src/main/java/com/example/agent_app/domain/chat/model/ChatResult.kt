package com.example.agent_app.domain.chat.model

data class ChatResult(
    val question: ChatMessage,
    val answer: ChatMessage,
    val contextItems: List<ChatContextItem>,
    val filters: QueryFilters,
    val attachment: ChatAttachment? = null, // 답변에 첨부된 데이터 (예: 생성된 일정)
)
