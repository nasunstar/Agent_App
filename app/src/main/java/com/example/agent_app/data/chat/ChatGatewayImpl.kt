package com.example.agent_app.data.chat

import com.example.agent_app.data.search.HybridSearchEngine
import com.example.agent_app.domain.chat.gateway.ChatGateway
import com.example.agent_app.domain.chat.model.ChatContextItem
import com.example.agent_app.domain.chat.model.ChatMessage
import com.example.agent_app.domain.chat.model.QueryFilters
import com.example.agent_app.openai.OpenAiClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatGatewayImpl(
    private val hybridSearchEngine: HybridSearchEngine,
    private val openAiClient: OpenAiClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ChatGateway {
    override suspend fun fetchContext(
        question: String,
        filters: QueryFilters,
        limit: Int,
    ): List<ChatContextItem> = hybridSearchEngine.search(question, filters, limit)

    override suspend fun requestChatCompletion(messages: List<ChatMessage>): ChatMessage = withContext(dispatcher) {
        openAiClient.createChatCompletion(messages)
    }
}
