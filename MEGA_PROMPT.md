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
### **PHASED EXECUTION PLAN** 이제 main 브랜치에서 시작하여 아래 각 단계를 위한 새 브랜치를 생성하고 작업을 순서대로 진행하십시오. #### **Phase 1: feat/init-db - Local Database Foundation** **Objective**: Room 데이터베이스의 모든 기본 구성요소(Entity, DAO, Database 클래스, FTS5 가상 테이블)를 정의하고, 기본적인 CRUD 및 검색 기능이 동작함을 단위 테스트로 증명합니다. * **DB Schema (Version 1)**: * **contacts**: id, name, email, phone, metaJson? * **users**: id, name, email, createdAt * **notes**: id, userId, title, body, createdAt, updatedAt * **event_types**: id, typeName (unique) * **events**: id, userId, typeId, title, startAt, endAt, location?, status? * **event_details**: id, eventId, description * **event_notifications**: id, eventId, notifyAt, channel * **auth_tokens**: provider (PK), accessToken, refreshToken?, scope, expiresAt * **ingest_items**: id (PK), source, type, title?, body, timestamp, dueDate?, confidence?, metaJson? * **Indices**: ingest_items(timestamp), ingest_items(dueDate) * **FTS5 Virtual Table**: * ingest_items_fts(title, body, content=ingest_items, content_rowid=id)를 FtsSchema.kt에 정의. * **Implementation**: * 위 스키마에 따라 모든 @Entity 클래스와 DAO 인터페이스를 구현. * AppDatabase.kt에 모든 구성요소를 포함하고, FTS5 동기화를 위한 TRIGGER를 RoomDatabase.Callback으로 생성. * **DoD**: 단위 테스트(CRUD, FTS5 smoke test) 작성, README.md에 스키마 다이어그램 추가. ----- 
#### **Phase 2: feat/auth-google - User Authentication** **Objective**: Google Sign-In 기능을 구현하고, 획득한 토큰을 Android Keystore를 통해 안전하게 암호화하여 저장 및 관리하는 로직을 완성합니다. * **Logic**: Google Sign-In 성공 후 받은 serverAuthCode를 백엔드(placeholder)로 보내 access_token 등을 받아옵니다. * **Storage**: CryptoPrefs.kt를 구현하여 실제 토큰 값은 EncryptedSharedPreferences에, 만료 시각 등 메타데이터는 auth_tokens Room 테이블에 저장합니다. * **UI**: LoginScreen.kt에 로그인 버튼을 만들고 성공/실패 UI 피드백을 구현합니다. ----- 
#### **Phase 3: feat/gmail-ingest - Data Collection** **Objective**: 인증된 사용자의 Gmail 메시지를 Gmail REST API를 통해 가져와 ingest_items 테이블에 저장하는 기능을 구현합니다. * **API**: gmail.readonly 스코프로 Gmail API를 사용. Retrofit과 kotlinx-serialization으로 GmailApi.kt 구현. * **Ingestion**: 최근 메시지 20개를 조회하여 subject, snippet, 수신 시각을 IngestItem으로 변환 후 upsert합니다. (ID는 Gmail 메시지 ID 사용) * **Error Handling**: 401 Unauthorized 에러 발생 시 토큰 만료로 간주하고 재로그인을 유도합니다.이를이용해서  MainActivityApp을 구현하여 앱을 동작시키게 하고. 이때 로그인 기능이 구현되도록 UI를 구성하십시오 ----- 
#### **Phase 4: feat/parser-v1 - Simple Intent & Date Parsing** **Objective**: 수집된 텍스트에서 간단한 규칙 기반으로 날짜, 시간, 의도를 추출하여 ingest_items 레코드를 보강합니다. * **TimeResolver.kt**: "내일", "다음 주", "9/30 14:00" 등 텍스트를 Asia/Seoul 기준의 절대 시간(epoch ms)으로 변환합니다. * **Logic**: IngestItem을 저장할 때, "회의/마감" 등의 키워드와 날짜 텍스트를 분석하여 dueDate, confidence 필드를 업데이트합니다. -----
#### **Phase 5: feat/widget - Glance Home Screen Widget** **Objective**: Glance API를 사용하여 홈스크린 위젯에 예정된 주요 항목을 요약하여 보여줍니다. * **Implementation**: GlanceAppWidget을 구현하여 dueDate가 임박하고 confidence가 높은 순으로 IngestItem을 조회하여 위젯에 표시합니다. ----- 
#### **Phase 6: feat/openai-chat - Context-Aware Conversational AI** **Objective**: 사용자의 자연어 질문을 \*\*분석(Parse) → 필터링(Filter) → 검색(Search) → 컨텍스트 구성(Build Context)\*\*의 다단계 파이프라인으로 처리하여, 로컬 DB의 정확한 정보를 기반으로 OpenAI가 답변을 생성하게 하는 완전한 챗봇 기능을 구현합니다. * **Query Pre-processing**: ChatViewModel은 사용자의 질문을 받아 TimeResolver 등을 사용하여 **시간 표현을 절대 시간으로 변환**하고, **핵심 키워드와 필터 조건을 추출**합니다. * **Hybrid Search Execution**: HybridSearch.kt는 다음을 수행합니다. 1. **구조화된 1차 필터링**: IngestItemDao에서 시간 범위, 출처 등으로 검색 대상을 좁힙니다. 2. **FTS5 2차 랭킹**: 1차 필터링된 결과를 대상으로 FTS5 MATCH를 실행하여 관련도 순으로 정렬합니다. 3. **(Optional) Vector Search**: 임베딩된 벡터의 코사인 유사도로 의미 기반 검색을 수행합니다. 4. **결과 통합(Rank Fusion)**: 각 검색 결과를 종합하여 최종 컨텍스트로 사용할 상위 N개의 결과를 선정합니다. * **Context Building & LLM Invocation**: * **PromptBuilder.kt**: 선정된 최종 결과를 명확한 프롬프트 문자열로 조립합니다. (System: "아래 Context만 근거로 간결히 답하라...") * **OpenAiClient.kt**: 완성된 프롬프트를 OpenAI Chat Completions API로 전송합니다. * **ChatScreen.kt**: API 응답과 답변의 근거가 된 컨텍스트 정보를 함께 렌더링합니다. ----- 
#### **Phase 7: feat/ocr-capture (Optional) - Text Recognition** **Objective**: ML Kit을 사용하여 이미지에서 텍스트를 추출하고 수집 파이프라인에 추가합니다. * **Implementation**: OcrClient.kt를 구현하여 이미지에서 텍스트를 추출하고, source="ocr"로 하여 ingest_items 테이블에 저장합니다. ----- #### **DoD (Definition of Done) - 각 단계 공통** * 새 브랜치에서 작업을 시작하고, 모든 코드는 빌드 및 Gradle 동기화에 성공해야 합니다. * 구현된 기능에 대한 단위 테스트 또는 수동 테스트 체크리스트를 통과해야 합니다. * README.md에 새로운 기능의 사용법, 필요한 권한, 스크린샷 등을 업데이트해야 합니다. * 작업 완료 후 main 브랜치로 Pull Request(PR)를 생성합니다. **이 전체 계획에 따라, Phase 1 (feat/init-db)부터 개발을 시작하십시오.**