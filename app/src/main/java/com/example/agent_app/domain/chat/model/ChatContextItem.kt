package com.example.agent_app.domain.chat.model

data class ChatContextItem(
    val itemId: String,
    val title: String,
    val body: String,
    val source: String,
    val timestamp: Long,
    val relevance: Double,
    val position: Int,
)
