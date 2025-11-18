package com.example.agent_app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.data.entity.Contact
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.entity.Note
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.agent_app.ai.HuenDongMinAiAgent
import com.example.agent_app.data.repo.AuthRepository
import com.example.agent_app.data.repo.GmailRepositoryWithAi
import com.example.agent_app.data.repo.GmailSyncResult
import com.example.agent_app.data.repo.IngestRepository
import com.example.agent_app.data.repo.ClassifiedDataRepository
import com.example.agent_app.data.repo.OcrRepositoryWithAi
import com.example.agent_app.data.dao.EventDao
import com.example.agent_app.util.SmsReader
import com.example.agent_app.service.CallRecordTextProcessor
import android.content.Intent
import com.example.agent_app.auth.GoogleSignInHelper
import com.example.agent_app.auth.GoogleAuthTokenProvider
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

class MainViewModel(
    private val authRepository: AuthRepository,
    private val ingestRepository: IngestRepository,
    private val gmailRepository: GmailRepositoryWithAi,
    private val classifiedDataRepository: ClassifiedDataRepository? = null,
    private val aiAgent: HuenDongMinAiAgent,
    private val context: Context,
    private val eventDao: EventDao? = null,
    private val ocrRepository: OcrRepositoryWithAi? = null,
    private val executeChatUseCase: com.example.agent_app.domain.chat.usecase.ExecuteChatUseCase? = null,
) : ViewModel() {

    private val loginState = MutableStateFlow(LoginUiState())
    private val syncState = MutableStateFlow(SyncState())
    private val smsScanState = MutableStateFlow(SmsScanState())
    private val gmailSyncState = MutableStateFlow(GmailSyncState())
    private val callRecordScanState = MutableStateFlow(CallRecordScanState())
    
    // 여러 Google 계정 관리
    private val googleAccountsState = MutableStateFlow<List<com.example.agent_app.data.entity.AuthToken>>(emptyList())
    private val selectedAccountEmail = MutableStateFlow<String?>(null)
    
    // Google Sign-In
    private val googleSignInHelper = GoogleSignInHelper(context)
    private val googleAuthTokenProvider = GoogleAuthTokenProvider(context)
    
    // Google Sign-In Intent는 동적으로 생성 (계정 선택 화면 표시를 위해)
    // 참고: Google Sign-In SDK 방식(Refresh Token 없음)에서는 서버 클라이언트 ID가 필요 없습니다.
    suspend fun getGoogleSignInIntent(): Intent {
        android.util.Log.d("MainViewModel", "Google Sign-In Intent 생성 (기본 방식 - Refresh Token 없음)")
        return googleSignInHelper.getSignInIntentWithAccountSelection()
    }
    
    /**
     * OAuth 2.0 플로우를 사용한 Google 로그인 (Refresh Token 포함)
     * 
     * 이 메서드는 Custom Tab을 열어서 사용자가 인증하고,
     * redirect URI로 돌아와서 authorization code를 받아 토큰으로 교환합니다.
     */
    fun startGoogleOAuth2Flow() {
        viewModelScope.launch {
            try {
                loginState.update { it.copy(isGoogleLoginInProgress = true) }
                
                val clientId = com.example.agent_app.BuildConfig.GOOGLE_WEB_CLIENT_ID
                
                // Client ID 검증
                if (clientId.isBlank() || clientId == "YOUR_GOOGLE_WEB_CLIENT_ID") {
                    android.util.Log.e("MainViewModel", "GOOGLE_WEB_CLIENT_ID가 설정되지 않았습니다. local.properties 파일을 확인하세요.")
                    loginState.update {
                        it.copy(
                            statusMessage = "오류: GOOGLE_WEB_CLIENT_ID가 설정되지 않았습니다.\nlocal.properties 파일에 GOOGLE_WEB_CLIENT_ID를 추가하세요.",
                            isGoogleLoginInProgress = false,
                        )
                    }
                    return@launch
                }
                
                android.util.Log.d("MainViewModel", "OAuth 2.0 플로우 시작 - Client ID: ${clientId.take(20)}...")
                
                val state = java.util.UUID.randomUUID().toString()
                
                // OAuth 2.0 인증 URL 생성
                val authUrl = googleSignInHelper.getOAuth2AuthorizationUrl(
                    clientId = clientId,
                    scope = DEFAULT_GMAIL_SCOPE,
                    state = state
                )
                
                android.util.Log.d("MainViewModel", "OAuth 2.0 인증 URL 생성: $authUrl")
                
                // Custom Tab으로 인증 URL 열기
                val oauthFlow = com.example.agent_app.auth.GoogleOAuth2Flow(context)
                oauthFlow.openAuthorizationUrl(context, authUrl)
                
                android.util.Log.d("MainViewModel", "OAuth 2.0 인증 URL 열기 완료")
                
                // State는 나중에 검증을 위해 저장 (현재는 간단히 처리)
                // 실제로는 SharedPreferences 등에 저장하고 redirect URI에서 검증해야 함
                
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "OAuth 2.0 플로우 시작 실패", e)
                loginState.update {
                    it.copy(
                        statusMessage = "OAuth 2.0 플로우 시작 실패: ${e.message}\n\n가능한 원인:\n1. local.properties에 GOOGLE_WEB_CLIENT_ID가 설정되지 않음\n2. Google Cloud Console에서 redirect URI가 등록되지 않음\n3. 네트워크 연결 문제",
                        isGoogleLoginInProgress = false,
                    )
                }
            }
        }
    }
    
    // SMS 스캔 작업 추적
    private var smsScanJob: Job? = null

    private val gmailItemsState = ingestRepository
        .observeBySource("gmail")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    
    // OCR 데이터 상태
    private val ocrItemsState = ingestRepository
        .observeBySource("ocr")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    
    // SMS 데이터 상태
    private val smsItemsState = ingestRepository
        .observeBySource("sms")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    
    // 푸시 알림 데이터 상태
    private val pushNotificationItemsState = ingestRepository
        .observeBySource("push_notification")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    
    // 분류된 데이터 상태
    private val contactsState = MutableStateFlow<List<Contact>>(emptyList())
    private val eventsState = MutableStateFlow<List<Event>>(emptyList())
    private val notesState = MutableStateFlow<List<Note>>(emptyList())
    private val ocrEventsState = MutableStateFlow<Map<String, List<Event>>>(emptyMap())
    private val smsEventsState = MutableStateFlow<Map<String, List<Event>>>(emptyMap())
    private val pushNotificationEventsState = MutableStateFlow<Map<String, List<Event>>>(emptyMap())

    val uiState: StateFlow<AssistantUiState> = combine(
        loginState,
        gmailItemsState,
        contactsState,
        eventsState,
        notesState,
        syncState,
        ocrItemsState,
        ocrEventsState,
        smsItemsState,
        smsEventsState,
        pushNotificationItemsState,
        pushNotificationEventsState,
        smsScanState,
        gmailSyncState,
        callRecordScanState,
    ) { flows ->
        val login = flows[0] as LoginUiState
        val gmailItems = flows[1] as List<IngestItem>
        val contacts = flows[2] as List<Contact>
        val events = flows[3] as List<Event>
        val notes = flows[4] as List<Note>
        val sync = flows[5] as SyncState
        val ocrItems = flows[6] as List<IngestItem>
        val ocrEvents = flows[7] as Map<String, List<Event>>
        val smsItems = flows[8] as List<IngestItem>
        val smsEvents = flows[9] as Map<String, List<Event>>
        val pushNotificationItems = flows[10] as List<IngestItem>
        val pushNotificationEvents = flows[11] as Map<String, List<Event>>
        val smsScan = flows[12] as SmsScanState
        val gmailSync = flows[13] as GmailSyncState
        val callRecordScan = flows[14] as CallRecordScanState
        
        AssistantUiState(
            loginState = login,
            gmailItems = gmailItems,
            syncState = sync,
            contacts = contacts,
            events = events,
            notes = notes,
            isSyncing = sync.isSyncing,
            syncMessage = sync.message,
            ocrItems = ocrItems,
            ocrEvents = ocrEvents,
            smsItems = smsItems,
            smsEvents = smsEvents,
            pushNotificationItems = pushNotificationItems,
            pushNotificationEvents = pushNotificationEvents,
            smsScanState = smsScan,
            gmailSyncState = gmailSync,
            callRecordScanState = callRecordScan,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AssistantUiState(),
    )

    init {
        // 단일 토큰 관찰 (기존 호환성)
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
        
        // 모든 Google 계정 관찰
        viewModelScope.launch {
            authRepository.observeAllGoogleTokens().collect { accounts ->
                googleAccountsState.value = accounts
                // 선택된 계정이 없거나 삭제된 경우 첫 번째 계정 선택
                val currentSelected = selectedAccountEmail.value
                if (currentSelected == null || accounts.none { it.email == currentSelected }) {
                    selectedAccountEmail.value = accounts.firstOrNull()?.email
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
        
        // SMS 이벤트 로드
        viewModelScope.launch {
            smsItemsState.collect { items ->
                loadSmsEvents(items)
            }
        }
        
        // 푸시 알림 이벤트 로드
        viewModelScope.launch {
            pushNotificationItemsState.collect { items ->
                loadPushNotificationEvents(items)
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
    
    private suspend fun loadSmsEvents(smsItems: List<IngestItem>) {
        classifiedDataRepository?.let { repo ->
            val eventsMap = mutableMapOf<String, List<Event>>()
            smsItems.forEach { item ->
                val events = repo.getAllEvents().filter { event ->
                    event.sourceType == "sms" && event.sourceId == item.id
                }
                eventsMap[item.id] = events
            }
            smsEventsState.value = eventsMap
        }
    }
    
    private suspend fun loadPushNotificationEvents(pushNotificationItems: List<IngestItem>) {
        classifiedDataRepository?.let { repo ->
            val eventsMap = mutableMapOf<String, List<Event>>()
            pushNotificationItems.forEach { item ->
                val events = repo.getAllEvents().filter { event ->
                    event.sourceType == "push_notification" && event.sourceId == item.id
                }
                eventsMap[item.id] = events
            }
            pushNotificationEventsState.value = eventsMap
        }
    }

    fun updateAccessToken(value: String) {
        loginState.update { it.copy(accessTokenInput = value) }
    }

    fun updateRefreshToken(value: String) {
        loginState.update { it.copy(refreshTokenInput = value) }
    }

    fun updateEmail(value: String) {
        loginState.update { it.copy(emailInput = value) }
    }

    fun updateScope(value: String) {
        loginState.update { it.copy(scopeInput = value) }
    }

    fun updateExpiresAt(value: String) {
        loginState.update { it.copy(expiresAtInput = value) }
    }
    
    // 여러 계정 관리 함수
    fun selectAccount(email: String?) {
        selectedAccountEmail.value = email
    }
    
    fun getGoogleAccounts(): List<com.example.agent_app.data.entity.AuthToken> {
        return googleAccountsState.value
    }
    
    fun getSelectedAccountEmail(): String? {
        return selectedAccountEmail.value
    }
    
    /**
     * Google Sign-In 결과 처리
     */
    fun handleGoogleSignInResult(data: android.content.Intent?) {
        viewModelScope.launch {
            loginState.update { it.copy(isGoogleLoginInProgress = true) }
            try {
                android.util.Log.d("MainViewModel", "Google Sign-In 결과 처리 시작 - data: ${data != null}")
                
                // OAuth 2.0 redirect URI로 돌아온 경우 처리
                val oauthFlow = com.example.agent_app.auth.GoogleOAuth2Flow(context)
                val authorizationCode = oauthFlow.extractAuthorizationCode(data)
                val error = oauthFlow.extractError(data)
                
                android.util.Log.d("MainViewModel", "OAuth 2.0 체크 - authorizationCode: ${authorizationCode != null}, error: $error")
                
                if (authorizationCode != null) {
                    // OAuth 2.0 플로우로 받은 authorization code를 토큰으로 교환
                    android.util.Log.d("MainViewModel", "OAuth 2.0 authorization code 받음")
                    val clientId = com.example.agent_app.BuildConfig.GOOGLE_WEB_CLIENT_ID
                    
                    when (val tokenResult = oauthFlow.exchangeCodeForTokens(authorizationCode, clientId)) {
                        is com.example.agent_app.auth.TokenExchangeResult.Success -> {
                            android.util.Log.d("MainViewModel", "토큰 교환 성공 - Refresh Token: ${tokenResult.refreshToken != null}")
                            
                            // 토큰 저장 (Refresh Token 포함!)
                            authRepository.upsertGoogleToken(
                                accessToken = tokenResult.accessToken,
                                refreshToken = tokenResult.refreshToken, // ✅ Refresh Token 포함!
                                scope = tokenResult.scope ?: DEFAULT_GMAIL_SCOPE,
                                expiresAt = tokenResult.expiresAt,
                                email = null, // 이메일은 나중에 API로 가져올 수 있음
                            )
                            loginState.update {
                                it.copy(
                                    statusMessage = "Google 계정이 추가되었습니다. Refresh Token이 포함되어 자동 갱신이 가능합니다.",
                                    isGoogleLoginInProgress = false,
                                )
                            }
                            return@launch
                        }
                        is com.example.agent_app.auth.TokenExchangeResult.Failure -> {
                            android.util.Log.e("MainViewModel", "토큰 교환 실패: ${tokenResult.message}")
                            loginState.update {
                                it.copy(
                                    statusMessage = "토큰 교환 실패: ${tokenResult.message}",
                                    isGoogleLoginInProgress = false,
                                )
                            }
                            return@launch
                        }
                    }
                } else if (error != null) {
                    android.util.Log.e("MainViewModel", "OAuth 2.0 오류: $error")
                    loginState.update {
                        it.copy(
                            statusMessage = "인증 실패: $error",
                            isGoogleLoginInProgress = false,
                        )
                    }
                    return@launch
                }
                
                // 기존 Google Sign-In 방식 (Refresh Token 없음)
                android.util.Log.d("MainViewModel", "Google Sign-In 방식으로 처리 시도")
                android.util.Log.d("MainViewModel", "Intent 정보 - action: ${data?.action}, dataString: ${data?.dataString}, extras: ${data?.extras?.keySet()}")
                
                val account = try {
                    googleSignInHelper.getSignInResultFromIntentAsync(data)
                } catch (e: com.google.android.gms.common.api.ApiException) {
                    // ApiException의 경우 상세 정보 표시
                    android.util.Log.e("MainViewModel", "Google Sign-In ApiException: ${e.statusCode}", e)
                    val errorMessage = when (e.statusCode) {
                        10 -> "개발자 오류: Google Sign-In이 제대로 설정되지 않았습니다."
                        12501 -> "사용자가 로그인을 취소했습니다."
                        7 -> "네트워크 연결 오류입니다. 인터넷 연결을 확인해주세요."
                        8 -> "앱 내부 오류가 발생했습니다."
                        else -> "Google 로그인 오류 (코드: ${e.statusCode})\n${e.status?.statusMessage ?: e.message}"
                    }
                    loginState.update {
                        it.copy(
                            statusMessage = errorMessage,
                            isGoogleLoginInProgress = false,
                        )
                    }
                    return@launch
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Google Sign-In 계정 가져오기 실패: ${e.javaClass.simpleName}", e)
                    android.util.Log.e("MainViewModel", "예외 상세: ${e.message}")
                    e.printStackTrace()
                    loginState.update {
                        it.copy(
                            statusMessage = "로그인 처리 중 오류 발생:\n${e.javaClass.simpleName}: ${e.message ?: "알 수 없는 오류"}",
                            isGoogleLoginInProgress = false,
                        )
                    }
                    return@launch
                }
                
                android.util.Log.d("MainViewModel", "계정 정보: ${account?.email ?: "null"}")
                if (account == null) {
                    android.util.Log.e("MainViewModel", "계정이 null입니다. Intent: ${data?.toString()}")
                    loginState.update {
                        it.copy(
                            statusMessage = "Google 로그인에 실패했습니다.\n\n가능한 원인:\n• 계정 선택을 취소함\n• 네트워크 연결 문제\n• Google Play Services 문제\n\n다시 시도하거나 OAuth 2.0 로그인을 사용해보세요.",
                            isGoogleLoginInProgress = false,
                        )
                    }
                    return@launch
                }
                
                if (account != null) {
                    android.util.Log.d("MainViewModel", "Google 계정 정보 받음: ${account.email}")
                    // Gmail scope로 토큰 가져오기
                    val result = googleAuthTokenProvider.fetchAccessToken(
                        account = account,
                        scope = DEFAULT_GMAIL_SCOPE
                    )
                    
                    when (result) {
                        is com.example.agent_app.auth.GoogleTokenFetchResult.Success -> {
                            android.util.Log.d("MainViewModel", "Access Token 가져오기 성공")
                            // ⚠️ Google Sign-In SDK는 refresh token을 제공하지 않습니다.
                            // Refresh token이 필요하면 OAuth 2.0 플로우를 사용하세요.
                            
                            // 토큰 저장 (expiresAt은 1시간 후로 설정)
                            val expiresAt = System.currentTimeMillis() + (3600 * 1000) // 1시간
                            authRepository.upsertGoogleToken(
                                accessToken = result.accessToken,
                                refreshToken = null, // ⚠️ Google Sign-In은 refresh token을 제공하지 않음
                                scope = DEFAULT_GMAIL_SCOPE,
                                expiresAt = expiresAt,
                                email = account.email,
                            )
                            loginState.update {
                                it.copy(
                                    statusMessage = "Google 계정이 추가되었습니다: ${account.email}\n⚠️ Refresh Token이 없어 수동 입력이 필요합니다.",
                                    isGoogleLoginInProgress = false,
                                )
                            }
                        }
                        is com.example.agent_app.auth.GoogleTokenFetchResult.NeedsConsent -> {
                            android.util.Log.w("MainViewModel", "권한 동의 필요")
                            // 권한 동의 필요 - Activity에서 처리해야 함
                            loginState.update {
                                it.copy(
                                    statusMessage = "권한 동의가 필요합니다.\nGmail 읽기 권한을 허용해주세요.",
                                    isGoogleLoginInProgress = false,
                                )
                            }
                        }
                        is com.example.agent_app.auth.GoogleTokenFetchResult.Failure -> {
                            android.util.Log.e("MainViewModel", "토큰 가져오기 실패: ${result.message}")
                            loginState.update {
                                it.copy(
                                    statusMessage = "토큰 가져오기 실패:\n${result.message}\n\nOAuth 2.0 로그인(Refresh Token 포함) 버튼을 사용해보세요.",
                                    isGoogleLoginInProgress = false,
                                )
                            }
                        }
                    }
                } else {
<<<<<<< HEAD
                    android.util.Log.e("MainViewModel", "Google Sign-In 계정 정보를 받지 못함. Intent: ${data?.data}")
                    loginState.update {
                        it.copy(
                            statusMessage = "Google 로그인에 실패했습니다.\n\n가능한 원인:\n• 계정 선택을 취소함\n• 네트워크 연결 문제\n• Google Play Services 문제\n\n다시 시도하거나 OAuth 2.0 로그인을 사용해보세요.",
=======
                    android.util.Log.e("MainViewModel", "Google Sign-In 실패 - 계정 정보가 null입니다. data: ${data?.dataString}")
                    
                    // Logcat에서 DEVELOPER_ERROR 확인
                    val isDeveloperError = android.util.Log.isLoggable("GoogleSignInHelper", android.util.Log.ERROR)
                    
                    // 더 구체적인 에러 메시지 제공
                    val errorMessage = buildString {
                        append("Google 로그인에 실패했습니다.\n\n")
                        append("⚠️ 가장 흔한 원인: SHA-1 인증서 미등록\n\n")
                        append("해결 방법:\n")
                        append("1. Android Studio에서 Gradle 탭 열기\n")
                        append("2. app → Tasks → android → signingReport 실행\n")
                        append("3. 출력된 SHA-1 값을 복사\n")
                        append("4. Google Cloud Console 접속:\n")
                        append("   https://console.cloud.google.com/\n")
                        append("5. API 및 서비스 → 사용자 인증 정보\n")
                        append("6. OAuth 2.0 클라이언트 ID 선택\n")
                        append("7. Android 앱 타입으로 추가:\n")
                        append("   - 패키지 이름: com.example.agent_app\n")
                        append("   - SHA-1 인증서 지문: (복사한 값)\n\n")
                        append("Logcat에서 'GoogleSignInHelper' 태그로 statusCode를 확인하세요.")
                    }
                    
                    loginState.update {
                        it.copy(
                            statusMessage = errorMessage,
>>>>>>> moa/main
                            isGoogleLoginInProgress = false,
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Google Sign-In 처리 실패", e)
                loginState.update {
                    it.copy(
                        statusMessage = "Google 로그인 처리 중 오류: ${e.message}",
                        isGoogleLoginInProgress = false,
                    )
                }
            }
        }
    }

    fun saveToken(email: String? = null) {
        val state = loginState.value
        val access = state.accessTokenInput.trim()
        if (access.isEmpty()) {
            loginState.update { it.copy(statusMessage = "액세스 토큰을 입력해 주세요.") }
            return
        }
        val expiresAt = state.expiresAtInput.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
        val accountEmail = email?.trim()?.takeIf { it.isNotEmpty() } ?: state.emailInput.trim().takeIf { it.isNotEmpty() }
        
        // 디버깅을 위한 로그 추가
        android.util.Log.d("MainViewModel", "토큰 저장 시도 - Email: $accountEmail")
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
                    email = accountEmail,
                )
                android.util.Log.d("MainViewModel", "토큰 저장 성공")
                loginState.update {
                    it.copy(
                        accessTokenInput = "",
                        refreshTokenInput = "",
                        emailInput = "",
                        expiresAtInput = expiresAt?.toString() ?: "",
                        scopeInput = it.scopeInput.ifEmpty { DEFAULT_GMAIL_SCOPE },
                        statusMessage = "토큰이 저장되었습니다.${if (accountEmail != null) " (계정: $accountEmail)" else ""}",
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

    fun clearToken(email: String? = null) {
        viewModelScope.launch {
            authRepository.clearGoogleToken(email)
            loginState.update {
                it.copy(
                    statusMessage = if (email != null) {
                        "계정 $email 의 토큰을 삭제했습니다."
                    } else {
                        "저장된 토큰 정보를 삭제했습니다."
                    },
                )
            }
        }
    }

    fun syncGmail(accountEmail: String, sinceTimestamp: Long = 0L, useBackgroundService: Boolean = true) {
        viewModelScope.launch {
            val emailKey = accountEmail.takeIf { it.isNotEmpty() } ?: ""
            val currentStates = gmailSyncState.value.syncStatesByEmail.toMutableMap()
            
            try {
                android.util.Log.d("MainViewModel", "syncGmail 호출 - accountEmail: $accountEmail, sinceTimestamp: $sinceTimestamp, useBackgroundService: $useBackgroundService")
                
                // 백그라운드 서비스 사용 시
                if (useBackgroundService) {
                    android.util.Log.d("MainViewModel", "백그라운드 서비스로 동기화 시작")
                    com.example.agent_app.service.GmailSyncService.startService(
                        context = context,
                        accountEmail = accountEmail,
                        sinceTimestamp = sinceTimestamp,
                        manualSync = true
                    )
                    
                    // 상태 업데이트 (백그라운드에서 진행 중)
                    currentStates[emailKey] = AccountSyncState(
                        isSyncing = true,
                        message = null,
                        recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                        lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                        progressMessage = "백그라운드에서 동기화 중...",
                        progress = 0.1f
                    )
                    gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                    return@launch
                }
                
                // 계정별 동기화 상태 업데이트
                currentStates[emailKey] = AccountSyncState(
                    isSyncing = true,
                    message = null,
                    recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                    lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                    progressMessage = "Gmail 메시지 목록 조회 중...",
                    progress = 0.1f
                )
                gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                
                val token = authRepository.getGoogleTokenByEmail(accountEmail)
                
                android.util.Log.d("MainViewModel", "계정: $accountEmail")
                android.util.Log.d("MainViewModel", "토큰 조회 결과: $token")
                android.util.Log.d("MainViewModel", "동기화 시작 시간: $sinceTimestamp")
                
                if (token?.accessToken.isNullOrBlank()) {
                    currentStates[emailKey] = AccountSyncState(
                        isSyncing = false,
                        message = "저장된 토큰이 없어 Gmail을 동기화할 수 없습니다.",
                        recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                        lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                        progress = 0f,
                        tokenExpired = true, // 토큰 만료 표시
                    )
                    gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                    return@launch
                }
                
                // 동기화 진행 상태 업데이트
                currentStates[emailKey] = currentStates[emailKey]!!.copy(
                    progressMessage = "메시지 처리 중...",
                    progress = 0.3f
                )
                gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                
                // Access Token 만료 체크 및 갱신
                var accessToken = token!!.accessToken
                var shouldUpdateToken = false
                var newExpiresAt: Long? = token.expiresAt
                
                // 토큰 만료 체크 (expiresAt이 있고 현재 시간보다 이전이면 만료)
                if (token.expiresAt != null && token.expiresAt!! < System.currentTimeMillis()) {
                    android.util.Log.d("MainViewModel", "Access Token 만료됨, Refresh Token으로 갱신 시도")
                    
                    // Refresh Token이 있으면 갱신 시도
                    if (!token.refreshToken.isNullOrBlank()) {
                        try {
                            val refresher = com.example.agent_app.auth.GoogleTokenRefresher()
                            val clientId = com.example.agent_app.BuildConfig.GOOGLE_WEB_CLIENT_ID
                            
                            when (val refreshResult = refresher.refreshAccessToken(token.refreshToken, clientId)) {
                                is com.example.agent_app.auth.TokenRefreshResult.Success -> {
                                    accessToken = refreshResult.accessToken
                                    newExpiresAt = refreshResult.expiresAt
                                    shouldUpdateToken = true
                                    android.util.Log.d("MainViewModel", "Access Token 갱신 성공")
                                }
                                is com.example.agent_app.auth.TokenRefreshResult.Failure -> {
                                    android.util.Log.e("MainViewModel", "Access Token 갱신 실패: ${refreshResult.message}")
                                    currentStates[emailKey] = AccountSyncState(
                                        isSyncing = false,
                                        message = "토큰 갱신 실패: ${refreshResult.message}. 다시 로그인해주세요.",
                                        recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                                        lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                                        progress = 0f,
                                    )
                                    gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                                    return@launch
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainViewModel", "토큰 갱신 중 오류", e)
                            currentStates[emailKey] = AccountSyncState(
                                isSyncing = false,
                                message = "토큰 갱신 중 오류가 발생했습니다: ${e.message}",
                                recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                                lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                                progress = 0f,
                            )
                            gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                            return@launch
                        }
                    } else {
                        android.util.Log.w("MainViewModel", "Refresh Token이 없어 토큰을 갱신할 수 없습니다")
                        currentStates[emailKey] = AccountSyncState(
                            isSyncing = false,
                            message = "토큰이 만료되었고 Refresh Token이 없습니다. 다시 로그인해주세요.",
                            recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                            lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                            progress = 0f,
                        )
                        gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                        return@launch
                    }
                }
                
                // 갱신된 토큰이 있으면 저장
                if (shouldUpdateToken) {
                    authRepository.upsertGoogleToken(
                        accessToken = accessToken,
                        refreshToken = token.refreshToken,
                        scope = token.scope,
                        expiresAt = newExpiresAt,
                        email = accountEmail,
                    )
                    android.util.Log.d("MainViewModel", "갱신된 Access Token 저장 완료")
                }
                
                // 기간 선택 시 Gmail 자동 처리 활성화 및 기간 저장
                if (sinceTimestamp > 0L) {
                    com.example.agent_app.util.AutoProcessSettings.enableGmailAutoProcess(
                        context,
                        sinceTimestamp,
                        System.currentTimeMillis()
                    )
                }
                
                when (val result = gmailRepository.syncRecentMessages(
                    accessToken = accessToken,
                    sinceTimestamp = sinceTimestamp,
                    onProgress = { progress, message ->
                        // 진행률 실시간 업데이트
                        currentStates[emailKey] = currentStates[emailKey]!!.copy(
                            progress = progress,
                            progressMessage = message
                        )
                        gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                    }
                )) {
                is GmailSyncResult.Success -> {
                    // Gmail 동기화 후 분류된 데이터 다시 로드
                    classifiedDataRepository?.let { repo ->
                        contactsState.value = repo.getAllContacts()
                        eventsState.value = repo.getAllEvents()
                        notesState.value = repo.getAllNotes()
                    }
                    
                    // Gmail 아이템은 Flow를 통해 자동으로 업데이트됨
                    
                    // 업데이트 기록 추가
                    val currentAccountState = currentStates[emailKey]!!
                    val newRecord = GmailUpdateRecord(
                        timestamp = System.currentTimeMillis(),
                        startTimestamp = result.startTimestamp,
                        endTimestamp = result.endTimestamp,
                        processedCount = result.upsertedCount,
                        eventCount = result.eventCount,
                        message = "${result.upsertedCount}개의 메시지를 동기화했습니다. 일정 ${result.eventCount}개 추출",
                    )
                    val updatedRecords = (listOf(newRecord) + currentAccountState.recentUpdates).take(10)
                    
                    currentStates[emailKey] = AccountSyncState(
                        isSyncing = false,
                        message = "${result.upsertedCount}개의 메시지를 동기화했습니다. 일정 ${result.eventCount}개 추출",
                        recentUpdates = updatedRecords,
                        lastSyncTimestamp = result.endTimestamp,
                        progressMessage = null,
                        progress = 1.0f,
                    )
                    gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                }
                is GmailSyncResult.Unauthorized -> {
                    android.util.Log.w("MainViewModel", "Gmail API 401 에러 - Refresh Token으로 재시도")
                    
                    // Refresh Token이 있으면 갱신 시도
                    if (!token.refreshToken.isNullOrBlank()) {
                        try {
                            val refresher = com.example.agent_app.auth.GoogleTokenRefresher()
                            val clientId = com.example.agent_app.BuildConfig.GOOGLE_WEB_CLIENT_ID
                            
                            when (val refreshResult = refresher.refreshAccessToken(token.refreshToken, clientId)) {
                                is com.example.agent_app.auth.TokenRefreshResult.Success -> {
                                    // 갱신된 토큰 저장
                                    authRepository.upsertGoogleToken(
                                        accessToken = refreshResult.accessToken,
                                        refreshToken = token.refreshToken,
                                        scope = token.scope,
                                        expiresAt = refreshResult.expiresAt,
                                        email = accountEmail,
                                    )
                                    
                                    android.util.Log.d("MainViewModel", "토큰 갱신 후 Gmail 동기화 재시도")
                                    
                                    // 갱신된 토큰으로 재시도
                                    when (val retryResult = gmailRepository.syncRecentMessages(
                                        accessToken = refreshResult.accessToken,
                                        sinceTimestamp = sinceTimestamp,
                                        onProgress = { progress, message ->
                                            // 진행률 실시간 업데이트
                                            currentStates[emailKey] = currentStates[emailKey]!!.copy(
                                                progress = progress,
                                                progressMessage = message
                                            )
                                            gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                                        }
                                    )) {
                                        is GmailSyncResult.Success -> {
                                            // 성공 처리
                                            classifiedDataRepository?.let { repo ->
                                                contactsState.value = repo.getAllContacts()
                                                eventsState.value = repo.getAllEvents()
                                                notesState.value = repo.getAllNotes()
                                            }
                                            
                                            val currentAccountState = currentStates[emailKey]!!
                                            val newRecord = GmailUpdateRecord(
                                                timestamp = System.currentTimeMillis(),
                                                startTimestamp = retryResult.startTimestamp,
                                                endTimestamp = retryResult.endTimestamp,
                                                processedCount = retryResult.upsertedCount,
                                                eventCount = retryResult.eventCount,
                                                message = "${retryResult.upsertedCount}개의 메시지를 동기화했습니다. 일정 ${retryResult.eventCount}개 추출"
                                            )
                                            val updatedRecords = (listOf(newRecord) + currentAccountState.recentUpdates).take(10)
                                            
                                            currentStates[emailKey] = AccountSyncState(
                                                isSyncing = false,
                                                message = "${retryResult.upsertedCount}개의 메시지를 동기화했습니다. 일정 ${retryResult.eventCount}개 추출",
                                                recentUpdates = updatedRecords,
                                                lastSyncTimestamp = retryResult.endTimestamp,
                                                progressMessage = null,
                                                progress = 1.0f,
                                            )
                                            gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                                        }
                                        is GmailSyncResult.Unauthorized -> {
                                            currentStates[emailKey] = AccountSyncState(
                                                isSyncing = false,
                                                message = "토큰 갱신 후에도 인증에 실패했습니다. 다시 로그인해주세요.",
                                                recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                                                lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                                                progress = 0f,
                                            )
                                            gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                                        }
                                        is GmailSyncResult.NetworkError -> {
                                            currentStates[emailKey] = AccountSyncState(
                                                isSyncing = false,
                                                message = "네트워크 오류: ${retryResult.message}",
                                                recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                                                lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                                                progress = 0f,
                                            )
                                            gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                                        }
                                        GmailSyncResult.MissingToken -> {
                                            currentStates[emailKey] = AccountSyncState(
                                                isSyncing = false,
                                                message = "토큰이 없습니다.",
                                                recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                                                lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                                                progress = 0f,
                                            )
                                            gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                                        }
                                    }
                                }
                                is com.example.agent_app.auth.TokenRefreshResult.Failure -> {
                                    android.util.Log.e("MainViewModel", "토큰 갱신 실패: ${refreshResult.message}")
                                    currentStates[emailKey] = AccountSyncState(
                                        isSyncing = false,
                                        message = "토큰 갱신 실패: ${refreshResult.message}. 다시 로그인해주세요.",
                                        recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                                        lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                                        progress = 0f,
                                    )
                                    gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainViewModel", "토큰 갱신 중 오류", e)
                            currentStates[emailKey] = AccountSyncState(
                                isSyncing = false,
                                message = "토큰 갱신 중 오류가 발생했습니다: ${e.message}",
                                recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                                lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                                progress = 0f,
                            )
                            gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                        }
                    } else {
                        currentStates[emailKey] = AccountSyncState(
                            isSyncing = false,
                            message = "Gmail API 인증에 실패했습니다. Refresh Token이 없어 갱신할 수 없습니다. 다시 로그인해주세요.",
                            recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                            lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                            progress = 0f,
                        )
                        gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                    }
                }
                is GmailSyncResult.NetworkError -> {
                    currentStates[emailKey] = currentStates[emailKey]!!.copy(
                        isSyncing = false,
                        message = "네트워크 오류: ${result.message}",
                        progressMessage = null,
                        progress = 0f,
                    )
                    gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                }
                GmailSyncResult.MissingToken -> {
                    currentStates[emailKey] = currentStates[emailKey]!!.copy(
                        isSyncing = false,
                        message = "저장된 토큰이 없어 Gmail을 동기화할 수 없습니다.",
                        progressMessage = null,
                        progress = 0f,
                    )
                    gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
                }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "syncGmail 중 예상치 못한 오류 발생", e)
                currentStates[emailKey] = AccountSyncState(
                    isSyncing = false,
                    message = "동기화 중 오류가 발생했습니다: ${e.message}",
                    recentUpdates = currentStates[emailKey]?.recentUpdates ?: emptyList(),
                    lastSyncTimestamp = currentStates[emailKey]?.lastSyncTimestamp ?: 0L,
                    progress = 0f,
                )
                gmailSyncState.value = GmailSyncState(syncStatesByEmail = currentStates)
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
        smsScanState.update { it.copy(message = null) }
        // GmailSyncState는 계정별 상태를 가지고 있으므로 개별적으로 처리하지 않음
    }
    
    fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * NotificationListenerService 권한이 활성화되어 있는지 확인
     */
    fun checkNotificationListenerPermission(): Boolean {
        val enabledNotificationListeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        
        if (enabledNotificationListeners.isNullOrEmpty()) {
            return false
        }
        
        val packageName = context.packageName
        return enabledNotificationListeners.contains(packageName)
    }
    
    /**
     * NotificationListenerService 설정 화면으로 이동
     */
    fun openNotificationListenerSettings() {
        val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
    
    /**
     * 푸시 알림 통계 조회
     */
    suspend fun getPushNotificationStats(startTime: Long? = null, endTime: Long? = null): PushNotificationStats {
        return withContext(Dispatchers.IO) {
            val database = com.example.agent_app.data.db.AppDatabase.build(context)
            val dao = database.pushNotificationDao()
            
            val appStats = dao.getAppStatistics(startTime, endTime)
            val hourlyStats = dao.getHourlyStatistics(startTime, endTime)
            val totalCount = dao.getByTimestampRange(startTime, endTime).size
            
            PushNotificationStats(
                totalCount = totalCount,
                appStatistics = appStats.map { 
                    AppStat(it.package_name, it.app_name ?: "알 수 없음", it.count) 
                },
                hourlyStatistics = hourlyStats.map { 
                    HourlyStat(it.hour.toIntOrNull() ?: 0, it.count) 
                }
            )
        }
    }

    /**
     * 특정 앱의 푸시 알림 저장 제외 설정 토글
     */
    fun togglePushNotificationExclusion(packageName: String, exclude: Boolean) {
        if (packageName.isBlank()) return
        if (exclude) {
            com.example.agent_app.util.PushNotificationFilterSettings.addExcludedPackage(context, packageName)
        } else {
            com.example.agent_app.util.PushNotificationFilterSettings.removeExcludedPackage(context, packageName)
        }
    }
    
    /**
     * 특정 앱의 푸시 알림 목록 조회
     */
    suspend fun getPushNotificationsByPackage(
        packageName: String,
        limit: Int = 100
    ): List<com.example.agent_app.data.entity.PushNotification> {
        return withContext(Dispatchers.IO) {
            val database = com.example.agent_app.data.db.AppDatabase.build(context)
            val dao = database.pushNotificationDao()
            dao.getByPackage(packageName).take(limit)
        }
    }
    
    fun scanSmsMessages(sinceTimestamp: Long) {
        // 권한 확인 (서비스 시작 전에 확인)
        if (!checkSmsPermission()) {
            smsScanState.value = SmsScanState(
                isScanning = false,
                message = "SMS 읽기 권한이 필요합니다. 설정에서 권한을 허용해주세요.",
                progress = 0f,
                progressMessage = null,
                scanStartTimestamp = sinceTimestamp,
                scanEndTimestamp = System.currentTimeMillis(),
                processedCount = 0,
                totalCount = 0,
            )
            return
        }
        
        // 기간 선택 시 자동 처리 활성화 및 기간 저장
        com.example.agent_app.util.AutoProcessSettings.enableSmsAutoProcess(
            context, 
            sinceTimestamp, 
            System.currentTimeMillis()
        )
        
        // Foreground Service로 백그라운드 처리 시작
        val intent = android.content.Intent(context, com.example.agent_app.service.SmsScanService::class.java).apply {
            putExtra("since_timestamp", sinceTimestamp)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        // 스캔 시작 상태 업데이트
        smsScanState.value = SmsScanState(
            isScanning = true,
            message = "SMS 스캔 시작...",
            progress = 0f,
            progressMessage = "SMS 메시지 읽는 중...",
            scanStartTimestamp = sinceTimestamp,
            scanEndTimestamp = System.currentTimeMillis(),
            processedCount = 0,
            totalCount = 0,
        )
    }
    
    fun onSmsScanProgress(
        startTimestamp: Long,
        endTimestamp: Long,
        processedCount: Int,
        totalCount: Int,
        progress: Float,
        progressMessage: String
    ) {
        viewModelScope.launch {
            smsScanState.value = smsScanState.value.copy(
                progress = progress,
                progressMessage = progressMessage,
                scanStartTimestamp = startTimestamp,
                scanEndTimestamp = endTimestamp,
                processedCount = processedCount,
                totalCount = totalCount,
            )
        }
    }
    
    fun onSmsScanComplete(
        startTimestamp: Long,
        endTimestamp: Long,
        processedCount: Int,
        eventCount: Int,
        message: String
    ) {
        viewModelScope.launch {
            // 업데이트 기록 추가
            val newRecord = SmsUpdateRecord(
                timestamp = System.currentTimeMillis(),
                startTimestamp = startTimestamp,
                endTimestamp = endTimestamp,
                processedCount = processedCount,
                eventCount = eventCount,
                message = message
            )
            val currentRecords = smsScanState.value.recentUpdates
            val updatedRecords = (listOf(newRecord) + currentRecords).take(10) // 최근 10개만 유지
            
            smsScanState.value = SmsScanState(
                isScanning = false,
                message = message,
                recentUpdates = updatedRecords,
                lastScanTimestamp = endTimestamp, // 마지막 스캔 시간 업데이트
                progress = 1.0f,
                progressMessage = null,
                scanStartTimestamp = 0L,
                scanEndTimestamp = 0L,
                processedCount = 0,
                totalCount = 0,
            )
            
            // 분류된 데이터 다시 로드
            classifiedDataRepository?.let { repo ->
                contactsState.value = repo.getAllContacts()
                eventsState.value = repo.getAllEvents()
                notesState.value = repo.getAllNotes()
            }
            
            // SMS 이벤트 다시 로드
            loadSmsEvents(smsItemsState.value)
        }
    }
    
    /**
     * 일정 업데이트
     */
    fun updateEvent(event: Event) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    eventDao?.update(event)
                    // 이벤트 목록 새로고침
                    classifiedDataRepository?.let { repo ->
                        eventsState.value = repo.getAllEvents()
                        // OCR 및 SMS 이벤트도 새로고침
                        loadOcrEvents(ocrItemsState.value)
                        loadSmsEvents(smsItemsState.value)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "일정 업데이트 실패", e)
            }
        }
    }
    
    /**
     * 분류된 데이터 다시 로드
     */
    fun loadClassifiedData() {
        viewModelScope.launch {
            classifiedDataRepository?.let { repo ->
                contactsState.value = repo.getAllContacts()
                eventsState.value = repo.getAllEvents()
                notesState.value = repo.getAllNotes()
            }
        }
    }
    
    /**
     * 인박스 데이터 새로고침
     * Gmail, SMS, OCR, 푸시 알림 데이터와 이벤트를 모두 다시 로드
     */
    fun refreshInboxData() {
        viewModelScope.launch {
            // 분류된 데이터 새로고침
            loadClassifiedData()
            
            // 각 소스별 이벤트 다시 로드
            loadOcrEvents(ocrItemsState.value)
            loadSmsEvents(smsItemsState.value)
            loadPushNotificationEvents(pushNotificationItemsState.value)
            
            // Gmail 이벤트는 eventsState에서 필터링되므로 자동 업데이트됨
            android.util.Log.d("MainViewModel", "인박스 데이터 새로고침 완료")
        }
    }
    
    /**
     * IngestItem에서 일정 생성
     */
    fun createEventFromItem(item: IngestItem) {
        viewModelScope.launch {
            try {
                when (item.source) {
                    "sms" -> {
                        val result = aiAgent.processSMSForEvent(
                            smsBody = item.body ?: "",
                            smsAddress = item.title ?: "Unknown",
                            receivedTimestamp = item.timestamp,
                            originalSmsId = item.id
                        )
                        android.util.Log.d("MainViewModel", "SMS 일정 생성 완료: ${result.events.size}개")
                    }
                    "ocr" -> {
                        val result = aiAgent.createEventFromImage(
                            ocrText = item.body ?: "",
                            currentTimestamp = item.timestamp,
                            originalOcrId = item.id
                        )
                        android.util.Log.d("MainViewModel", "OCR 일정 생성 완료: ${result.events.size}개")
                    }
                    "gmail" -> {
                        val result = aiAgent.processGmailForEvent(
                            emailSubject = item.title ?: "",
                            emailBody = item.body ?: "",
                            receivedTimestamp = item.timestamp,
                            originalEmailId = item.id
                        )
                        android.util.Log.d("MainViewModel", "Gmail 일정 생성 완료: ${result.events.size}개")
                    }
                    "push_notification" -> {
                        val result = aiAgent.processPushNotificationForEvent(
                            appName = item.title ?: "Unknown",
                            notificationTitle = null,
                            notificationText = item.body ?: "",
                            notificationSubText = null,
                            receivedTimestamp = item.timestamp,
                            originalNotificationId = item.id
                        )
                        android.util.Log.d("MainViewModel", "푸시 알림 일정 생성 완료: ${result.events.size}개")
                    }
                    else -> {
                        android.util.Log.w("MainViewModel", "지원하지 않는 소스 타입: ${item.source}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "일정 생성 실패", e)
            }
        }
    }
    
    /**
     * 자연어 텍스트에서 일정 생성 (UI 레이어에서만 사용)
     * 기존 ChatGateway의 tryCreateEventFromQuestion 로직을 재사용
     */
    fun createEventFromNaturalLanguage(text: String) {
        viewModelScope.launch {
            try {
                if (executeChatUseCase == null) {
                    android.util.Log.e("MainViewModel", "ExecuteChatUseCase가 주입되지 않았습니다.")
                    throw IllegalStateException("ExecuteChatUseCase가 필요합니다.")
                }
                
                // ExecuteChatUseCase를 통해 일정 생성 의도가 감지되도록 질문 구성
                // ChatGateway의 detectEventCreationIntent가 감지할 수 있도록 명시적 키워드 추가
                val question = if (text.contains("약속") || text.contains("일정") || text.contains("회의")) {
                    text
                } else {
                    "일정 잡아줘: $text" // 일정 생성 의도가 명확하도록 키워드 추가
                }
                
                // ExecuteChatUseCase를 통해 일정 생성 (내부적으로 tryCreateEventFromQuestion 호출)
                val result = executeChatUseCase(question, emptyList())
                android.util.Log.d("MainViewModel", "자연어 일정 생성 완료: ${result.answer.content}")
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "자연어 일정 생성 실패", e)
                throw e
            }
        }
    }
    
    /**
     * 통화 녹음 텍스트 파일 스캔
     */
    fun scanCallRecordTexts(sinceTimestamp: Long) {
        viewModelScope.launch {
            try {
                callRecordScanState.value = CallRecordScanState(
                    isScanning = true,
                    message = null
                )
                
                val ocrRepo = ocrRepository
                    ?: throw IllegalStateException("OcrRepositoryWithAi가 초기화되지 않았습니다")
                
                val processor = CallRecordTextProcessor(
                    context = context,
                    ocrRepository = ocrRepo
                )
                
                val result = processor.processRecentCallRecordTexts(sinceTimestamp) { fileName, isProcessing ->
                    callRecordScanState.value = CallRecordScanState(
                        isScanning = true,
                        message = if (isProcessing) "처리 중: $fileName" else null
                    )
                }
                
                // 분류된 데이터 다시 로드
                classifiedDataRepository?.let { repo ->
                    contactsState.value = repo.getAllContacts()
                    eventsState.value = repo.getAllEvents()
                    notesState.value = repo.getAllNotes()
                }
                
                // OCR 이벤트 다시 로드
                loadOcrEvents(ocrItemsState.value)
                
                callRecordScanState.value = CallRecordScanState(
                    isScanning = false,
                    message = "${result.successCount}개의 통화 녹음 텍스트를 처리했습니다. ${result.eventCount}개의 일정을 추출했습니다."
                )
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "통화 녹음 텍스트 스캔 실패", e)
                callRecordScanState.value = CallRecordScanState(
                    isScanning = false,
                    message = "통화 녹음 텍스트 스캔 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 일정 삭제
     */
    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    eventDao?.delete(event)
                    // 이벤트 목록 새로고침
                    classifiedDataRepository?.let { repo ->
                        eventsState.value = repo.getAllEvents()
                        // OCR 및 SMS 이벤트도 새로고침
                        loadOcrEvents(ocrItemsState.value)
                        loadSmsEvents(smsItemsState.value)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "일정 삭제 실패", e)
            }
        }
    }
}

data class SmsScanState(
    val isScanning: Boolean = false,
    val message: String? = null,
    val recentUpdates: List<SmsUpdateRecord> = emptyList(),
    val lastScanTimestamp: Long = 0L, // 마지막 스캔 시간
    val progress: Float = 0f, // 진행률 (0.0 ~ 1.0)
    val progressMessage: String? = null, // 진행 상황 메시지
    val scanStartTimestamp: Long = 0L, // 현재 스캔 시작 시간
    val scanEndTimestamp: Long = 0L, // 현재 스캔 예상 종료 시간
    val processedCount: Int = 0, // 처리된 메시지 수
    val totalCount: Int = 0, // 전체 메시지 수
)

data class SmsUpdateRecord(
    val timestamp: Long,
    val startTimestamp: Long,  // SMS 스캔 시작 시간 (범위)
    val endTimestamp: Long,    // SMS 스캔 끝 시간 (범위)
    val processedCount: Int,
    val eventCount: Int,
    val message: String,
)

data class GmailSyncState(
    val syncStatesByEmail: Map<String, AccountSyncState> = emptyMap(), // 계정별 동기화 상태
)

data class AccountSyncState(
    val isSyncing: Boolean = false,
    val message: String? = null,
    val recentUpdates: List<GmailUpdateRecord> = emptyList(),
    val lastSyncTimestamp: Long = 0L,
    val progressMessage: String? = null,
    val progress: Float = 0f,
    val tokenExpired: Boolean = false, // 토큰 만료 여부
)

data class GmailUpdateRecord(
    val timestamp: Long,
    val startTimestamp: Long,  // Gmail 스캔 시작 시간 (범위)
    val endTimestamp: Long,    // Gmail 스캔 끝 시간 (범위)
    val processedCount: Int,
    val eventCount: Int,
    val message: String,
)

data class CallRecordScanState(
    val isScanning: Boolean = false,
    val message: String? = null,
)

data class AssistantUiState(
    val loginState: LoginUiState = LoginUiState(),
    val gmailItems: List<IngestItem> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val events: List<Event> = emptyList(),
    val notes: List<Note> = emptyList(),
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val syncState: SyncState = SyncState(),
    val ocrItems: List<IngestItem> = emptyList(),
    val ocrEvents: Map<String, List<Event>> = emptyMap(),
    val smsItems: List<IngestItem> = emptyList(),
    val smsEvents: Map<String, List<Event>> = emptyMap(),
    val pushNotificationItems: List<IngestItem> = emptyList(),
    val pushNotificationEvents: Map<String, List<Event>> = emptyMap(),
    val smsScanState: SmsScanState = SmsScanState(),
    val gmailSyncState: GmailSyncState = GmailSyncState(),
    val callRecordScanState: CallRecordScanState = CallRecordScanState(),
)

data class LoginUiState(
    val accessTokenInput: String = "",
    val refreshTokenInput: String = "",
    val emailInput: String = "", // 계정 이메일 입력 필드
    val scopeInput: String = DEFAULT_GMAIL_SCOPE,
    val expiresAtInput: String = "",
    val hasStoredToken: Boolean = false,
    val storedScope: String? = null,
    val storedExpiresAt: Long? = null,
    val statusMessage: String? = null,
    val isGoogleLoginInProgress: Boolean = false, // Google 로그인 진행 중 여부
)

data class SyncState(
    val isSyncing: Boolean = false,
    val message: String? = null,
)

data class PushNotificationStats(
    val totalCount: Int,
    val appStatistics: List<AppStat>,
    val hourlyStatistics: List<HourlyStat>,
)

data class AppStat(
    val packageName: String,
    val appName: String,
    val count: Int,
)

data class HourlyStat(
    val hour: Int,
    val count: Int,
)