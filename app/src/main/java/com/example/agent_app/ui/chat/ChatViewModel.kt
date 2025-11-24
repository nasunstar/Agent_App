package com.example.agent_app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent_app.domain.chat.model.ChatMessage
import com.example.agent_app.domain.chat.model.ChatResult
import com.example.agent_app.domain.chat.model.QueryFilters
import com.example.agent_app.domain.chat.usecase.ExecuteChatUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatThreadEntry(
    val question: String,
    val answer: String,
    val context: List<ContextItemUi>,
    val filtersDescription: String,
    val timestamp: Long = System.currentTimeMillis(), // 메시지 생성 시간 (UI 레이어에서만 사용)
    val attachment: com.example.agent_app.domain.chat.model.ChatAttachment? = null, // 첨부 데이터 (예: 생성된 일정)
)

data class ContextItemUi(
    val id: String,
    val title: String,
    val preview: String,
    val meta: String,
)

data class ChatUiState(
    val entries: List<ChatThreadEntry> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val failedEntryIndex: Int? = null, // 실패한 메시지 인덱스 (UI 레이어에서만 사용)
)

class ChatViewModel(
    private val executeChatUseCase: ExecuteChatUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun submit(question: String) {
        if (question.isBlank()) return
        _uiState.update { it.copy(isProcessing = true, error = null, failedEntryIndex = null) }
        viewModelScope.launch {
            // 이전 대화를 ChatMessage 리스트로 변환
            val conversationHistory = _uiState.value.entries.flatMap { entry ->
                listOf(
                    ChatMessage(ChatMessage.Role.USER, entry.question),
                    ChatMessage(ChatMessage.Role.ASSISTANT, entry.answer)
                )
            }
            
            val currentIndex = _uiState.value.entries.size
            
            runCatching { executeChatUseCase(question, conversationHistory) }
                .onSuccess { result ->
                    _uiState.update { state ->
                        state.copy(
                            entries = state.entries + result.toThreadEntry(),
                            isProcessing = false,
                            error = null,
                            failedEntryIndex = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    android.util.Log.e("ChatViewModel", "챗 실행 실패", throwable)
                    val errorMessage = throwable.message ?: "제가 처리하지 못했어요. 다시 시도해주세요."
                    _uiState.update { state ->
                        state.copy(
                            isProcessing = false,
                            error = errorMessage,
                            failedEntryIndex = currentIndex // 실패한 메시지 인덱스 저장
                        )
                    }
                }
        }
    }
    
    /**
     * 실패한 메시지 재시도
     * @param entryIndex 실패한 메시지 인덱스
     */
    fun retryFailedMessage(entryIndex: Int) {
        val entries = _uiState.value.entries
        if (entryIndex < 0 || entryIndex >= entries.size) return
        
        val failedEntry = entries[entryIndex]
        submit(failedEntry.question)
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun ChatResult.toThreadEntry(): ChatThreadEntry = ChatThreadEntry(
        question = question.content,
        answer = answer.content,
        context = contextItems.map { item ->
            ContextItemUi(
                id = item.itemId,
                title = item.title.ifBlank { "(제목 없음)" },
                preview = item.body.take(400),
                meta = "${item.source} • ${item.position}위 • score=${"%.2f".format(item.relevance)}",
            )
        },
        filtersDescription = buildFiltersDescription(filters),
        timestamp = System.currentTimeMillis(), // 메시지 생성 시간 저장
        attachment = attachment ?: answer.attachment, // ChatResult의 attachment 우선, 없으면 ChatMessage의 attachment
    )

    private fun buildFiltersDescription(filters: QueryFilters): String = buildString {
        val parts = mutableListOf<String>()
        
        // 날짜/시간 형식으로 변환
        filters.startTimeMillis?.let { start ->
            filters.endTimeMillis?.let { end ->
                val startDate = java.time.Instant.ofEpochMilli(start)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("MM월 dd일 HH:mm"))
                val endDate = java.time.Instant.ofEpochMilli(end)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("MM월 dd일 HH:mm"))
                parts.add("기간: $startDate ~ $endDate")
            } ?: run {
                val startDate = java.time.Instant.ofEpochMilli(start)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("MM월 dd일 HH:mm"))
                parts.add("시작: $startDate")
            }
        }
        
        filters.source?.let { parts.add("출처: $it") }
        if (filters.keywords.isNotEmpty()) {
            parts.add("키워드: ${filters.keywords.joinToString(", ")}")
        }
        
        append(parts.joinToString(" • "))
    }.ifBlank { "필터 없음" }
}
