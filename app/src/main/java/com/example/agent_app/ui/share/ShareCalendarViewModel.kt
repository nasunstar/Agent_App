package com.example.agent_app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.agent_app.share.data.ShareCalendarRepository
import com.example.agent_app.share.model.CalendarDetailDto
import com.example.agent_app.share.model.CalendarSummaryDto
import com.example.agent_app.share.model.ShareProfileResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareCalendarUiState(
    val name: String = "",
    val description: String = "",
    val showNameValidationError: Boolean = false,
    val isCreating: Boolean = false,
    val isLoadingProfile: Boolean = false,
    val myShareId: String? = null,
    val myCalendars: List<CalendarSummaryDto> = emptyList(),
    val lastCreatedCalendarName: String? = null,
    val searchInput: String = "",
    val isSearching: Boolean = false,
    val searchResult: ShareProfileResponse? = null,
    val isLoadingMyCalendarPreview: Boolean = false,
    val myCalendarPreview: CalendarDetailDto? = null,
    val snackbarMessage: String? = null,
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

    fun updateSearchInput(value: String) {
        _uiState.update {
            it.copy(
                searchInput = value,
                searchResult = null,
            )
        }
    }

    fun loadMyProfile(actorEmail: String?) {
        val email = actorEmail ?: run {
            emitMessage("Google 계정을 선택해 주세요.")
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingProfile = true,
                    snackbarMessage = null,
                )
            }
            val result = repository.getOrCreateProfile(email)
            result.fold(
                onSuccess = { profile ->
                    _uiState.update {
                        it.copy(
                            isLoadingProfile = false,
                            myShareId = profile.shareId,
                            myCalendars = profile.calendars,
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingProfile = false,
                            snackbarMessage = throwable.message ?: "공유 ID를 불러오지 못했습니다.",
                        )
                    }
                }
            )
        }
    }

    fun createCalendar(actorEmail: String?) {
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
        val email = actorEmail ?: run {
            emitMessage("공유 캘린더를 생성하려면 Google 계정을 선택해 주세요.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCreating = true,
                    snackbarMessage = null,
                )
            }
            val result = repository.createCalendar(
                actorEmail = email,
                name = name,
                description = description,
            )
            result.fold(
                onSuccess = { calendar ->
                    _uiState.update {
                        it.copy(
                            name = "",
                            description = "",
                            isCreating = false,
                            lastCreatedCalendarName = calendar.name,
                            snackbarMessage = "공유 캘린더가 생성되었습니다.",
                        )
                    }
                    loadMyProfile(email)
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            snackbarMessage = throwable.message ?: "캘린더 생성에 실패했습니다.",
                        )
                    }
                }
            )
        }
    }

    fun searchProfileByShareId() {
        val shareId = _uiState.value.searchInput.trim()
        if (shareId.isEmpty()) {
            emitMessage("조회할 공유 ID를 입력해 주세요.")
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    snackbarMessage = null,
                )
            }
            val result = repository.getProfileByShareId(shareId)
            result.fold(
                onSuccess = { profile ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchResult = profile,
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchResult = null,
                            snackbarMessage = throwable.message ?: "공유 ID를 찾지 못했습니다.",
                        )
                    }
                }
            )
        }
    }

    fun loadMyCalendarDetail(actorEmail: String?, calendarId: String) {
        val email = actorEmail ?: run {
            emitMessage("Google 계정을 선택해 주세요.")
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingMyCalendarPreview = true,
                    snackbarMessage = null,
                )
            }
            val result = repository.getCalendarDetail(email, calendarId)
            result.fold(
                onSuccess = { detail ->
                    _uiState.update {
                        it.copy(
                            isLoadingMyCalendarPreview = false,
                            myCalendarPreview = detail,
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingMyCalendarPreview = false,
                            myCalendarPreview = null,
                            snackbarMessage = throwable.message ?: "캘린더 정보를 불러오지 못했습니다.",
                        )
                    }
                }
            )
        }
    }

    fun clearMyCalendarPreview() {
        _uiState.update { it.copy(myCalendarPreview = null) }
    }

    fun applyInternalDataPlaceholder() {
        emitMessage("내부 일정 공유 기능은 곧 지원 예정입니다.")
    }

    fun consumeMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun emitMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
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

