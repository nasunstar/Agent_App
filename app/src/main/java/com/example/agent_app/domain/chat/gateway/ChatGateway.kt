package com.example.agent_app.domain.chat.gateway

import com.example.agent_app.domain.chat.model.ChatContextItem
import com.example.agent_app.domain.chat.model.ChatMessage
import com.example.agent_app.domain.chat.model.QueryFilters

interface ChatGateway {
    suspend fun fetchContext(question: String, filters: QueryFilters, limit: Int = 5): List<ChatContextItem>
    suspend fun requestChatCompletion(messages: List<ChatMessage>): ChatMessage
}
