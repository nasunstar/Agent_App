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
    suspend operator fun invoke(
        questionText: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): ChatResult {
        val question = ChatMessage(ChatMessage.Role.USER, questionText)
        
        // 실시간 현재 시간 가져오기
        val currentTimestamp = System.currentTimeMillis()
        
        // AI가 내부적으로 필터를 생성하므로 빈 필터 전달
        val emptyFilters = QueryFilters()
        val context = chatGateway.fetchContext(questionText, emptyFilters)
        val messages = promptBuilder.buildMessages(
            question = question,
            context = context,
            currentTimestamp = currentTimestamp,
            conversationHistory = conversationHistory
        )
        // MOA-Chat-Source: context를 전달하여 중복 조회 방지
        val answer = chatGateway.requestChatCompletion(messages, context)
        
        return ChatResult(
            question = question,
            answer = answer,
            contextItems = context,
            filters = emptyFilters, // AI가 생성한 필터는 내부적으로만 사용
            attachment = answer.attachment, // 답변에 첨부된 데이터 (예: 생성된 일정)
        )
    }
}

interface PromptBuilder {
    fun buildMessages(
        question: ChatMessage,
        context: List<ChatContextItem>,
        currentTimestamp: Long,
        conversationHistory: List<ChatMessage> = emptyList()
    ): List<ChatMessage>
}
