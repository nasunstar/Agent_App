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
        currentTimestamp: Long,
        conversationHistory: List<ChatMessage>
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
            
            🔍 **하이브리드 검색으로 찾은 관련 정보 활용:**
            아래 제공된 Context 목록은 키워드 검색(FTS5), 벡터 임베딩 기반 의미 유사도 검색, 그리고 시간 관련성을 종합한 하이브리드 검색을 통해 찾은 관련 데이터입니다.
            각 항목의 relevance 점수는 키워드 매칭(30%), 벡터 유사도(30%), 시간 관련성(40%)을 종합한 점수입니다.
            
            💬 **대화 컨텍스트 활용:**
            ${if (conversationHistory.isNotEmpty()) {
                "이전 대화 내용을 참고하여 사용자의 의도를 더 정확히 파악하고, 이전에 언급된 내용과 연관지어 답변하세요."
            } else {
                "이것은 새로운 대화의 시작입니다."
            }}
            
            📋 **답변 규칙:**
            1. 아래 Context 목록만을 근거로 질문에 답변하세요.
            2. Context에 없는 정보는 언급하지 마세요.
            3. relevance 점수가 높은 항목을 우선적으로 참고하세요.
            4. 이전 대화에서 언급된 내용이 있다면, 그것을 참고하여 더 정확한 답변을 제공하세요.
            5. ⚠️ 중요: Context 목록이 비어있거나 "하이브리드 검색으로 관련 데이터를 찾지 못했습니다"라고 나와있으면, 
               "제공된 정보로는 답변할 수 없습니다"라고 답하세요.
            6. ⚠️ 중요: Context 목록에 일정 정보가 있으면, 반드시 그 정보를 바탕으로 답변하세요!
               예: "다음주 일정은 뭐야?"라는 질문에 Context에 다음주 일정이 있으면, 그 일정들을 나열하세요.
            7. 추측하지 마세요.
            8. 답변은 한국어로 자연스럽고 친근한 톤으로 작성하되, 5문장 이내로 요약해 주세요.
            9. 일정이 있는 경우, 날짜와 시간을 명확하게 표시하세요.
            10. 사용자가 "그거", "그것", "저번에" 등 지시어를 사용하면 이전 대화를 참고하여 해석하세요.
        """.trimIndent()

        val contextContent = buildString {
            appendLine("[Context - 하이브리드 검색 결과]")
            if (context.isEmpty()) {
                appendLine("- 하이브리드 검색(키워드 + 벡터 + 시간)으로 관련 데이터를 찾지 못했습니다.")
                appendLine("- 질문을 바꿔서 다시 시도해 주세요.")
            } else {
                appendLine("총 ${context.size}개의 관련 항목을 찾았습니다 (relevance 점수 순, 하이브리드 검색 결과):")
                appendLine()
                context.forEach { item ->
                    val relevancePercent = (item.relevance * 100).toInt()
                    appendLine("${item.position}. [관련도: ${relevancePercent}%] (출처: ${item.source}) ${formatTimestamp(item.timestamp)}")
                    appendLine("   제목: ${item.title}")
                    val bodyPreview = if (item.body.length > 500) {
                        "${item.body.take(500)}..."
                    } else {
                        item.body
                    }
                    appendLine("   내용: $bodyPreview")
                    appendLine()
                }
            }
        }.trim()

        val userContent = buildString {
            // 이전 대화 내용 추가 (최근 5개만)
            if (conversationHistory.isNotEmpty()) {
                appendLine("[이전 대화 내용]")
                conversationHistory.takeLast(5).forEach { msg ->
                    val role = when (msg.role) {
                        ChatMessage.Role.USER -> "사용자"
                        ChatMessage.Role.ASSISTANT -> "HuenDongMin"
                        ChatMessage.Role.SYSTEM -> "시스템"
                    }
                    appendLine("$role: ${msg.content}")
                }
                appendLine()
            }
            
            appendLine(contextContent)
            appendLine()
            appendLine("[현재 질문]")
            appendLine(question.content)
        }

        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage(ChatMessage.Role.SYSTEM, systemContent))
        
        // 이전 대화 히스토리 추가 (최근 10개)
        conversationHistory.takeLast(10).forEach { msg ->
            messages.add(msg)
        }
        
        messages.add(ChatMessage(ChatMessage.Role.USER, userContent))
        
        return messages
    }

    private fun formatTimestamp(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.of("Asia/Seoul"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
}
