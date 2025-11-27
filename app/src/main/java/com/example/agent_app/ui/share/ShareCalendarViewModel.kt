package com.example.agent_app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.agent_app.data.entity.Event
import com.example.agent_app.share.data.ShareCalendarRepository
import com.example.agent_app.share.model.CalendarDetailDto
import com.example.agent_app.share.model.CalendarSummaryDto
import com.example.agent_app.share.model.CreateCalendarEventRequest
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
    val myCalendars: List<CalendarSummaryDto> = emptyList(),  // 팀 캘린더 (멤버로 있는 캘린더)
    val myPersonalCalendar: CalendarSummaryDto? = null,  // 나의 고유 캘린더
    val lastCreatedCalendarName: String? = null,
    val searchProfileInput: String = "",  // 남의 공유 ID 검색
    val searchCalendarInput: String = "",  // 캘린더 공유 ID 검색
    val isSearchingProfile: Boolean = false,
    val isSearchingCalendar: Boolean = false,
    val searchProfileResult: ShareProfileResponse? = null,  // 남의 프로필 검색 결과
    val searchCalendarResult: CalendarDetailDto? = null,  // 캘린더 검색 결과
    val isLoadingMyCalendarPreview: Boolean = false,
    val myCalendarPreview: CalendarDetailDto? = null,
    val isSyncingInternalEvents: Boolean = false,
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

    fun updateSearchProfileInput(value: String) {
        _uiState.update {
            it.copy(
                searchProfileInput = value,
                searchProfileResult = null,
            )
        }
    }

    fun updateSearchCalendarInput(value: String) {
        _uiState.update {
            it.copy(
                searchCalendarInput = value,
                searchCalendarResult = null,
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
                    // 고유 캘린더 찾기 (description이 "나의 고유 캘린더"인 것)
                    val personalCalendar = profile.calendars.firstOrNull { 
                        it.description == "나의 고유 캘린더"
                    }
                    // 팀 캘린더는 고유 캘린더를 제외한 나머지
                    val teamCalendars = profile.calendars.filter { 
                        it.description?.equals("나의 고유 캘린더") != true
                    }
                    
                    _uiState.update {
                        it.copy(
                            isLoadingProfile = false,
                            myShareId = profile.shareId,
                            myCalendars = teamCalendars,
                            myPersonalCalendar = personalCalendar,
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
                    loadMyProfile(email)  // 프로필 다시 로드하여 캘린더 목록 업데이트
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
        val shareId = _uiState.value.searchProfileInput.trim()
        if (shareId.isEmpty()) {
            emitMessage("조회할 공유 ID를 입력해 주세요.")
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearchingProfile = true,
                    snackbarMessage = null,
                    searchProfileResult = null,
                )
            }
            val profileResult = repository.getProfileByShareId(shareId)
            profileResult.fold(
                onSuccess = { profile ->
                    _uiState.update {
                        it.copy(
                            isSearchingProfile = false,
                            searchProfileResult = profile,
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isSearchingProfile = false,
                            searchProfileResult = null,
                            snackbarMessage = throwable.message ?: "공유 ID를 찾지 못했습니다.",
                        )
                    }
                }
            )
        }
    }

    fun searchCalendarByShareId() {
        val shareId = _uiState.value.searchCalendarInput.trim()
        if (shareId.isEmpty()) {
            emitMessage("조회할 캘린더 공유 ID를 입력해 주세요.")
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearchingCalendar = true,
                    snackbarMessage = null,
                    searchCalendarResult = null,
                )
            }
            val calendarResult = repository.getCalendarByShareId(shareId)
            calendarResult.fold(
                onSuccess = { calendar ->
                    _uiState.update {
                        it.copy(
                            isSearchingCalendar = false,
                            searchCalendarResult = calendar,
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isSearchingCalendar = false,
                            searchCalendarResult = null,
                            snackbarMessage = throwable.message ?: "캘린더를 찾지 못했습니다.",
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
        _uiState.update {
            it.copy(
                myCalendarPreview = null,
                isLoadingMyCalendarPreview = false,
                isSyncingInternalEvents = false,
            )
        }
    }

    fun syncInternalEvents(
        actorEmail: String?,
        calendarId: String,
        events: List<Event>,
    ) {
        val email = actorEmail ?: run {
            emitMessage("Google 계정을 선택해 주세요.")
            return
        }
        val shareableEvents = events.filter { it.startAt != null && it.title.isNotBlank() }
        if (shareableEvents.isEmpty()) {
            emitMessage("공유할 일정이 없습니다.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSyncingInternalEvents = true,
                    snackbarMessage = null,
                )
            }

            var hasError = false

            shareableEvents.forEach { event ->
                val startAt = event.startAt ?: return@forEach
                val request = CreateCalendarEventRequest(
                    title = event.title,
                    description = event.body,
                    location = event.location,
                    allDay = false,
                    startAt = java.time.Instant.ofEpochMilli(startAt).toString(),
                    endAt = event.endAt?.let { java.time.Instant.ofEpochMilli(it).toString() },
                )

                val result = repository.createEvent(
                    actorEmail = email,
                    calendarId = calendarId,
                    request = request,
                )
                if (result.isFailure) {
                    hasError = true
                }
            }

            if (hasError) {
                _uiState.update {
                    it.copy(
                        isSyncingInternalEvents = false,
                        snackbarMessage = "일부 일정을 공유하지 못했습니다.",
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSyncingInternalEvents = false,
                        snackbarMessage = "내부 일정이 공유 캘린더에 추가되었습니다.",
                    )
                }
            }

            val detailResult = repository.getCalendarDetail(email, calendarId)
            detailResult.onSuccess { detail ->
                _uiState.update { it.copy(myCalendarPreview = detail) }
            }.onFailure { throwable ->
                emitMessage(throwable.message ?: "캘린더 정보를 새로고침하지 못했습니다.")
            }
        }
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

