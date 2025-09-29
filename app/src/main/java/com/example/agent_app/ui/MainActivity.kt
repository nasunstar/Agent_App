package com.example.agent_app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.agent_app.data.db.AppDatabase
import com.example.agent_app.data.repo.AuthRepository
import com.example.agent_app.data.repo.GmailRepository
import com.example.agent_app.data.repo.IngestRepository
import com.example.agent_app.data.repo.ClassifiedDataRepository
import com.example.agent_app.ai.OpenAIClassifier
import com.example.agent_app.data.entity.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.agent_app.gmail.GmailServiceFactory
import com.example.agent_app.ui.theme.AgentAppTheme

class MainActivity : ComponentActivity() {

    private val database: AppDatabase by lazy { AppDatabase.build(applicationContext) }
    private val authRepository: AuthRepository by lazy { AuthRepository(database.authTokenDao()) }
    private val ingestRepository: IngestRepository by lazy { IngestRepository(database.ingestItemDao()) }
    private val classifiedDataRepository: ClassifiedDataRepository by lazy {
        val openAIClassifier = OpenAIClassifier()
        ClassifiedDataRepository(
            openAIClassifier = openAIClassifier,
            contactDao = database.contactDao(),
            eventDao = database.eventDao(),
            eventTypeDao = database.eventTypeDao(),
            noteDao = database.noteDao(),
            ingestItemDao = database.ingestItemDao()
        )
    }
    
    private val gmailRepository: GmailRepository by lazy {
        GmailRepository(
            api = GmailServiceFactory.create(),
            ingestRepository = ingestRepository,
            classifiedDataRepository = classifiedDataRepository,
        )
    }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(authRepository, ingestRepository, gmailRepository, classifiedDataRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
                AssistantApp(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (database.isOpen) {
            database.close()
        }
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