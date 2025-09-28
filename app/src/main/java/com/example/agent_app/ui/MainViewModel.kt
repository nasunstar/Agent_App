package com.example.agent_app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.data.repo.AuthRepository
import com.example.agent_app.data.repo.GmailRepository
import com.example.agent_app.data.repo.GmailSyncResult
import com.example.agent_app.data.repo.IngestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEFAULT_GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

class MainViewModel(
    private val authRepository: AuthRepository,
    private val ingestRepository: IngestRepository,
    private val gmailRepository: GmailRepository,
) : ViewModel() {

    private val loginState = MutableStateFlow(LoginUiState())
    private val syncState = MutableStateFlow(SyncState())

    private val gmailItemsState = ingestRepository
        .observeBySource("gmail")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<AssistantUiState> = combine(
        loginState,
        gmailItemsState,
        syncState,
    ) { login, gmailItems, sync ->
        AssistantUiState(
            loginState = login,
            gmailItems = gmailItems,
            isSyncing = sync.isSyncing,
            syncMessage = sync.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AssistantUiState(),
    )

    init {
        viewModelScope.launch {
            authRepository.observeGoogleToken().collect { token ->
                loginState.update { state ->
                    state.copy(
                        hasStoredToken = token != null,
                        storedScope = token?.scope,
                        storedExpiresAt = token?.expiresAt,
                    )
                }
            }
        }
    }

    fun updateAccessToken(value: String) {
        loginState.update { it.copy(accessTokenInput = value) }
    }

    fun updateRefreshToken(value: String) {
        loginState.update { it.copy(refreshTokenInput = value) }
    }

    fun updateScope(value: String) {
        loginState.update { it.copy(scopeInput = value) }
    }

    fun updateExpiresAt(value: String) {
        loginState.update { it.copy(expiresAtInput = value) }
    }

    fun saveToken() {
        val state = loginState.value
        val access = state.accessTokenInput.trim()
        if (access.isEmpty()) {
            loginState.update { it.copy(statusMessage = "액세스 토큰을 입력해 주세요.") }
            return
        }
        val expiresAt = state.expiresAtInput.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
        viewModelScope.launch {
            authRepository.upsertGoogleToken(
                accessToken = access,
                refreshToken = state.refreshTokenInput.trim().takeIf { it.isNotEmpty() },
                scope = state.scopeInput.trim().ifEmpty { DEFAULT_GMAIL_SCOPE },
                expiresAt = expiresAt,
            )
            loginState.update {
                it.copy(
                    accessTokenInput = "",
                    refreshTokenInput = "",
                    expiresAtInput = expiresAt?.toString() ?: "",
                    scopeInput = it.scopeInput.ifEmpty { DEFAULT_GMAIL_SCOPE },
                    statusMessage = "토큰이 저장되었습니다.",
                )
            }
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            authRepository.clearGoogleToken()
            loginState.update {
                it.copy(
                    statusMessage = "저장된 토큰 정보를 삭제했습니다.",
                )
            }
        }
    }

    fun syncGmail() {
        viewModelScope.launch {
            syncState.value = SyncState(isSyncing = true, message = null)
            val token = authRepository.getGoogleToken()
            if (token?.accessToken.isNullOrBlank()) {
                syncState.value = SyncState(
                    isSyncing = false,
                    message = "저장된 토큰이 없어 Gmail을 동기화할 수 없습니다.",
                )
                return@launch
            }
            when (val result = gmailRepository.syncRecentMessages(token!!.accessToken)) {
                is GmailSyncResult.Success -> {
                    syncState.value = SyncState(
                        isSyncing = false,
                        message = "${result.upsertedCount}개의 메시지를 동기화했습니다.",
                    )
                }
                is GmailSyncResult.Unauthorized -> {
                    syncState.value = SyncState(
                        isSyncing = false,
                        message = "토큰이 만료되었거나 권한이 없습니다. 다시 로그인해 주세요.",
                    )
                }
                is GmailSyncResult.NetworkError -> {
                    syncState.value = SyncState(
                        isSyncing = false,
                        message = "네트워크 오류: ${result.message}",
                    )
                }
                GmailSyncResult.MissingToken -> {
                    syncState.value = SyncState(
                        isSyncing = false,
                        message = "저장된 토큰이 없어 Gmail을 동기화할 수 없습니다.",
                    )
                }
            }
        }
    }

    fun consumeStatusMessage() {
        loginState.update { it.copy(statusMessage = null) }
        syncState.update { it.copy(message = null) }
    }
}

data class AssistantUiState(
    val loginState: LoginUiState = LoginUiState(),
    val gmailItems: List<IngestItem> = emptyList(),
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
)

data class LoginUiState(
    val accessTokenInput: String = "",
    val refreshTokenInput: String = "",
    val scopeInput: String = DEFAULT_GMAIL_SCOPE,
    val expiresAtInput: String = "",
    val hasStoredToken: Boolean = false,
    val storedScope: String? = null,
    val storedExpiresAt: Long? = null,
    val statusMessage: String? = null,
)

data class SyncState(
    val isSyncing: Boolean = false,
    val message: String? = null,
)
