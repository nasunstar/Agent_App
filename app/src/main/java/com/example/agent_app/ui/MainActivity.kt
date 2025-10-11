package com.example.agent_app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.agent_app.data.repo.AuthRepository
import com.example.agent_app.data.repo.ClassifiedDataRepository
import com.example.agent_app.data.repo.GmailRepository
import com.example.agent_app.data.repo.IngestRepository
import com.example.agent_app.data.entity.User
import com.example.agent_app.di.AppContainer
import com.example.agent_app.ui.chat.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.agent_app.ui.theme.AgentAppTheme

class MainActivity : ComponentActivity() {

    private val appContainer: AppContainer by lazy { AppContainer(applicationContext) }

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            appContainer.authRepository,
            appContainer.ingestRepository,
            appContainer.gmailRepository,
            appContainer.classifiedDataRepository,
        )
    }

    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(appContainer.executeChatUseCase)
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

        setContent {
            AgentAppTheme {
                AssistantApp(
                    mainViewModel = mainViewModel,
                    chatViewModel = chatViewModel,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appContainer.close()
    }
}

private class MainViewModelFactory(
    private val authRepository: AuthRepository,
    private val ingestRepository: IngestRepository,
    private val gmailRepository: GmailRepository,
    private val classifiedDataRepository: ClassifiedDataRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return MainViewModel(authRepository, ingestRepository, gmailRepository, classifiedDataRepository) as T
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