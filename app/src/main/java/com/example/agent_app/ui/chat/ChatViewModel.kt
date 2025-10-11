package com.example.agent_app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

class ChatViewModel(
    private val executeChatUseCase: ExecuteChatUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun submit(question: String) {
        if (question.isBlank()) return
        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            runCatching { executeChatUseCase(question) }
                .onSuccess { result ->
                    _uiState.update { state ->
                        state.copy(
                            entries = state.entries + result.toThreadEntry(),
                            isProcessing = false,
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(isProcessing = false, error = throwable.message ?: "알 수 없는 오류가 발생했습니다.")
                    }
                }
        }
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
    )

    private fun buildFiltersDescription(filters: QueryFilters): String = buildString {
        filters.startTimeMillis?.let { append("시작: $it ") }
        filters.endTimeMillis?.let { append("끝: $it ") }
        filters.source?.let { append("출처: $it ") }
        if (filters.keywords.isNotEmpty()) {
            append("키워드: ${filters.keywords.joinToString(", ")}")
        }
    }.ifBlank { "필터 없음" }
}
