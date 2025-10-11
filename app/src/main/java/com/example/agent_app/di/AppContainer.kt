package com.example.agent_app.di

import android.content.Context
import com.example.agent_app.ai.OpenAIClassifier
import com.example.agent_app.data.chat.ChatGatewayImpl
import com.example.agent_app.data.db.AppDatabase
import com.example.agent_app.data.repo.AuthRepository
import com.example.agent_app.data.repo.ClassifiedDataRepository
import com.example.agent_app.data.repo.GmailRepository
import com.example.agent_app.data.repo.IngestItemParser
import com.example.agent_app.data.repo.IngestRepository
import com.example.agent_app.data.search.EmbeddingGenerator
import com.example.agent_app.data.search.EmbeddingStore
import com.example.agent_app.data.search.HybridSearchEngine
import com.example.agent_app.domain.chat.gateway.ChatGateway
import com.example.agent_app.domain.chat.usecase.ExecuteChatUseCase
import com.example.agent_app.domain.chat.usecase.ProcessUserQueryUseCase
import com.example.agent_app.domain.chat.usecase.PromptBuilder
import com.example.agent_app.gmail.GmailServiceFactory
import com.example.agent_app.openai.OpenAiClient
import com.example.agent_app.openai.PromptBuilderImpl

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)

    private val embeddingStore = EmbeddingStore(database.embeddingDao())
    private val embeddingGenerator = EmbeddingGenerator()

    val authRepository: AuthRepository = AuthRepository(database.authTokenDao())
    val ingestRepository: IngestRepository = IngestRepository(
        dao = database.ingestItemDao(),
        parser = IngestItemParser(),
        embeddingStore = embeddingStore,
        embeddingGenerator = embeddingGenerator,
    )

    private val openAIClassifier = OpenAIClassifier()

    val classifiedDataRepository: ClassifiedDataRepository = ClassifiedDataRepository(
        openAIClassifier = openAIClassifier,
        contactDao = database.contactDao(),
        eventDao = database.eventDao(),
        eventTypeDao = database.eventTypeDao(),
        noteDao = database.noteDao(),
        ingestRepository = ingestRepository,
    )

    val gmailRepository: GmailRepository = GmailRepository(
        api = GmailServiceFactory.create(),
        ingestRepository = ingestRepository,
        classifiedDataRepository = classifiedDataRepository,
    )

    private val hybridSearchEngine = HybridSearchEngine(
        ingestItemDao = database.ingestItemDao(),
        embeddingStore = embeddingStore,
        embeddingGenerator = embeddingGenerator,
    )

    private val promptBuilder: PromptBuilder = PromptBuilderImpl()
    private val chatGateway: ChatGateway = ChatGatewayImpl(
        hybridSearchEngine = hybridSearchEngine,
        openAiClient = OpenAiClient(),
    )

    private val processUserQueryUseCase = ProcessUserQueryUseCase()

    val executeChatUseCase: ExecuteChatUseCase = ExecuteChatUseCase(
        processUserQueryUseCase = processUserQueryUseCase,
        chatGateway = chatGateway,
        promptBuilder = promptBuilder,
    )

    fun close() {
        if (database.isOpen) {
            database.close()
        }
    }
}
