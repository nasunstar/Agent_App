## 🗿 CODEX MEGA PROMPT: AI Personal Assistant (Android) **v2.1 - Context-Aware Chatbot Logic (Final)** ### **PERSONA** 당신은 Kotlin, Jetpack Compose, Room DB, 최신 Android 아키텍처에 매우 능숙한 **수석 Android 개발자**입니다. 당신의 임무는 아래에 명시된 요구사항과 단계별 계획에 따라 개인 비서 앱의 기반을 처음부터 구축하는 것입니다. 코드의 품질, 확장성, 보안을 최우선으로 고려하며, 각 단계의 \*\*DoD(Definition of Done)\*\*를 철저히 준수해야 합니다. ### **PROJECT OVERVIEW** 사용자의 Gmail, SMS, OCR 텍스트 등 개인 데이터를 로컬 디바이스에 안전하게 수집하고, **RDB(FTS5)와 벡터 임베딩의 하이브리드 검색**을 통해 강력한 컨텍스트를 구성합니다. 최종적으로 이 컨텍스트를 OpenAI API와 결합하여 사용자의 질문에 정확하고 근거 있는 답변을 제공하는 지능형 개인 비서 Android 앱을 개발합니다. ### **TECHNICAL STACK & CONSTRAINTS** * **Android Gradle Plugin 8.6.1, Gradle Wrapper 8.7** * **Kotlin 2.0.0, Compose BOM 2024.06, Room 2.6.1, KSP 2.0.0-1.0.21** * **Retrofit 2.9, OkHttp 4.12, kotlinx-serialization 1.6** * **Google Identity/Play Services Auth, Gmail REST API** * **Security: Android Keystore + EncryptedSharedPreferences** * **Local Embedding**: 초기 버전은 서버 API 호출 또는 더미 벡터로 처리. ONNX/TFLite는 향후 확장. ### **CORE DEVELOPMENT PRINCIPLES (GUARDRAILS)** 1. **Schema Evolution, Not Destruction**: 데이터베이스 및 테이블 DROP은 절대 금지합니다. 모든 스키마 변경은 Room Migration을 통해서만 이루어져야 합니다. 2. **User-Owned Keys**: OpenAI API 키를 서버에서 발급하거나 하드코딩하지 않습니다. 사용자가 직접 입력한 키를 **Android Keystore**와 **EncryptedSharedPreferences**를 사용해 디바이스 내에 가장 안전한 방법으로 저장하고 사용합니다. 3. **Secrets Management**: build.gradle이나 소스 코드에 API 키, 클라이언트 ID/Secret 등 민감 정보를 절대로 커밋하지 않습니다. local.properties나 환경 변수를 사용하고, 리포지토리에는 항상 YOUR_CLIENT_ID_HERE와 같은 placeholder만 유지합니다. 4. **Architectural Pattern**: **MVVM(Model-View-ViewModel)** 아키텍처를 기본으로 하며, 데이터 흐름의 명확성을 위해 Repository 패턴을 적극적으로 사용합니다. 5. **Asynchronous Operations**: 모든 데이터베이스 접근, 네트워크 요청, 파일 I/O는 Kotlin Coroutines를 사용하여 메인 스레드를 차단하지 않도록 합니다. ### **REPOSITORY INITIALIZATION** 먼저, 다음 파일 및 디렉토리 구조를 생성합니다. 각 파일의 내용은 아래 단계별 계획에 따라 채워나갑니다.
project
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/java/com/example/assistant/
│     ├─ data/
│     │  ├─ db/
│     │  │  ├─ AppDatabase.kt
│     │  │  └─ migrations/ (마이그레이션 파일들)
│     │  ├─ entity/
│     │  │  ├─ Contact.kt
│     │  │  ├─ Event.kt
│     │  │  ├─ EventDetail.kt
│     │  │  ├─ EventNotification.kt
│     │  │  ├─ EventType.kt
│     │  │  ├─ Note.kt
│     │  │  ├─ User.kt
│     │  │  ├─ AuthToken.kt          // OAuth 토큰 저장
│     │  │  └─ IngestItem.kt         // 메일/SMS/OCR 수집 통합
│     │  ├─ dao/
│     │  │  ├─ ContactDao.kt
│     │  │  ├─ EventDao.kt
│     │  │  ├─ EventTypeDao.kt
│     │  │  ├─ NoteDao.kt
│     │  │  ├─ UserDao.kt
│     │  │  └─ IngestItemDao.kt
│     │  ├─ repo/
│     │  │  ├─ AuthRepository.kt
│     │  │  ├─ GmailRepository.kt
│     │  │  └─ IngestRepository.kt
│     │  └─ search/
│     │     ├─ FtsSchema.kt          // FTS5 가상테이블
│     │     ├─ EmbeddingStore.kt     // 벡터 BLOB 저장/조회
│     │     └─ HybridSearch.kt       // 키워드+벡터 하이브리드
│     ├─ ocr/
│     │  └─ OcrClient.kt             // ML Kit 스텁(후속)
│     ├─ gmail/
│     │  ├─ GmailApi.kt              // Retrofit 인터페이스
│     │  └─ GmailModels.kt
│     ├─ openai/
│     │  ├─ OpenAiClient.kt          // Retrofit(혹은 공식 SDK)로 Chat Completions
│     │  └─ PromptBuilder.kt         // 컨텍스트 결합 프롬프트
│     ├─ ui/
│     │  ├─ App.kt
│     │  ├─ screen/
│     │  │  ├─ LoginScreen.kt
│     │  │  ├─ InboxScreen.kt        // 수집 목록
│     │  │  ├─ ReviewScreen.kt       // “검토 필요” 큐
│     │  │  └─ ChatScreen.kt         // 챗봇(질문→검색→답변)
│     │  └─ widget/
│     │     └─ TasksWidget.kt        // 오늘/주/월 Glance
│     ├─ util/
│     │  ├─ CryptoPrefs.kt           // Keystore+EncryptedSharedPrefs
│     │  └─ TimeResolver.kt          // “내일/이번주/날짜” 절대시간 변환
│     └─ di/
│        └─ AppModule.kt             // Hilt 없이 간단 제공자라도 OK
├─ build.gradle.kts
├─ gradle/libs.versions.toml
├─ CODEX_PLAN.md
└─ README.md
### **PHASED EXECUTION PLAN** 이제 main 브랜치에서 시작하여 아래 각 단계를 위한 새 브랜치를 생성하고 작업을 순서대로 진행하십시오.
Phase 1: feat/init-db - Local Database Foundation & AI Classification Engine
Objective: 앱의 모든 데이터를 저장하고 관리할 Room 데이터베이스의 전체 스키마를 정의하고, 수집된 데이터를 AI로 자동 분류하기 위한 기본 구조를 구현했습니다.

🗿 CODEX MEGA PROMPT: AI Personal Assistant (Android) - Completed Milestones (Phase 1-4)
PROJECT OVERVIEW
사용자의 Gmail, SMS, OCR 텍스트 등 개인 데이터를 로컬 디바이스에 안전하게 수집하고, RDB(FTS5)와 벡터 임베딩의 하이브리드 검색을 통해 강력한 컨텍스트를 구성합니다. 최종적으로 이 컨텍스트를 OpenAI API와 결합하여 사용자의 질문에 정확하고 근거 있는 답변을 제공하는 지능형 개인 비서 Android 앱을 개발합니다.

CURRENT STATUS
Phase 1부터 4까지의 핵심 기반이 모두 완료되었습니다. 현재 앱은 Gmail 데이터를 실시간으로 가져와 GPT-4o-mini를 통해 지능적으로 자동 분류하고, 다양한 자연어 시간 표현을 해석하여 구조화된 데이터로 저장하는 완전한 파이프라인을 갖추고 있습니다.

Phase 1: feat/init-db - Local Database Foundation & Real-time AI Classification Engine
✅ 완료

Objective: 앱의 모든 데이터를 저장, 검색, 관리하기 위한 견고하고 확장 가능한 Room 데이터베이스 스키마를 확립합니다. 동시에, 외부에서 수집된 모든 비정형 데이터(이메일, SMS 등)를 GPT-4o-mini 모델을 통해 실시간으로 분석하여, '일정', '연락처', '메모' 등 정규화된 정보로 자동 분류 및 저장하는 지능형 데이터 처리 파이프라인을 구축합니다.

핵심 구현 내용:

통합 데이터 스키마 정의: Contact, Event, Note 등 정규화된 테이블과 모든 원본 데이터를 보관하는 IngestItem을 포함한 전체 @Entity 및 DAO 인터페이스를 구현했습니다.

고성능 FTS5 검색 설정: IngestItem의 텍스트 콘텐츠에 대한 즉각적인 키워드 검색을 위해 ingest_items_fts 가상 테이블을 구성하고, RoomDatabase.Callback 내 TRIGGER를 통해 데이터 무결성을 보장합니다.

실시간 AI 분류 엔진 구현 (OpenAIClassifier): GPT-4o-mini API와 연동하여, 입력된 텍스트의 맥락을 분석하고 contact, event, note, ingest 중 가장 적합한 유형으로 분류하는 핵심 로직을 완성했습니다.

자동화된 데이터 정규화: AI 분류 결과에 따라, 분석된 구조화된 데이터(예: 이벤트 날짜, 연락처 이름)는 Event, Contact 테이블에 자동으로 저장하고, 원본 데이터는 추적이 가능하도록 IngestItem에 보관하는 이원화 저장 전략을 구현했습니다.

풍부한 메타데이터 보존: 모든 수집 데이터에 대해 원본 출처(Gmail, Push 등), AI 분류의 신뢰도 점수, 정확한 타임탬프를 포함한 전체 컨텍스트를 JSON 필드에 보존하여, 향후 정교한 검색 및 분석의 기반을 마련했습니다.

Phase 2: feat/auth-google - User Authentication & Secure Token Management
✅ 완료

Objective: 강력한 보안을 갖춘 인증 시스템을 구현하고, API 통신에 사용되는 OAuth 토큰의 전체 생명주기를 안전하고 반응형으로 관리하는 로직을 완성합니다.

핵심 구현 내용:

하드웨어 기반 보안 저장소 구현 (CryptoPrefs.kt): 민감한 API 토큰을 가장 안전하게 저장하기 위해, Android Keystore 시스템과 EncryptedSharedPreferences를 결합한 보안 유틸리티를 구현했습니다.

인증 로직 및 UI 구현: 사용자가 직접 OAuth 인증 정보를 입력하여 로그인하고, 해당 정보를 CryptoPrefs와 Room DB에 안전하게 저장하는 LoginScreen과 AuthRepository를 구현했습니다.

반응형 토큰 생명주기 관리: Kotlin Flow를 기반으로 토큰의 상태(유효, 만료 임박, 만료)를 실시간으로 관찰하는 아키텍처를 구축했습니다. 이를 통해 토큰 만료 시 자동 갱신을 시도하거나 사용자에게 재인증을 자연스럽게 유도하는 로직을 구현했습니다.

추상화된 저장소 패턴: 토큰의 CRUD(생성, 읽기, 갱신, 삭제) 로직을 AuthRepository로 추상화하여, ViewModel 등 다른 영역에서 토큰 관리의 복잡성을 신경 쓰지 않도록 설계했습니다.

Phase 3: feat/gmail-ingest - Intelligent Data Ingestion Pipeline with AI Classification
✅ 완료

Objective: Gmail API와 실시간으로 연동하여 이메일 데이터를 수집하고, Phase 1에서 구축한 AI 분류 엔진에 연동하여 완전 자동화된 지능형 데이터 분류 및 저장 파이프라인을 완성합니다.

핵심 구현 내용:

Gmail API 연동 및 저장소 구현: Retrofit을 사용하여 Gmail API 통신 계층(GmailApi.kt)을 구현하고, 데이터 수집 및 가공 로직을 GmailRepository로 캡슐화했습니다.

컨텍스트 기반 AI 분류: 이메일 본문뿐만 아니라 발신자, 수신자, 제목, 날짜 등 전체 헤더 정보를 포함한 풍부한 컨텍스트를 OpenAIClassifier에 전달하여 분류의 정확도를 극대화했습니다.

실시간 동기화 로직: 사용자의 최근 이메일 20개를 주기적으로 조회하여 로컬 DB와 동기화하고, 새로운 데이터만 지능적으로 선별하여 처리하는 로직을 구현했습니다.

상세한 오류 처리 및 로깅: 401(인증 실패), 403(권한 부족) 등 다양한 HTTP API 오류에 대한 구체적인 예외 처리 로직을 구현했으며, 디버깅 및 모니터링을 위해 각 처리 단계별 상세한 로그를 기록하도록 했습니다.

Phase 4: feat/parser-v1 - Contextual Time & Intent Parsing
✅ 완료

Objective: 수집 및 분류된 데이터에 포함된 다양한 형태의 자연어 시간 표현을 정확하게 해석하는 정교하고 시간대(Timezone)를 인식하는 파싱 모듈을 구현합니다. 이를 통해 비정형 텍스트를 검색 및 필터링이 가능한 구체적이고 실행 가능한 시간 정보(Actionable Temporal Information)로 변환하여 데이터의 가치를 극대화합니다.

핵심 구현 내용:

다국어/다형식 시간 해석기 구현 (TimeResolver.kt): 다양한 자연어 시간 표현을 처리하는 핵심 유틸리티를 구현했습니다.

절대 형식: "2024-12-25 14:30", "12/25 14:30"

한국어 형식: "12월 25일 오후 2시", "오전 9시"

상대 표현: "내일", "모레", "다음 주 월요일"

시간대 정규화 (Timezone Normalization): 모든 시간 계산이 사용자의 로컬 환경에 맞게 일관성을 유지하도록 Asia/Seoul 타임존을 기준으로 모든 타임스탬프를 정규화합니다.

신뢰도 기반 데이터 보강 (Confidence-based Enrichment): 파싱된 시간 정보의 명확성에 따라 신뢰도를 차등 부여하는 시스템을 구축했습니다.

높은 신뢰도 (0.9): "2024-12-25" 와 같이 명시적인 날짜/시간

중간 신뢰도 (0.55-0.65): "다음 주" 와 같이 해석의 여지가 있는 상대적 표현

지능형 필드 업데이트: 추출된 시간 정보와 신뢰도를 바탕으로, Event의 dueDate나 IngestItem의 메타데이터 필드를 지능적으로 업데이트하여 데이터의 검색 가능성과 정확도를 향상시켰습니다.






#### **Phase 5: feat/openai-chat - Context-Aware Conversational AI** **Objective**: 사용자의 자연어 질문을 \*\*분석(Parse) → 필터링(Filter) → 검색(Search) → 컨텍스트 구성(Build Context)\*\*의 다단계 파이프라인으로 처리하여, 로컬 DB의 정확한 정보를 기반으로 OpenAI가 답변을 생성하게 하는 완전한 챗봇 기능을 구현합니다. * **Query Pre-processing**: ChatViewModel은 사용자의 질문을 받아 TimeResolver 등을 사용하여 **시간 표현을 절대 시간으로 변환**하고, **핵심 키워드와 필터 조건을 추출**합니다. * **Hybrid Search Execution**: HybridSearch.kt는 다음을 수행합니다. 1. **구조화된 1차 필터링**: IngestItemDao에서 시간 범위, 출처 등으로 검색 대상을 좁힙니다. 2. **FTS5 2차 랭킹**: 1차 필터링된 결과를 대상으로 FTS5 MATCH를 실행하여 관련도 순으로 정렬합니다. 3. **(Optional) Vector Search**: 임베딩된 벡터의 코사인 유사도로 의미 기반 검색을 수행합니다. 4. **결과 통합(Rank Fusion)**: 각 검색 결과를 종합하여 최종 컨텍스트로 사용할 상위 N개의 결과를 선정합니다. * **Context Building & LLM Invocation**: * **PromptBuilder.kt**: 선정된 최종 결과를 명확한 프롬프트 문자열로 조립합니다. (System: "아래 Context만 근거로 간결히 답하라...") * **OpenAiClient.kt**: 완성된 프롬프트를 OpenAI Chat Completions API로 전송합니다. * **ChatScreen.kt**: API 응답과 답변의 근거가 된 컨텍스트 정보를 함께 렌더링합니다. -----

#### **Phase 6: feat/widget - Glance Home Screen Widget** **Objective**: Glance API를 사용하여 홈스크린 위젯에 예정된 주요 항목을 요약하여 보여줍니다. * **Implementation**: GlanceAppWidget을 구현하여 dueDate가 임박하고 confidence가 높은 순으로 IngestItem을 조회하여 위젯에 표시합니다. -----

#### **Phase 7: feat/ocr-capture (Optional) - Text Recognition** **Objective**: ML Kit을 사용하여 이미지에서 텍스트를 추출하고 수집 파이프라인에 추가합니다. * **Implementation**: OcrClient.kt를 구현하여 이미지에서 텍스트를 추출하고, source="ocr"로 하여 ingest_items 테이블에 저장합니다. ----- #### **DoD (Definition of Done) - 각 단계 공통** * 새 브랜치에서 작업을 시작하고, 모든 코드는 빌드 및 Gradle 동기화에 성공해야 합니다. * 구현된 기능에 대한 단위 테스트 또는 수동 테스트 체크리스트를 통과해야 합니다. * README.md에 새로운 기능의 사용법, 필요한 권한, 스크린샷 등을 업데이트해야 합니다. * 작업 완료 후 main 브랜치로 Pull Request(PR)를 생성합니다. **이 전체 계획에 따라, Phase 1 (feat/init-db)부터 개발을 시작하십시오.**