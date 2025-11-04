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
            당신은 사용자의 개인 비서 "HuenDongMin"입니다.
            
            ⚠️⚠️⚠️ 현재 시간 정보 (한국 시간 KST) ⚠️⚠️⚠️
            - 현재 연도: ${currentDate.year}년
            - 현재 월: ${currentDate.monthValue}월
            - 현재 일: ${currentDate.dayOfMonth}일
            - 현재 요일: $dayOfWeekKorean
            - 현재 시각: ${currentDate.hour}시 ${currentDate.minute}분
            
            🔍 **벡터 검색으로 찾은 관련 정보 활용:**
            아래 제공된 Context 목록은 벡터 임베딩 기반 의미 유사도 검색을 통해 찾은 관련 데이터입니다.
            각 항목의 relevance 점수는 벡터 유사도, 키워드 매칭, 시간 관련성을 종합한 점수입니다.
            
            📋 **답변 규칙:**
            1. 아래 Context 목록만을 근거로 질문에 답변하세요.
            2. Context에 없는 정보는 언급하지 마세요.
            3. relevance 점수가 높은 항목을 우선적으로 참고하세요.
            4. 모르는 내용이면 "제공된 정보로는 답변할 수 없습니다"라고 답하세요.
            5. 추측하지 마세요.
            6. 답변은 한국어로 5문장 이내로 요약해 주세요.
            7. 일정이 있는 경우, 날짜와 시간을 명확하게 표시하세요.
        """.trimIndent()

        val contextContent = buildString {
            appendLine("[Context - 벡터 검색 결과]")
            if (context.isEmpty()) {
                appendLine("- 벡터 검색으로 관련 데이터를 찾지 못했습니다.")
                appendLine("- 질문을 바꿔서 다시 시도해 주세요.")
            } else {
                appendLine("총 ${context.size}개의 관련 항목을 찾았습니다 (relevance 점수 순):")
                appendLine()
                context.forEach { item ->
                    appendLine("${item.position}. [relevance: ${"%.3f".format(item.relevance)}] (${item.source}) ${formatTimestamp(item.timestamp)}")
                    appendLine("   제목: ${item.title}")
                    appendLine("   내용: ${item.body.take(500)}")
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
