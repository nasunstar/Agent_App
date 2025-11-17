package com.example.agent_app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.agent_app.data.repo.AuthRepository
import com.example.agent_app.data.repo.ClassifiedDataRepository
import com.example.agent_app.data.repo.GmailRepositoryWithAi
import com.example.agent_app.data.repo.IngestRepository
import com.example.agent_app.data.entity.User
import com.example.agent_app.di.AppContainer
import com.example.agent_app.service.CallRecordTextProcessor
import com.example.agent_app.ui.chat.ChatViewModel
import com.example.agent_app.ui.share.ShareCalendarViewModel
import com.example.agent_app.ui.share.ShareCalendarViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.agent_app.ui.theme.AgentAppTheme
import android.util.Log

class MainActivity : ComponentActivity() {

    private val appContainer: AppContainer by lazy { AppContainer(applicationContext) }

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            appContainer.authRepository,
            appContainer.ingestRepository,
            appContainer.gmailRepository,
            appContainer.classifiedDataRepository,
            appContainer.huenDongMinAiAgent,
            applicationContext,
            appContainer.eventDao,
            appContainer.ocrRepository,
        )
    }

    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(appContainer.executeChatUseCase)
    }

    private val shareCalendarViewModel: ShareCalendarViewModel by viewModels {
        ShareCalendarViewModelFactory(appContainer.shareCalendarRepository)
    }

    private val scanCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                com.example.agent_app.service.SmsScanService.ACTION_SCAN_COMPLETE -> {
                    val startTimestamp = intent.getLongExtra(com.example.agent_app.service.SmsScanService.EXTRA_START_TIMESTAMP, 0L)
                    val endTimestamp = intent.getLongExtra(com.example.agent_app.service.SmsScanService.EXTRA_END_TIMESTAMP, 0L)
                    val processedCount = intent.getIntExtra(com.example.agent_app.service.SmsScanService.EXTRA_PROCESSED_COUNT, 0)
                    val eventCount = intent.getIntExtra(com.example.agent_app.service.SmsScanService.EXTRA_EVENT_COUNT, 0)
                    val message = intent.getStringExtra(com.example.agent_app.service.SmsScanService.EXTRA_MESSAGE) ?: ""
                    
                    mainViewModel.onSmsScanComplete(startTimestamp, endTimestamp, processedCount, eventCount, message)
                    
                    // 데이터 새로고침
                    mainViewModel.loadClassifiedData()
                }
                com.example.agent_app.service.SmsScanService.ACTION_SCAN_PROGRESS -> {
                    val startTimestamp = intent.getLongExtra(com.example.agent_app.service.SmsScanService.EXTRA_START_TIMESTAMP, 0L)
                    val endTimestamp = intent.getLongExtra(com.example.agent_app.service.SmsScanService.EXTRA_END_TIMESTAMP, 0L)
                    val processedCount = intent.getIntExtra(com.example.agent_app.service.SmsScanService.EXTRA_PROCESSED_COUNT, 0)
                    val totalCount = intent.getIntExtra(com.example.agent_app.service.SmsScanService.EXTRA_TOTAL_COUNT, 0)
                    val progress = intent.getFloatExtra(com.example.agent_app.service.SmsScanService.EXTRA_PROGRESS, 0f)
                    val progressMessage = intent.getStringExtra(com.example.agent_app.service.SmsScanService.EXTRA_MESSAGE) ?: ""
                    
                    mainViewModel.onSmsScanProgress(
                        startTimestamp = startTimestamp,
                        endTimestamp = endTimestamp,
                        processedCount = processedCount,
                        totalCount = totalCount,
                        progress = progress,
                        progressMessage = progressMessage
                    )
                }
            }
        }
    }
    
    // Google Sign-In 결과 처리
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Google Sign-In은 일반 Intent로 처리됨
    }
    
    private val googleSignInActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("MainActivity", "Google Sign-In 결과 받음 - resultCode: ${result.resultCode}, data: ${result.data != null}")
        if (result.data != null) {
            android.util.Log.d("MainActivity", "Intent data: ${result.data?.dataString}")
        }
        mainViewModel.handleGoogleSignInResult(result.data)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = appContainer.database
        // 기본 사용자 생성
        CoroutineScope(Dispatchers.IO).launch {
            val existingUser = database.userDao().getById(1L)
            if (existingUser == null) {
                val defaultUser = User(
                    id = 1L,
                    name = "Default User",
                    email = "user@example.com",
                    createdAt = System.currentTimeMillis()
                )
                database.userDao().upsert(defaultUser)
            }
        }
        
        // 일정 알림 스케줄러 서비스 시작
        com.example.agent_app.service.EventNotificationScheduler.startService(this)

        // SMS 스캔 완료 및 진행 상황 브로드캐스트 수신 등록
        val filter = IntentFilter().apply {
            addAction(com.example.agent_app.service.SmsScanService.ACTION_SCAN_COMPLETE)
            addAction(com.example.agent_app.service.SmsScanService.ACTION_SCAN_PROGRESS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanCompleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(scanCompleteReceiver, filter)
        }

        // 공유된 텍스트 처리 (삼성 녹음 앱에서 STT 텍스트 공유)
        handleSharedText(intent)
        
        // 위젯에서 전달된 탭 정보 처리
        val initialTab = when (intent.getStringExtra("tab")) {
            "calendar" -> AssistantTab.Calendar
            "inbox" -> AssistantTab.Inbox
            "dashboard" -> AssistantTab.Dashboard
            else -> null
        }

        setContent {
            AgentAppTheme {
                AssistantApp(
                    mainViewModel = mainViewModel,
                    chatViewModel = chatViewModel,
                    googleSignInLauncher = googleSignInActivityLauncher,
                    shareCalendarViewModel = shareCalendarViewModel,
                    initialTab = initialTab,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // OAuth 2.0 redirect URI 처리
        if (intent.data?.scheme == "com.example.agent_app" && 
            intent.data?.host == "oauth2callback") {
            android.util.Log.d("MainActivity", "OAuth 2.0 redirect URI 받음: ${intent.data}")
            // ViewModel에서 처리
            mainViewModel.handleGoogleSignInResult(intent)
            return
        }
        
        // 위젯에서 전달된 탭 정보 처리
        val tab = when (intent.getStringExtra("tab")) {
            "calendar" -> AssistantTab.Calendar
            "inbox" -> AssistantTab.Inbox
            "dashboard" -> AssistantTab.Dashboard
            else -> null
        }
        if (tab != null) {
            // 탭 전환은 AssistantApp의 LaunchedEffect에서 처리됨
            // 여기서는 Intent만 업데이트
        }
        
        // 앱이 이미 실행 중일 때 공유 텍스트 받기
        handleSharedText(intent)
    }

    /**
     * 공유된 텍스트 처리 (삼성 녹음 앱에서 STT 텍스트 공유)
     */
    private fun handleSharedText(intent: Intent?) {
        if (intent == null) return
        
        val action = intent.action
        val type = intent.type
        
        // 텍스트 공유 Intent 체크
        if (Intent.ACTION_SEND == action && type != null && type.startsWith("text/plain")) {
            val sharedText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getStringExtra(Intent.EXTRA_TEXT)
            } else {
                @Suppress("DEPRECATION")
                intent.getStringExtra(Intent.EXTRA_TEXT)
            }
            
            if (!sharedText.isNullOrBlank()) {
                Log.d("MainActivity", "공유된 텍스트 받음: ${sharedText.take(100)}...")
                
                // 백그라운드에서 처리 (경로 1: OcrRepositoryWithAi 사용)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val result = appContainer.ocrRepository.processOcrText(
                            ocrText = sharedText,
                            source = "call_record_shared"
                        )
                        
                        if (result.success && result.totalEventCount > 0) {
                            Log.d("MainActivity", "공유된 텍스트 처리 완료: ${result.totalEventCount}개의 일정 생성")
                            
                            // UI 업데이트를 위해 메인 스레드에서 실행
                            CoroutineScope(Dispatchers.Main).launch {
                                // 분류된 데이터 다시 로드
                                mainViewModel.loadClassifiedData()
                            }
                        } else {
                            Log.w("MainActivity", "공유된 텍스트에서 일정을 추출하지 못했습니다.")
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "공유된 텍스트 처리 실패", e)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(scanCompleteReceiver)
        } catch (e: Exception) {
            // Receiver가 등록되지 않았을 수 있음
        }
        appContainer.close()
    }
}

private class MainViewModelFactory(
    private val authRepository: AuthRepository,
    private val ingestRepository: IngestRepository,
    private val gmailRepository: GmailRepositoryWithAi,
    private val classifiedDataRepository: ClassifiedDataRepository,
    private val aiAgent: com.example.agent_app.ai.HuenDongMinAiAgent,
    private val context: android.content.Context,
    private val eventDao: com.example.agent_app.data.dao.EventDao,
    private val ocrRepository: com.example.agent_app.data.repo.OcrRepositoryWithAi,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return MainViewModel(authRepository, ingestRepository, gmailRepository, classifiedDataRepository, aiAgent, context, eventDao, ocrRepository) as T
    }
}

private class ChatViewModelFactory(
    private val executeChatUseCase: com.example.agent_app.domain.chat.usecase.ExecuteChatUseCase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return ChatViewModel(executeChatUseCase) as T
    }
}