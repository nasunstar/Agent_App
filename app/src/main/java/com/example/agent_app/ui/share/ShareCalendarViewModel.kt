package com.example.agent_app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.agent_app.share.data.ShareCalendarRepository
import com.example.agent_app.share.model.CalendarDetailDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareCalendarUiState(
    val name: String = "",
    val description: String = "",
    val isLoading: Boolean = false,
    val showNameValidationError: Boolean = false,
    val snackbarMessage: String? = null,
    val lastCreatedCalendar: CalendarDetailDto? = null,
)

class ShareCalendarViewModel(
    private val repository: ShareCalendarRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareCalendarUiState())
    val uiState: StateFlow<ShareCalendarUiState> = _uiState

    fun updateName(value: String) {
        _uiState.update {
            it.copy(
                name = value,
                showNameValidationError = if (value.isNotBlank()) false else it.showNameValidationError,
            )
        }
    }

    fun updateDescription(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun createCalendar() {
        val name = _uiState.value.name.trim()
        if (name.isEmpty()) {
            _uiState.update {
                it.copy(
                    showNameValidationError = true,
                    snackbarMessage = "캘린더 이름을 입력해 주세요.",
                )
            }
            return
        }

        val description = _uiState.value.description.trim().takeIf { it.isNotEmpty() }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    showNameValidationError = false,
                    snackbarMessage = null,
                )
            }

            val result = repository.createCalendar(name, description)
            result.fold(
                onSuccess = { calendar ->
                    _uiState.update {
                        it.copy(
                            name = "",
                            description = "",
                            isLoading = false,
                            lastCreatedCalendar = calendar,
                            snackbarMessage = "공유 캘린더가 생성되었습니다.",
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            snackbarMessage = throwable.message ?: "캘린더 생성에 실패했습니다.",
                        )
                    }
                }
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

class ShareCalendarViewModelFactory(
    private val repository: ShareCalendarRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShareCalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShareCalendarViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}

