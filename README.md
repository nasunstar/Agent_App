# AI Personal Assistant (Android)

## Configuration

Before running the app configure the following entries in `local.properties`:

- `GOOGLE_WEB_CLIENT_ID`: optional Web client ID used when requesting a server auth code from Google Sign-In. Leave as the default placeholder if you only need on-device token retrieval.
- `OPENAI_API_KEY`: stored locally and exposed to the app via `BuildConfig.OPENAI_API_KEY` for future OpenAI integrations.

## 프로젝트 구조

### 루트 디렉토리

```
Agent_App/
├── app/                          # Android 앱 모듈
├── backend/                      # 백엔드 서버 모듈 (Ktor 기반)
├── gradle/                       # Gradle 설정 파일
├── build.gradle.kts              # 프로젝트 레벨 빌드 설정
├── settings.gradle.kts           # 프로젝트 모듈 설정
├── gradle.properties             # Gradle 속성 설정
├── local.properties              # 로컬 개발 환경 설정 (API 키 등)
├── README.md                     # 프로젝트 메인 문서
├── CODEX_PLAN.txt                # 개발 단계별 계획서
├── MEGA_PROMPT.md                # 프로젝트 초기 설계 문서
├── DEPLOYMENT_GUIDE.md           # 배포 가이드
├── SECURITY_GUIDE.md             # 보안 가이드
├── BACKEND_CONFIG_GUIDE.md       # 백엔드 설정 가이드
├── ADMIN_ACCOUNTS_GUIDE.md       # 관리자 계정 가이드
├── WIDGET_DEBUG_GUIDE.md         # 위젯 디버깅 가이드
└── WIDGET_ISSUE_SUMMARY.md       # 위젯 이슈 요약
```

### app/ 디렉토리 (Android 앱)

```
app/
├── build.gradle.kts              # 앱 모듈 빌드 설정 및 의존성
├── proguard-rules.pro            # ProGuard 난독화 규칙
├── schemas/                      # Room 데이터베이스 스키마 버전 히스토리
│   └── com.example.agent_app.data.db.AppDatabase/
│       └── [1-7].json            # 각 DB 버전별 스키마 정의
└── src/
    ├── main/
    │   ├── AndroidManifest.xml   # 앱 매니페스트 (권한, 컴포넌트 등)
    │   ├── java/com/example/agent_app/
    │   │   ├── ai/               # AI 에이전트 관련
    │   │   │   ├── HuenDongMinAiAgent.kt        # AI 에이전트 메인 로직
    │   │   │   ├── HuenDongMinAiTools.kt         # AI 도구 함수들
    │   │   │   ├── OpenAIClassifier.kt           # OpenAI 기반 분류기
    │   │   │   └── FewShotExampleLoader.kt       # Few-shot 예제 로더
    │   │   ├── auth/             # 인증 관련
    │   │   │   ├── GoogleSignInHelper.kt         # Google 로그인 헬퍼
    │   │   │   ├── GoogleOAuth2Flow.kt           # OAuth2 플로우 처리
    │   │   │   ├── GoogleAuthTokenProvider.kt    # Google 토큰 제공자
    │   │   │   └── GoogleTokenRefresher.kt       # 토큰 갱신 처리
    │   │   ├── data/             # 데이터 레이어
    │   │   │   ├── db/                           # Room 데이터베이스
    │   │   │   │   ├── AppDatabase.kt            # 데이터베이스 인스턴스
    │   │   │   │   ├── migrations/               # DB 마이그레이션
    │   │   │   │   │   └── DatabaseMigrations.kt # 마이그레이션 로직
    │   │   │   │   └── converters/               # 타입 컨버터
    │   │   │   │       └── Converters.kt        # JSON, 날짜 등 변환
    │   │   │   ├── entity/                       # Room 엔티티
    │   │   │   │   ├── User.kt                  # 사용자 정보
    │   │   │   │   ├── Note.kt                  # 메모
    │   │   │   │   ├── Contact.kt               # 연락처
    │   │   │   │   ├── Event.kt                 # 일정
    │   │   │   │   ├── EventType.kt             # 일정 유형
    │   │   │   │   ├── EventDetail.kt           # 일정 상세
    │   │   │   │   ├── EventNotification.kt     # 일정 알림
    │   │   │   │   ├── AuthToken.kt             # 인증 토큰
    │   │   │   │   ├── IngestItem.kt            # 수집된 아이템
    │   │   │   │   ├── IngestItemFts.kt         # FTS 검색용 엔티티
    │   │   │   │   └── IngestItemEmbedding.kt   # 벡터 임베딩
    │   │   │   ├── dao/                         # Data Access Objects
    │   │   │   │   ├── UserDao.kt               # 사용자 쿼리
    │   │   │   │   ├── NoteDao.kt               # 메모 쿼리
    │   │   │   │   ├── ContactDao.kt            # 연락처 쿼리
    │   │   │   │   ├── EventDao.kt              # 일정 쿼리
    │   │   │   │   ├── EventTypeDao.kt          # 일정 유형 쿼리
    │   │   │   │   ├── IngestItemDao.kt         # 수집 아이템 쿼리
    │   │   │   │   ├── AuthTokenDao.kt          # 토큰 쿼리
    │   │   │   │   ├── EmbeddingDao.kt          # 임베딩 쿼리
    │   │   │   │   └── PushNotificationDao.kt   # 푸시 알림 쿼리
    │   │   │   ├── repo/                        # Repository 패턴
    │   │   │   │   ├── AuthRepository.kt        # 인증 저장소
    │   │   │   │   ├── GmailRepositoryWithAi.kt # Gmail 수집 (AI 분류 포함)
    │   │   │   │   ├── IngestRepository.kt     # 수집 데이터 저장소
    │   │   │   │   ├── ClassifiedDataRepository.kt # 분류된 데이터 저장소
    │   │   │   │   ├── GmailSyncManager.kt      # Gmail 동기화 관리자
    │   │   │   │   ├── OcrRepositoryWithAi.kt  # OCR 수집 (AI 분류 포함)
    │   │   │   │   └── WidgetRepository.kt      # 위젯 데이터 저장소
    │   │   │   ├── search/                      # 검색 엔진
    │   │   │   │   ├── HybridSearchEngine.kt    # 하이브리드 검색 (FTS + 벡터)
    │   │   │   │   ├── EmbeddingStore.kt        # 임베딩 저장소
    │   │   │   │   ├── EmbeddingGenerator.kt    # 임베딩 생성 인터페이스
    │   │   │   │   └── OpenAIEmbeddingGenerator.kt # OpenAI 임베딩 생성
    │   │   │   └── chat/                        # 챗봇 데이터
    │   │   │       └── HuenDongMinChatGatewayImpl.kt # 챗봇 게이트웨이 구현
    │   │   ├── domain/           # 도메인 레이어 (비즈니스 로직)
    │   │   │   └── chat/                        # 챗봇 도메인
    │   │   │       ├── gateway/                  # 게이트웨이 인터페이스
    │   │   │       │   └── ChatGateway.kt       # 챗봇 게이트웨이
    │   │   │       ├── model/                   # 도메인 모델
    │   │   │       │   ├── ChatMessage.kt       # 챗 메시지
    │   │   │       │   ├── ChatResult.kt        # 챗 결과
    │   │   │       │   ├── ChatContextItem.kt   # 컨텍스트 아이템
    │   │   │       │   └── QueryFilters.kt      # 쿼리 필터
    │   │   │       └── usecase/                 # Use Case
    │   │   │           └── ExecuteChatUseCase.kt # 챗 실행 유스케이스
    │   │   ├── ui/               # UI 레이어 (Jetpack Compose)
    │   │   │   ├── MainActivity.kt              # 메인 액티비티
    │   │   │   ├── MainScreen.kt                # 메인 화면
    │   │   │   ├── MainViewModel.kt             # 메인 뷰모델
    │   │   │   ├── DashboardScreen.kt           # 대시보드 화면
    │   │   │   ├── DeveloperMenuScreen.kt       # 개발자 메뉴
    │   │   │   ├── SidebarMenu.kt               # 사이드바 메뉴
    │   │   │   ├── chat/                        # 챗봇 UI
    │   │   │   │   ├── ChatScreen.kt            # 챗봇 화면
    │   │   │   │   └── ChatViewModel.kt         # 챗봇 뷰모델
    │   │   │   ├── auth/                        # 인증 UI
    │   │   │   ├── sync/                        # 동기화 UI
    │   │   │   └── theme/                       # 테마 설정
    │   │   │       ├── Color.kt                 # 색상 정의
    │   │   │       ├── Theme.kt                 # 테마 정의
    │   │   │       └── Type.kt                  # 타이포그래피
    │   │   ├── gmail/            # Gmail API 통합
    │   │   │   ├── GmailApi.kt                  # Gmail API 인터페이스
    │   │   │   ├── GmailModels.kt               # Gmail 데이터 모델
    │   │   │   ├── GmailServiceFactory.kt        # Gmail 서비스 팩토리
    │   │   │   └── GmailBodyExtractor.kt         # Gmail 본문 추출
    │   │   ├── ocr/              # OCR 기능
    │   │   │   ├── OcrCaptureActivity.kt        # OCR 캡처 액티비티
    │   │   │   └── OcrClient.kt                 # OCR 클라이언트
    │   │   ├── openai/           # OpenAI 통합
    │   │   │   └── PromptBuilderImpl.kt         # 프롬프트 빌더 구현
    │   │   ├── service/          # 백그라운드 서비스
    │   │   │   ├── GmailSyncService.kt          # Gmail 동기화 서비스
    │   │   │   ├── SmsScanService.kt            # SMS 스캔 서비스
    │   │   │   ├── SmsAutoProcessReceiver.kt    # SMS 자동 처리 리시버
    │   │   │   ├── EventNotificationService.kt  # 일정 알림 서비스
    │   │   │   ├── EventNotificationScheduler.kt # 일정 알림 스케줄러
    │   │   │   ├── PushNotificationListenerService.kt # 푸시 알림 수신 서비스
    │   │   │   └── CallRecordTextProcessor.kt    # 통화 녹음 텍스트 처리
    │   │   ├── widget/           # 위젯
    │   │   │   └── SummaryWidget.kt             # 요약 위젯 (Glance)
    │   │   ├── util/             # 유틸리티
    │   │   │   ├── TimeFormatter.kt             # 시간 포맷터
    │   │   │   ├── TokenEncryption.kt           # 토큰 암호화
    │   │   │   ├── SmsReader.kt                 # SMS 읽기
    │   │   │   ├── CallRecordTextReader.kt      # 통화 녹음 텍스트 읽기
    │   │   │   ├── JsonCleaner.kt               # JSON 정리
    │   │   │   └── TestUserManager.kt           # 테스트 사용자 관리
    │   │   └── di/               # 의존성 주입
    │   │       └── AppContainer.kt              # 앱 컨테이너 (수동 DI)
    │   ├── test/                 # 단위 테스트
    │   │   └── java/
    │   └── androidTest/          # Android 통합 테스트
    │       └── java/
    └── res/                      # 리소스 파일
        ├── layout/               # 레이아웃 XML
        ├── values/               # 문자열, 색상 등
        ├── xml/                   # XML 설정 파일
        ├── drawable/              # 드로어블 리소스
        ├── mipmap/                # 앱 아이콘
        └── raw/                   # 원시 리소스 (JSON 등)
```

### backend/ 디렉토리 (Ktor 서버)

```
backend/
├── build.gradle.kts              # 백엔드 빌드 설정
├── Dockerfile                    # Docker 이미지 빌드 파일
├── railway.json                  # Railway 배포 설정
├── run-server.bat                # 서버 실행 스크립트 (Windows)
├── README.md                     # 백엔드 README
├── README_SERVER.md              # 서버 상세 문서
└── src/main/
    ├── kotlin/com/example/agent_app/backend/
    │   ├── Application.kt        # Ktor 애플리케이션 진입점
    │   ├── config/               # 설정 관리
    │   │   └── ConfigLoader.kt   # 설정 로더
    │   ├── data/                 # 데이터 모델
    │   │   ├── AdminAccountsTable.kt      # 관리자 계정 테이블
    │   │   └── ManagedGoogleAccountTable.kt # 관리되는 Google 계정 테이블
    │   ├── models/               # 데이터 모델
    │   │   ├── AdminAccount.kt   # 관리자 계정 모델
    │   │   ├── UserInfo.kt      # 사용자 정보 모델
    │   │   ├── TokenResponse.kt  # 토큰 응답 모델
    │   │   ├── EmailCheckResult.kt # 이메일 확인 결과
    │   │   └── InstantSerializer.kt # Instant 직렬화
    │   ├── plugins/              # Ktor 플러그인
    │   │   ├── Database.kt       # 데이터베이스 플러그인
    │   │   ├── Security.kt       # 보안 플러그인
    │   │   ├── Serialization.kt  # 직렬화 플러그인
    │   │   ├── Routing.kt        # 라우팅 플러그인
    │   │   ├── RateLimiting.kt   # Rate Limiting 플러그인
    │   │   └── Monitoring.kt     # 모니터링 플러그인
    │   ├── routes/               # API 라우트
    │   │   ├── AdminAuthRoutes.kt    # 관리자 인증 라우트
    │   │   ├── AdminAccountRoutes.kt # 관리자 계정 라우트
    │   │   └── AdminApiRoutes.kt     # 관리자 API 라우트
    │   ├── repositories/         # Repository
    │   │   └── ManagedAccountRepository.kt # 관리 계정 저장소
    │   ├── services/             # 비즈니스 로직
    │   │   ├── AdminAccountService.kt  # 관리자 계정 서비스
    │   │   └── GoogleOAuthService.kt   # Google OAuth 서비스
    │   └── utils/                # 유틸리티
    │       ├── CryptoUtils.kt    # 암호화 유틸
    │       └── GoogleAuthUtils.kt # Google 인증 유틸
    └── resources/                # 리소스 파일
        ├── application.conf      # Ktor 설정 파일
        └── logback.xml           # 로깅 설정
```

### 주요 폴더/파일 역할 설명

#### 루트 레벨 파일

- **build.gradle.kts**: 프로젝트 전체 빌드 설정, 공통 의존성 관리
- **settings.gradle.kts**: 프로젝트에 포함된 모듈 정의 (app, backend)
- **gradle.properties**: Gradle 빌드 속성 (메모리 설정 등)
- **local.properties**: 로컬 개발 환경 설정 (API 키, SDK 경로 등, Git에 커밋되지 않음)
- **CODEX_PLAN.txt**: 개발 단계별 계획 및 가드레일 정의
- **MEGA_PROMPT.md**: 프로젝트 초기 설계 및 아키텍처 문서

#### app/ 모듈

**데이터 레이어 (data/)**
- **db/**: Room 데이터베이스 설정, 마이그레이션, 타입 컨버터
- **entity/**: 데이터베이스 테이블 엔티티 정의
- **dao/**: 데이터베이스 쿼리 인터페이스
- **repo/**: 데이터 저장소 구현 (Gmail, OCR, 인증 등)
- **search/**: 하이브리드 검색 엔진 (FTS + 벡터 임베딩)

**도메인 레이어 (domain/)**
- 비즈니스 로직과 Use Case 정의
- UI와 데이터 레이어를 분리하는 인터페이스

**UI 레이어 (ui/)**
- Jetpack Compose 기반 화면 구성
- ViewModel을 통한 상태 관리

**서비스 레이어 (service/)**
- 백그라운드 작업 (Gmail 동기화, SMS 스캔, 알림 등)

**기타 모듈**
- **gmail/**: Gmail API 통합
- **ocr/**: OCR 기능
- **openai/**: OpenAI API 통합
- **auth/**: Google 인증 처리
- **widget/**: 홈 화면 위젯
- **util/**: 공통 유틸리티 함수

#### backend/ 모듈

- **Ktor** 기반 REST API 서버
- 관리자 계정 관리 및 Google OAuth 토큰 교환
- H2 데이터베이스 사용
- Railway 등 클라우드 플랫폼 배포 지원

## Local Database Schema

```
users                 notes                    contacts
-----                 -----                    --------
id (PK)               id (PK)                  id (PK)
name                  user_id (FK→users.id)    name
email                 title                    email
created_at            body                     phone
                      created_at               meta_json
                      updated_at

event_types           events                   event_details
-----------           ------                   -------------
id (PK)               id (PK)                  id (PK)
type_name (unique)    user_id (FK)             event_id (FK→events.id)
                      type_id (FK→event_types) description
                      title
                      start_at
                      end_at
                      location
                      status

 event_notifications          auth_tokens             ingest_items
 --------------------         -----------             ------------
 id (PK)                      provider (PK)           id (PK)
 event_id (FK→events.id)      access_token            source
 notify_at                    refresh_token           type
 channel                      scope                   title
                              expires_at              body
                                                       timestamp
                                                       due_date
                                                       confidence
                                                       meta_json
```

- `ingest_items` includes indices on `timestamp` and `due_date` to support time based queries.
- `ingest_items_fts` mirrors `ingest_items` via Room FTS4 to enable MATCH-based keyword queries, and `ingest_item_embeddings` persists hashed float vectors for semantic search.
- Database version 2 ships with a migration that back-fills the FTS table and provisions the embedding store without dropping existing data.

## Gmail Ingestion (Phase 3)

The home screen now includes a Google Sign-In button that requests the `gmail.readonly` scope and fetches an access token directly on device using Play Services. When the sign-in flow completes successfully the token is persisted through `AuthRepository` and can be reused for Gmail synchronization. Manual token entry fields remain available as a fallback for development.

To sync messages:

1. Tap **Google 계정으로 로그인** inside the **Google 로그인 설정** card and complete the consent dialog. The access token is stored automatically.
2. (Optional) Provide a manually issued access token/refresh token and press **토큰 저장** if you need to test with external credentials.
3. Press **최근 20개 동기화** within the **Gmail 수집함** card. The Gmail REST API retrieves the newest 20 messages and upserts them into `ingest_items` using the Gmail message ID as the primary key.
4. The Compose list renders the subject, snippet, received timestamp, parsed due date (if present), and confidence score. 401 responses trigger a snackbar that prompts the user to reauthenticate.


## Natural Language Parsing (Phase 4)

Phase 4 introduces a lightweight intent and date parser that enriches each `ingest_items` record:

- `TimeResolver` converts phrases such as "내일 오전 10시", "다음 주 금요일", `9/30 14:00`, and Korean date formats into Asia/Seoul epoch milliseconds.
- `IngestItemParser` combines detected timestamps with intent keywords (회의, 마감, deadline, 등) to populate `dueDate` and a heuristic `confidence` score before persisting to Room.
- The Gmail list renders the derived due date and confidence so upcoming action items stand out immediately after ingestion.

The parser intentionally favours deterministic rules so it can run offline; it can be extended with additional patterns as new data sources are introduced.

## Context-Aware Chat (Phase 5)

The bottom navigation now exposes a dedicated **챗봇** screen built with a clean architecture stack (domain use cases + data gateway + presentation ViewModel):

- `ProcessUserQueryUseCase` parses each user question to extract time windows, candidate keywords, and optional source filters using `TimeResolver`.
- `HybridSearchEngine` executes a three-stage ranking pipeline (structured filters → FTS MATCH → vector cosine similarity) over `ingest_items`, backed by the new FTS and embedding tables.
- `PromptBuilderImpl` assembles a concise system instruction plus a formatted context block so OpenAI completions stay grounded.
- `ExecuteChatUseCase` orchestrates the pipeline through `ChatGatewayImpl` and returns a `ChatResult` consumed by `ChatViewModel` and `ChatScreen`.

Each chat response highlights the context snippets (with relevance scores) that informed the answer so users always understand the provenance of a reply. When no OpenAI API key is configured the assistant still surfaces the ranked context and returns a fallback response.

## Testing

Run the full unit test suite (includes Gmail repository coverage):

```bash
./gradlew test
```

> **Note:** The test task requires an Android SDK (`sdk.dir` or `ANDROID_HOME`). In CI-less local runs configure the SDK path before executing the command.
