package com.example.agent_app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.data.entity.Contact
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.entity.Note
import com.example.agent_app.data.repo.AuthRepository
import com.example.agent_app.data.repo.GmailRepositoryWithAi
import com.example.agent_app.data.repo.GmailSyncResult
import com.example.agent_app.data.repo.IngestRepository
import com.example.agent_app.data.repo.ClassifiedDataRepository
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
    private val gmailRepository: GmailRepositoryWithAi,
    private val classifiedDataRepository: ClassifiedDataRepository? = null,
) : ViewModel() {

    private val loginState = MutableStateFlow(LoginUiState())
    private val syncState = MutableStateFlow(SyncState())

    private val gmailItemsState = ingestRepository
        .observeBySource("gmail")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    
    // OCR 데이터 상태
    private val ocrItemsState = ingestRepository
        .observeBySource("ocr")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    
    // 분류된 데이터 상태
    private val contactsState = MutableStateFlow<List<Contact>>(emptyList())
    private val eventsState = MutableStateFlow<List<Event>>(emptyList())
    private val notesState = MutableStateFlow<List<Note>>(emptyList())
    private val ocrEventsState = MutableStateFlow<Map<String, List<Event>>>(emptyMap())

    val uiState: StateFlow<AssistantUiState> = combine(
        loginState,
        gmailItemsState,
        contactsState,
        eventsState,
        notesState,
        syncState,
        ocrItemsState,
        ocrEventsState,
    ) { flows ->
        val login = flows[0] as LoginUiState
        val gmailItems = flows[1] as List<IngestItem>
        val contacts = flows[2] as List<Contact>
        val events = flows[3] as List<Event>
        val notes = flows[4] as List<Note>
        val sync = flows[5] as SyncState
        val ocrItems = flows[6] as List<IngestItem>
        val ocrEvents = flows[7] as Map<String, List<Event>>
        
        AssistantUiState(
            loginState = login,
            gmailItems = gmailItems,
            contacts = contacts,
            events = events,
            notes = notes,
            isSyncing = sync.isSyncing,
            syncMessage = sync.message,
            ocrItems = ocrItems,
            ocrEvents = ocrEvents,
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
        
        // 분류된 데이터 로드
        viewModelScope.launch {
            classifiedDataRepository?.let { repo ->
                contactsState.value = repo.getAllContacts()
                eventsState.value = repo.getAllEvents()
                notesState.value = repo.getAllNotes()
            }
        }
        
        // OCR 이벤트 로드
        viewModelScope.launch {
            ocrItemsState.collect { items ->
                loadOcrEvents(items)
            }
        }
    }
    
    private suspend fun loadOcrEvents(ocrItems: List<IngestItem>) {
        classifiedDataRepository?.let { repo ->
            val eventsMap = mutableMapOf<String, List<Event>>()
            ocrItems.forEach { item ->
                val events = repo.getAllEvents().filter { event ->
                    event.sourceType == "ocr" && event.sourceId == item.id
                }
                eventsMap[item.id] = events
            }
            ocrEventsState.value = eventsMap
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
        
        // 디버깅을 위한 로그 추가
        android.util.Log.d("MainViewModel", "토큰 저장 시도 - Access Token: ${access.take(20)}...")
        android.util.Log.d("MainViewModel", "토큰 저장 시도 - Refresh Token: ${state.refreshTokenInput.take(20)}...")
        android.util.Log.d("MainViewModel", "토큰 저장 시도 - Scope: ${state.scopeInput}")
        android.util.Log.d("MainViewModel", "토큰 저장 시도 - Expires At: $expiresAt")
        
        viewModelScope.launch {
            try {
                // 토큰에서 개행 문자와 공백 제거
                val cleanAccessToken = access.replace("\n", "").replace("\r", "").trim()
                val cleanRefreshToken = state.refreshTokenInput.replace("\n", "").replace("\r", "").trim().takeIf { it.isNotEmpty() }
                
                android.util.Log.d("MainViewModel", "정리된 Access Token 길이: ${cleanAccessToken.length}")
                android.util.Log.d("MainViewModel", "정리된 Refresh Token 길이: ${cleanRefreshToken?.length ?: 0}")
                
                authRepository.upsertGoogleToken(
                    accessToken = cleanAccessToken,
                    refreshToken = cleanRefreshToken,
                    scope = state.scopeInput.trim().ifEmpty { DEFAULT_GMAIL_SCOPE },
                    expiresAt = expiresAt,
                )
                android.util.Log.d("MainViewModel", "토큰 저장 성공")
                loginState.update {
                    it.copy(
                        accessTokenInput = "",
                        refreshTokenInput = "",
                        expiresAtInput = expiresAt?.toString() ?: "",
                        scopeInput = it.scopeInput.ifEmpty { DEFAULT_GMAIL_SCOPE },
                        statusMessage = "토큰이 저장되었습니다.",
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "토큰 저장 실패", e)
                loginState.update {
                    it.copy(statusMessage = "토큰 저장 실패: ${e.message}")
                }
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
            
            // 디버깅을 위한 로그 추가
            android.util.Log.d("MainViewModel", "토큰 조회 결과: $token")
            android.util.Log.d("MainViewModel", "Access Token: ${token?.accessToken?.take(20)}...")
            android.util.Log.d("MainViewModel", "토큰 만료 시간: ${token?.expiresAt}")
            
            if (token?.accessToken.isNullOrBlank()) {
                syncState.value = SyncState(
                    isSyncing = false,
                    message = "저장된 토큰이 없어 Gmail을 동기화할 수 없습니다.",
                )
                return@launch
            }
            when (val result = gmailRepository.syncRecentMessages(token!!.accessToken)) {
                is GmailSyncResult.Success -> {
                    // Gmail 동기화 후 분류된 데이터 다시 로드
                    classifiedDataRepository?.let { repo ->
                        contactsState.value = repo.getAllContacts()
                        eventsState.value = repo.getAllEvents()
                        notesState.value = repo.getAllNotes()
                    }
                    
                    syncState.value = SyncState(
                        isSyncing = false,
                        message = "${result.upsertedCount}개의 메시지를 동기화했습니다. (${com.example.agent_app.util.TimeFormatter.formatSyncTime()})",
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

    fun resetDatabase() {
        viewModelScope.launch {
            try {
                android.util.Log.d("MainViewModel", "데이터베이스 초기화 시작")
                
                // 모든 테이블 초기화
                ingestRepository.clearAll()
                android.util.Log.d("MainViewModel", "IngestItem 테이블 초기화 완료")
                
                classifiedDataRepository?.let { repo ->
                    repo.clearAll()
                    android.util.Log.d("MainViewModel", "Event, Contact, Note 테이블 초기화 완료")
                }
                
                // 상태 초기화
                contactsState.value = emptyList()
                eventsState.value = emptyList()
                notesState.value = emptyList()
                
                android.util.Log.d("MainViewModel", "데이터베이스 초기화 완료!")
                
                syncState.value = SyncState(
                    isSyncing = false,
                    message = "데이터베이스가 초기화되었습니다."
                )
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "데이터베이스 초기화 실패", e)
                syncState.value = SyncState(
                    isSyncing = false,
                    message = "데이터베이스 초기화 중 오류가 발생했습니다: ${e.message}"
                )
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
    val contacts: List<Contact> = emptyList(),
    val events: List<Event> = emptyList(),
    val notes: List<Note> = emptyList(),
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val ocrItems: List<IngestItem> = emptyList(),
    val ocrEvents: Map<String, List<Event>> = emptyMap(),
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
