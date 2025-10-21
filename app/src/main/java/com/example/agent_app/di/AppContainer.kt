package com.example.agent_app.di

import android.content.Context
import com.example.agent_app.ai.HuenDongMinAiAgent
import com.example.agent_app.ai.OpenAIClassifier
import com.example.agent_app.data.chat.HuenDongMinChatGatewayImpl
import com.example.agent_app.data.db.AppDatabase
import com.example.agent_app.data.repo.AuthRepository
import com.example.agent_app.data.repo.ClassifiedDataRepository
import com.example.agent_app.data.repo.GmailRepositoryWithAi
import com.example.agent_app.data.repo.IngestItemParser
import com.example.agent_app.data.repo.IngestRepository
import com.example.agent_app.data.repo.OcrRepositoryWithAi
import com.example.agent_app.data.search.EmbeddingGenerator
import com.example.agent_app.data.search.EmbeddingGeneratorInterface
import com.example.agent_app.data.search.EmbeddingStore
import com.example.agent_app.data.search.HybridSearchEngine
import com.example.agent_app.data.search.OpenAIEmbeddingGenerator
import com.example.agent_app.domain.chat.gateway.ChatGateway
import com.example.agent_app.domain.chat.usecase.ExecuteChatUseCase
import com.example.agent_app.domain.chat.usecase.PromptBuilder
import com.example.agent_app.gmail.GmailServiceFactory
import com.example.agent_app.openai.PromptBuilderImpl

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)

    private val embeddingStore = EmbeddingStore(database.embeddingDao())
    // OpenAI Embeddings API를 사용한 한국어 최적화 임베딩 생성기
    private val embeddingGenerator: EmbeddingGeneratorInterface = OpenAIEmbeddingGenerator()

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
    
    // AI 에이전트 "HuenDongMin" - Gmail/OCR 자동 처리
    private val huenDongMinAiAgent = HuenDongMinAiAgent(
        eventDao = database.eventDao(),
        eventTypeDao = database.eventTypeDao(),
        ingestRepository = ingestRepository,
    )

    // Gmail AI 자동 처리 Repository
    val gmailRepository: GmailRepositoryWithAi = GmailRepositoryWithAi(
        api = GmailServiceFactory.create(),
        huenDongMinAiAgent = huenDongMinAiAgent,
    )
    
    // OCR AI 자동 처리 Repository
    val ocrRepository: OcrRepositoryWithAi = OcrRepositoryWithAi(
        huenDongMinAiAgent = huenDongMinAiAgent,
        eventDao = database.eventDao(),
    )

    private val hybridSearchEngine = HybridSearchEngine(
        ingestItemDao = database.ingestItemDao(),
        eventDao = database.eventDao(),
        embeddingStore = embeddingStore,
        embeddingGenerator = embeddingGenerator,
    )

    private val promptBuilder: PromptBuilder = PromptBuilderImpl()
    
    // AI 에이전트 "HuenDongMin" 기반 ChatGateway (TimeResolver 제거)
    private val chatGateway: ChatGateway = HuenDongMinChatGatewayImpl(
        hybridSearchEngine = hybridSearchEngine,
    )

    val executeChatUseCase: ExecuteChatUseCase = ExecuteChatUseCase(
        chatGateway = chatGateway,
        promptBuilder = promptBuilder,
    )

    fun close() {
        if (database.isOpen) {
            database.close()
        }
    }
}
