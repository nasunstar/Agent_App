package com.example.agent_app.domain.chat.usecase

import com.example.agent_app.domain.chat.gateway.ChatGateway
import com.example.agent_app.domain.chat.model.ChatContextItem
import com.example.agent_app.domain.chat.model.ChatMessage
import com.example.agent_app.domain.chat.model.ChatResult
import com.example.agent_app.domain.chat.model.QueryFilters

class ExecuteChatUseCase(
    private val processUserQueryUseCase: ProcessUserQueryUseCase,
    private val chatGateway: ChatGateway,
    private val promptBuilder: PromptBuilder,
) {
    suspend operator fun invoke(questionText: String): ChatResult {
        val question = ChatMessage(ChatMessage.Role.USER, questionText)
        val filters = processUserQueryUseCase(questionText)
        val context = chatGateway.fetchContext(questionText, filters)
        val messages = promptBuilder.buildMessages(question, context)
        val answer = chatGateway.requestChatCompletion(messages)
        return ChatResult(
            question = question,
            answer = answer,
            contextItems = context,
            filters = filters,
        )
    }
}

interface PromptBuilder {
    fun buildMessages(question: ChatMessage, context: List<ChatContextItem>): List<ChatMessage>
}
