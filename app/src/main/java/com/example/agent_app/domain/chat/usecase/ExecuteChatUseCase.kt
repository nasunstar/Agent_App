package com.example.agent_app.domain.chat.usecase

import com.example.agent_app.domain.chat.gateway.ChatGateway
import com.example.agent_app.domain.chat.model.ChatContextItem
import com.example.agent_app.domain.chat.model.ChatMessage
import com.example.agent_app.domain.chat.model.ChatResult
import com.example.agent_app.domain.chat.model.QueryFilters

class ExecuteChatUseCase(
    private val chatGateway: ChatGateway,
    private val promptBuilder: PromptBuilder,
) {
    suspend operator fun invoke(questionText: String): ChatResult {
        val question = ChatMessage(ChatMessage.Role.USER, questionText)
        
        // 실시간 현재 시간 가져오기
        val currentTimestamp = System.currentTimeMillis()
        
        // AI가 내부적으로 필터를 생성하므로 빈 필터 전달
        val emptyFilters = QueryFilters()
        val context = chatGateway.fetchContext(questionText, emptyFilters)
        val messages = promptBuilder.buildMessages(question, context, currentTimestamp)
        val answer = chatGateway.requestChatCompletion(messages)
        
        return ChatResult(
            question = question,
            answer = answer,
            contextItems = context,
            filters = emptyFilters, // AI가 생성한 필터는 내부적으로만 사용
        )
    }
}

interface PromptBuilder {
    fun buildMessages(question: ChatMessage, context: List<ChatContextItem>, currentTimestamp: Long): List<ChatMessage>
}
