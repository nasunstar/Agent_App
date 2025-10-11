package com.example.agent_app.domain.chat.model

data class ChatResult(
    val question: ChatMessage,
    val answer: ChatMessage,
    val contextItems: List<ChatContextItem>,
    val filters: QueryFilters,
)
