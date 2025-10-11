package com.example.agent_app.openai

import com.example.agent_app.domain.chat.model.ChatContextItem
import com.example.agent_app.domain.chat.model.ChatMessage
import com.example.agent_app.domain.chat.usecase.PromptBuilder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PromptBuilderImpl : PromptBuilder {
    override fun buildMessages(
        question: ChatMessage,
        context: List<ChatContextItem>,
    ): List<ChatMessage> {
        val systemContent = """
            당신은 사용자의 개인 비서입니다. 아래 제공된 Context 목록만을 근거로 질문에 답변하세요.
            모르면 모른다고 답하고, 추측하지 마세요. 답변은 한국어로 5문장 이내로 요약해 주세요.
        """.trimIndent()

        val contextContent = buildString {
            appendLine("[Context]")
            if (context.isEmpty()) {
                appendLine("- 관련 데이터를 찾지 못했습니다.")
            } else {
                context.forEach { item ->
                    appendLine("${item.position}. (${item.source}) ${formatTimestamp(item.timestamp)} - ${item.title}")
                    appendLine(item.body.take(500))
                    appendLine("-- relevance: ${"%.2f".format(item.relevance)}")
                    appendLine()
                }
            }
        }.trim()

        val userContent = buildString {
            appendLine(contextContent)
            appendLine()
            appendLine("[질문]")
            appendLine(question.content)
        }

        return listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, systemContent),
            ChatMessage(ChatMessage.Role.USER, userContent)
        )
    }

    private fun formatTimestamp(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.of("Asia/Seoul"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
}
