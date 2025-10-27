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
        currentTimestamp: Long
    ): List<ChatMessage> {
        // 실시간으로 현재 날짜 계산
        val currentDate = Instant.ofEpochMilli(currentTimestamp)
            .atZone(ZoneId.of("Asia/Seoul"))
        
        // 요일 이름 가져오기 (한글)
        val dayOfWeekKorean = when (currentDate.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "월요일"
            java.time.DayOfWeek.TUESDAY -> "화요일"
            java.time.DayOfWeek.WEDNESDAY -> "수요일"
            java.time.DayOfWeek.THURSDAY -> "목요일"
            java.time.DayOfWeek.FRIDAY -> "금요일"
            java.time.DayOfWeek.SATURDAY -> "토요일"
            java.time.DayOfWeek.SUNDAY -> "일요일"
        }
        
        val systemContent = """
            당신은 사용자의 개인 비서입니다.
            
            ⚠️⚠️⚠️ 현재 시간 정보 (한국 시간 KST) ⚠️⚠️⚠️
            - 현재 연도: ${currentDate.year}년
            - 현재 월: ${currentDate.monthValue}월
            - 현재 일: ${currentDate.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            - 현재 시각: ${currentDate.hour}시 ${currentDate.minute}분
            
            아래 제공된 Context 목록만을 근거로 질문에 답변하세요.
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
