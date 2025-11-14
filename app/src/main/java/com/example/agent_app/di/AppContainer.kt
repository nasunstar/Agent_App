package com.example.agent_app.di

import android.content.Context
import com.example.agent_app.BuildConfig
import com.example.agent_app.ai.HuenDongMinAiAgent
import com.example.agent_app.ai.OpenAIClassifier
import com.example.agent_app.data.chat.HuenDongMinChatGatewayImpl
import com.example.agent_app.data.db.AppDatabase
import com.example.agent_app.data.repo.AuthRepository
import com.example.agent_app.data.repo.ClassifiedDataRepository
import com.example.agent_app.data.repo.GmailRepositoryWithAi
import com.example.agent_app.data.repo.IngestRepository
import com.example.agent_app.data.repo.OcrRepositoryWithAi
import com.example.agent_app.data.repo.WidgetRepository
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
import com.example.agent_app.share.data.ShareCalendarRepository
import com.example.agent_app.share.network.ShareCalendarServiceFactory

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)

    private val embeddingStore = EmbeddingStore(database.embeddingDao())
    // OpenAI Embeddings API를 사용한 한국어 최적화 임베딩 생성기
    private val embeddingGenerator: EmbeddingGeneratorInterface = OpenAIEmbeddingGenerator()

    val authRepository: AuthRepository = AuthRepository(database.authTokenDao(), context)
    val ingestRepository: IngestRepository = IngestRepository(
        dao = database.ingestItemDao(),
        embeddingStore = embeddingStore,
        embeddingGenerator = embeddingGenerator,
    )
    
    // EventDao를 외부에서 접근할 수 있도록 제공
    val eventDao = database.eventDao()

    private val openAIClassifier = OpenAIClassifier()

    val classifiedDataRepository: ClassifiedDataRepository = ClassifiedDataRepository(
        openAIClassifier = openAIClassifier,
        contactDao = database.contactDao(),
        eventDao = eventDao,
        eventTypeDao = database.eventTypeDao(),
        noteDao = database.noteDao(),
        ingestRepository = ingestRepository,
    )
    
    // AI 에이전트 "HuenDongMin" - Gmail/OCR/SMS 자동 처리
    val huenDongMinAiAgent = HuenDongMinAiAgent(
        context = context,
        eventDao = eventDao,
        eventTypeDao = database.eventTypeDao(),
        ingestRepository = ingestRepository,
    )

    // Gmail AI 자동 처리 Repository
    val gmailRepository: GmailRepositoryWithAi = GmailRepositoryWithAi(
        api = GmailServiceFactory.create(),
        huenDongMinAiAgent = huenDongMinAiAgent,
        ingestRepository = ingestRepository,
    )
    
    // OCR AI 자동 처리 Repository
    val ocrRepository: OcrRepositoryWithAi = OcrRepositoryWithAi(
        huenDongMinAiAgent = huenDongMinAiAgent,
        eventDao = eventDao,
    )

    private val shareCalendarApi = ShareCalendarServiceFactory.create(
        baseUrl = BuildConfig.BACKEND_BASE_URL,
        enableLogging = false,
    )

    val shareCalendarRepository: ShareCalendarRepository = ShareCalendarRepository(
        api = shareCalendarApi,
    )

    private val hybridSearchEngine = HybridSearchEngine(
        ingestItemDao = database.ingestItemDao(),
        eventDao = eventDao,
        embeddingStore = embeddingStore,
        embeddingGenerator = embeddingGenerator,
    )

    private val promptBuilder: PromptBuilder = PromptBuilderImpl()
    
    // AI 에이전트 "HuenDongMin" 기반 ChatGateway (TimeResolver 제거)
    private val chatGateway: ChatGateway = HuenDongMinChatGatewayImpl(
        hybridSearchEngine = hybridSearchEngine,
        eventDao = eventDao,
        huenDongMinAiAgent = huenDongMinAiAgent,
    )

    val executeChatUseCase: ExecuteChatUseCase = ExecuteChatUseCase(
        chatGateway = chatGateway,
        promptBuilder = promptBuilder,
    )
    
    // 위젯용 Repository
    val widgetRepository: WidgetRepository = WidgetRepository(
        eventDao = eventDao,
        ingestItemDao = database.ingestItemDao(),
    )

    fun close() {
        if (database.isOpen) {
            database.close()
        }
    }
}
