package com.example.agent_app.domain.chat.model

data class ChatMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}
