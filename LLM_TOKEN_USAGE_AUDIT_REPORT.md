# LLM Token Usage Audit 보고서

**프로젝트**: MOA (Android 개인 비서 앱)  
**분석 일시**: 2025년  
**분석 범위**: 전체 Android 프로젝트의 LLM 호출 지점 전수 조사

---

## 📊 Executive Summary

### 전체 LLM 호출 함수 개수
- **총 8개의 LLM 호출 함수** 발견
- **자동 호출 경로**: 4개 (Push, SMS, Gmail, OCR)
- **수동 호출 경로**: 2개 (Chat, 검색 필터)
- **분류 전용**: 2개 (이메일 분류, 푸시 알림 분류)

### 주요 발견 사항
1. ⚠️ **Push 알림 수신 시마다 자동 LLM 호출** (필터링 있으나 불완전)
2. ⚠️ **SMS 수신 시마다 자동 LLM 호출** (기간 제한 있음)
3. ⚠️ **Gmail 동기화 시 모든 메시지에 LLM 호출** (중복 체크 있음)
4. ⚠️ **일정이 2개 이상이면 2단계 처리로 LLM 호출 2~3회 발생**
5. ✅ **Chat 화면은 사용자 질문 시에만 호출** (정상)
6. ⚠️ **프롬프트 크기가 매우 큼** (평균 2000~3000 토큰)

---

## 1️⃣ AI 호출 함수 목록 및 상세 분석

### 1-1. `analyzeTimeFromText()` - 시간 분석

**위치**: `HuenDongMinAiAgent.kt:89`

**호출 경로**:
- `processGmailForEvent()` → `analyzeTimeFromText()` (Gmail 처리 시)
- `processSMSForEvent()` → `analyzeTimeFromText()` (SMS 처리 시)
- `processPushNotificationForEvent()` → `analyzeTimeFromText()` (Push 알림 처리 시)
- `createEventFromImage()` → `analyzeTimeFromText()` (OCR 처리 시)

**호출 빈도**:
- **Gmail**: 동기화 시 모든 메시지마다 1회 호출
- **SMS**: 수신 시마다 1회 호출 (자동 처리 활성화 시)
- **Push 알림**: 수신 시마다 1회 호출 (필터링 통과 시)
- **OCR**: 이미지 처리 시 1회 호출

**프롬프트 크기**: 약 **2,500~3,000 토큰** (매우 큼)
- 구어체 정규화 규칙, 날짜 표현, 시간대 표현 등 상세 가이드 포함
- 중복 설명 다수 발견

**중복 호출 방지**: ❌ 없음
- 같은 텍스트를 여러 번 처리하면 매번 호출됨

---

### 1-2. `extractEventSummary()` - 일정 요약 추출

**위치**: `HuenDongMinAiAgent.kt:2929`

**호출 경로**:
- `processGmailForEvent()` → `extractEventSummary()` (Gmail 처리 시)
- `processSMSForEvent()` → `extractEventSummary()` (SMS 처리 시)
- `createEventFromImage()` → `extractEventSummary()` (OCR 처리 시)

**호출 빈도**:
- 일정이 2개 이상인 경우에만 호출 (1단계)
- 일정이 1개 이하면 호출되지 않음

**프롬프트 크기**: 약 **800~1,000 토큰**

**중복 호출 방지**: ❌ 없음

---

### 1-3. `createEventFromSummary()` - 일정 상세 생성

**위치**: `HuenDongMinAiAgent.kt:3035`

**호출 경로**:
- `processGmailForEvent()` → `extractEventSummary()` → `createEventFromSummary()` (일정이 2개 이상)
- `processSMSForEvent()` → `extractEventSummary()` → `createEventFromSummary()` (일정이 2개 이상)
- `createEventFromImage()` → `extractEventSummary()` → `createEventFromSummary()` (일정이 2개 이상)

**호출 빈도**:
- 일정이 2개 이상이면 각 일정마다 1회 호출
- 예: 일정 3개면 3회 호출

**프롬프트 크기**: 약 **400~500 토큰**

**중복 호출 방지**: ❌ 없음

---

### 1-4. `extractSearchFilters()` - 검색 필터 추출

**위치**: `HuenDongMinChatGatewayImpl.kt:496`

**호출 경로**:
- `fetchContext()` → `extractSearchFilters()` (Chat 화면에서 질문 시)

**호출 빈도**:
- 사용자가 Chat 화면에서 질문할 때마다 1회 호출
- **Inbox 진입 시 자동 호출 없음** ✅

**프롬프트 크기**: 약 **1,500~2,000 토큰** (매우 큼)
- 자연어 시간 표현 해석 규칙이 매우 상세함

**중복 호출 방지**: ❌ 없음 (debounce/throttle 없음)

---

### 1-5. `tryCreateEventFromQuestion()` - 질문에서 일정 생성

**위치**: `HuenDongMinChatGatewayImpl.kt:203`

**호출 경로**:
- `requestChatCompletion()` → `detectEventCreationIntent()` → `tryCreateEventFromQuestion()` (일정 생성 의도 감지 시)

**호출 빈도**:
- Chat 화면에서 일정 생성 의도가 감지될 때만 호출
- **자동 호출 없음** ✅

**프롬프트 크기**: 약 **2,500~3,000 토큰** (매우 큼)
- 자연어 시간 표현 해석 규칙이 매우 상세함

**중복 호출 방지**: ❌ 없음

---

### 1-6. `OpenAIClassifier.classifyEmail()` - 이메일 분류

**위치**: `OpenAIClassifier.kt:59`

**호출 경로**:
- `ClassifiedDataRepository.processAndStoreEmail()` → `classifyEmail()` (이메일 처리 시)

**호출 빈도**:
- **현재 사용되지 않음** (Gmail은 `processGmailForEvent()` 사용)
- `ClassifiedDataRepository`는 레거시 코드로 보임

**프롬프트 크기**: 약 **600~800 토큰**

**중복 호출 방지**: ❌ 없음

---

### 1-7. `OpenAIClassifier.classifyPushNotification()` - 푸시 알림 분류

**위치**: `OpenAIClassifier.kt:119`

**호출 경로**:
- `ClassifiedDataRepository.processAndStorePushNotification()` → `classifyPushNotification()` (푸시 알림 처리 시)

**호출 빈도**:
- **현재 사용되지 않음** (Push 알림은 `processPushNotificationForEvent()` 사용)
- `ClassifiedDataRepository`는 레거시 코드로 보임

**프롬프트 크기**: 약 **400~500 토큰**

**중복 호출 방지**: ❌ 없음

---

### 1-8. `requestChatCompletion()` - 채팅 답변 생성

**위치**: `HuenDongMinChatGatewayImpl.kt:88`

**호출 경로**:
- Chat 화면에서 사용자 질문 입력 시

**호출 빈도**:
- 사용자가 질문을 입력하고 전송할 때만 호출
- **자동 호출 없음** ✅

**프롬프트 크기**: 동적 (대화 히스토리 포함)

**중복 호출 방지**: ❌ 없음 (debounce/throttle 없음)

---

## 2️⃣ 각 함수의 호출 소스(Source) 분석

### 2-1. Push 알림 수신 시 자동 호출

**위치**: `PushNotificationListenerService.kt:35`

**호출 흐름**:
```
onNotificationPosted() 
  → saveNotification() 
    → processPushNotificationForEvent() 
      → analyzeTimeFromText() (1회)
      → extractEventSummary() (일정 2개 이상 시)
      → createEventFromSummary() (일정 개수만큼)
```

**필터링 메커니즘**:
- ✅ `PushNotificationFilterSettings.isPackageExcluded()` 체크
- ✅ 기본 허용 목록: Gmail, Naver Mail, SMS 앱만
- ⚠️ **문제**: 필터링은 있으나, 통과한 알림은 모두 LLM 호출

**호출 빈도**:
- 사용자가 받는 모든 푸시 알림마다 호출
- 예상: 하루 50~200개 알림 → 50~200회 LLM 호출

**중복 방지**:
- ❌ 같은 알림이 여러 번 수신되면 매번 호출
- ❌ `notificationId` 기반 중복 체크 없음

---

### 2-2. SMS 수신 시 자동 호출

**위치**: `SmsAutoProcessReceiver.kt:21`

**호출 흐름**:
```
onReceive() 
  → processSMSForEvent() 
    → analyzeTimeFromText() (1회)
    → extractEventSummary() (일정 2개 이상 시)
    → createEventFromSummary() (일정 개수만큼)
```

**필터링 메커니즘**:
- ✅ `AutoProcessSettings.isSmsAutoProcessEnabled()` 체크
- ✅ `AutoProcessSettings.isWithinSmsAutoProcessPeriod()` 체크 (기간 제한)
- ⚠️ **문제**: 기간 내 SMS는 모두 LLM 호출

**호출 빈도**:
- 자동 처리 활성화 시 수신하는 모든 SMS마다 호출
- 예상: 하루 10~50개 SMS → 10~50회 LLM 호출

**중복 방지**:
- ❌ 같은 SMS가 여러 번 수신되면 매번 호출
- ❌ `originalSmsId` 기반 중복 체크 없음

---

### 2-3. Gmail 동기화 시 호출

**위치**: `GmailRepositoryWithAi.kt:27`

**호출 흐름**:
```
syncRecentMessages() 
  → processMessageWithAi() 
    → processGmailForEvent() 
      → analyzeTimeFromText() (1회)
      → extractEventSummary() (일정 2개 이상 시)
      → createEventFromSummary() (일정 개수만큼)
```

**필터링 메커니즘**:
- ✅ `GmailSyncManager`에서 중복 메시지 체크 (`getById()`)
- ✅ `sinceTimestamp` 기반 증분 동기화
- ⚠️ **문제**: 새 메시지는 모두 LLM 호출

**호출 빈도**:
- 동기화 시 새 메시지마다 호출
- 예상: 하루 20~100개 메시지 → 20~100회 LLM 호출

**중복 방지**:
- ✅ `ingestItemDao.getById()` 체크로 중복 메시지 스킵
- ⚠️ **문제**: 같은 메시지를 여러 번 동기화하면 첫 번째만 스킵, 나머지는 호출

---

### 2-4. OCR 처리 시 호출

**위치**: `OcrRepositoryWithAi.kt` (간접)

**호출 흐름**:
```
processOcrText() 
  → createEventFromImage() 
    → analyzeTimeFromText() (1회)
    → extractEventSummary() (일정 2개 이상 시)
    → createEventFromSummary() (일정 개수만큼)
```

**필터링 메커니즘**:
- ❌ 없음 (사용자가 수동으로 OCR 처리)

**호출 빈도**:
- 사용자가 OCR 이미지를 공유할 때만 호출
- 예상: 하루 0~10회

**중복 방지**:
- ❌ 없음

---

### 2-5. Chat 화면 호출

**위치**: `HuenDongMinChatGatewayImpl.kt`

**호출 흐름**:
```
사용자 질문 입력 
  → fetchContext() → extractSearchFilters() (1회)
  → requestChatCompletion() → tryCreateEventFromQuestion() (일정 생성 의도 시)
  → requestChatCompletion() → callOpenAiWithChatMessages() (답변 생성)
```

**필터링 메커니즘**:
- ✅ 사용자 입력에 의한 호출만 (자동 호출 없음)

**호출 빈도**:
- 사용자가 질문할 때만 호출
- 예상: 하루 5~20회

**중복 방지**:
- ❌ debounce/throttle 없음 (빠르게 여러 번 입력하면 여러 번 호출)

---

### 2-6. ViewModel 초기화 시 호출 여부

**위치**: `MainViewModel.kt:221`

**분석 결과**:
- ✅ **LLM 호출 없음**
- `loadClassifiedData()`는 DB 조회만 수행 (`getAllContacts()`, `getAllEvents()`, `getAllNotes()`)
- `loadOcrEvents()`, `loadSmsEvents()`, `loadPushNotificationEvents()`는 DB 필터링만 수행

**결론**: ViewModel init 시 자동 LLM 호출 없음 ✅

---

## 3️⃣ LLM 호출 빈도 분석

### 3-1. 같은 Push 알림이 여러 번 호출되는지

**현재 상태**: ⚠️ **중복 호출 가능**

**원인**:
- `PushNotificationListenerService.onNotificationPosted()`는 알림이 표시될 때마다 호출
- 같은 알림이 여러 번 표시되면 매번 `processPushNotificationForEvent()` 호출
- `notificationId` 기반 중복 체크 없음

**예상 시나리오**:
- 앱이 알림을 여러 번 표시 → 같은 알림에 대해 LLM 호출 2~3회
- 알림이 업데이트될 때마다 호출 → 같은 알림에 대해 LLM 호출 여러 회

**개선 필요**: ✅ `notificationId` 기반 중복 체크 추가 필요

---

### 3-2. 동일 IngestItem에 대해 중복 분석이 발생하는지

**현재 상태**: ⚠️ **중복 분석 가능**

**원인**:
- `MainViewModel.createEventFromItem()`에서 수동으로 일정 생성 시
- 이미 처리된 `IngestItem`을 다시 처리하면 LLM 호출 재발생
- `IngestItem` 기반 중복 체크 없음

**예상 시나리오**:
- 사용자가 Inbox에서 "일정 생성" 버튼을 여러 번 클릭
- 같은 `IngestItem`에 대해 `processSMSForEvent()` 여러 번 호출

**개선 필요**: ✅ `IngestItem.id` 기반 중복 체크 추가 필요

---

### 3-3. ViewModel에서 init 시마다 호출되는지

**현재 상태**: ✅ **호출 없음**

**확인 결과**:
- `MainViewModel.init`에서는 DB 조회만 수행
- LLM 호출 함수 호출 없음

---

### 3-4. Debounce / Throttle 적용 여부

**현재 상태**: ❌ **적용 없음**

**영향**:
- Chat 화면에서 빠르게 여러 번 질문하면 여러 번 LLM 호출
- `extractSearchFilters()`가 매번 호출됨

**개선 필요**: ✅ Chat 화면에 debounce 적용 필요

---

## 4️⃣ 프롬프트 크기 분석

### 4-1. 각 프롬프트의 평균 입력 토큰 수 (추정)

| 함수 | System Prompt | User Prompt | 총 토큰 (추정) | 비고 |
|------|---------------|--------------|----------------|------|
| `analyzeTimeFromText` | ~2,000 | ~500 | **2,500~3,000** | 매우 큼 |
| `extractEventSummary` | ~600 | ~200 | **800~1,000** | 보통 |
| `createEventFromSummary` | ~300 | ~100 | **400~500** | 작음 |
| `extractSearchFilters` | ~1,500 | ~100 | **1,600~2,000** | 매우 큼 |
| `tryCreateEventFromQuestion` | ~2,200 | ~200 | **2,400~3,000** | 매우 큼 |
| `classifyEmail` | ~400 | ~200 | **600~800** | 보통 |
| `classifyPushNotification` | ~200 | ~200 | **400~500** | 작음 |
| `requestChatCompletion` | 동적 | 동적 | **500~2,000** | 가변 |

### 4-2. 불필요하게 긴 문장 / 중복 규칙 / Redundant 설명

#### 🔴 `analyzeTimeFromText` 프롬프트 문제점

**위치**: `HuenDongMinAiAgent.kt:98-228`

**문제점**:
1. **중복 설명**: 구어체 정규화 규칙이 여러 곳에 반복됨
2. **과도한 예시**: 예시가 너무 많음 (10개 이상)
3. **불필요한 설명**: "⚠️ 중요", "🔴 예시" 등 이모지와 강조 문구가 많음

**개선 가능 토큰**: 약 **500~800 토큰** 절감 가능

**예시**:
```kotlin
// 현재: 매우 상세한 설명
"① 날짜 표현 줄임말
- "낼", "내" → "내일"
- "모래" → "모레"
..."

// 개선: 간결하게
"줄임말: 낼/내→내일, 모래→모레, 담주/담쥬→다음주, 수욜→수요일"
```

---

#### 🔴 `extractSearchFilters` 프롬프트 문제점

**위치**: `HuenDongMinChatGatewayImpl.kt:515-605`

**문제점**:
1. **자연어 시간 표현 해석 규칙이 너무 상세함**
2. **`analyzeTimeFromText`와 중복되는 내용 다수**
3. **불필요한 예시 반복**

**개선 가능 토큰**: 약 **400~600 토큰** 절감 가능

---

#### 🔴 `tryCreateEventFromQuestion` 프롬프트 문제점

**위치**: `HuenDongMinChatGatewayImpl.kt:225-363`

**문제점**:
1. **자연어 시간 표현 해석 규칙이 매우 상세함** (300줄 이상)
2. **`analyzeTimeFromText`와 거의 동일한 내용**
3. **과도한 예시와 설명**

**개선 가능 토큰**: 약 **800~1,200 토큰** 절감 가능

---

### 4-3. 프롬프트 압축 우선순위

1. **최우선**: `analyzeTimeFromText` (가장 많이 호출됨)
2. **2순위**: `tryCreateEventFromQuestion` (프롬프트가 매우 큼)
3. **3순위**: `extractSearchFilters` (Chat 화면에서 자주 호출)

---

## 5️⃣ 전체 토큰 사용량을 줄이기 위한 개선 방안

### 5-1. 불필요 알림 필터링 강화

#### 현재 상태
- ✅ `PushNotificationFilterSettings`로 기본 필터링 있음
- ⚠️ 패턴 기반 필터링 없음

#### 개선 방안

**1) 시스템 알림 / 광고 패턴 기반 차단**

```kotlin
// PushNotificationListenerService.kt에 추가
private fun shouldSkipNotification(
    packageName: String,
    title: String?,
    text: String?
): Boolean {
    // 시스템 알림 차단
    if (packageName.startsWith("android") || 
        packageName == "com.android.systemui") {
        return true
    }
    
    // 광고 키워드 차단
    val adKeywords = listOf("광고", "할인", "% 할인", "쿠폰", "이벤트", "프로모션")
    val fullText = "${title ?: ""} ${text ?: ""}".lowercase()
    if (adKeywords.any { fullText.contains(it) }) {
        return true
    }
    
    // 배달 앱 알림 차단 (배민, 요기요 등)
    val deliveryApps = listOf(
        "com.baemin", "com.yogiyo", "com.coupang", "com.ssg"
    )
    if (deliveryApps.any { packageName.contains(it) }) {
        return true
    }
    
    // 로그인/인증 알림 차단
    val authKeywords = listOf("로그인", "인증", "OTP", "인증번호", "비밀번호")
    if (authKeywords.any { fullText.contains(it) }) {
        return true
    }
    
    // 배터리/시스템 알림 차단
    val systemKeywords = listOf("배터리", "충전", "저장공간", "업데이트")
    if (systemKeywords.any { fullText.contains(it) }) {
        return true
    }
    
    return false
}
```

**예상 절감 효과**: 하루 50~100회 LLM 호출 절감 (약 **30~50%**)

---

### 5-2. 프롬프트 압축

#### 방안 1: 공통 규칙을 별도 함수로 분리

```kotlin
// 공통 시간 표현 해석 규칙을 한 곳에 정의
private fun getTimeExpressionRules(): String = """
줄임말: 낼/내→내일, 모래→모레, 담주/담쥬→다음주, 수욜→수요일
시간대: 새벽(03-06), 아침(06-09), 점심(12-13), 저녁(18-21), 밤(21-24)
날짜: 오늘, 내일, 모레, 다음주, 이번달
""".trimIndent()
```

**예상 절감 효과**: 프롬프트당 **500~800 토큰** 절감

---

#### 방안 2: 예시 최소화

**현재**: 10개 이상의 예시  
**개선**: 핵심 예시 2~3개만 유지

**예상 절감 효과**: 프롬프트당 **200~400 토큰** 절감

---

#### 방안 3: 이모지/강조 문구 제거

**현재**: "⚠️⚠️⚠️", "🔴🔴🔴", "✅" 등 이모지 다수  
**개선**: 텍스트만 사용

**예상 절감 효과**: 프롬프트당 **50~100 토큰** 절감

---

### 5-3. 빈 화면 로딩 시 자동 호출 제거

**현재 상태**: ✅ **이미 구현됨**
- ViewModel init 시 LLM 호출 없음
- Inbox 진입 시 자동 호출 없음

---

### 5-4. 중복 호출 Guard 로직 추가

#### 방안 1: Push 알림 중복 체크

```kotlin
// PushNotificationListenerService.kt
private val processedNotificationIds = mutableSetOf<String>()

private fun saveNotification(...) {
    val notificationId = "push-${timestamp}-${packageName.hashCode()}"
    
    // 중복 체크
    if (processedNotificationIds.contains(notificationId)) {
        Log.d(TAG, "이미 처리된 알림, 건너뜀: $notificationId")
        return
    }
    
    processedNotificationIds.add(notificationId)
    
    // 기존 로직...
}
```

**예상 절감 효과**: 하루 10~30회 LLM 호출 절감

---

#### 방안 2: IngestItem 기반 중복 체크

```kotlin
// HuenDongMinAiAgent.kt
private val processedIngestItemIds = mutableSetOf<String>()

suspend fun processSMSForEvent(..., originalSmsId: String): AiProcessingResult {
    // 중복 체크
    if (processedIngestItemIds.contains(originalSmsId)) {
        android.util.Log.d("HuenDongMinAiAgent", "이미 처리된 SMS, 건너뜀: $originalSmsId")
        return AiProcessingResult("note", 0.0, emptyList())
    }
    
    processedIngestItemIds.add(originalSmsId)
    
    // 기존 로직...
}
```

**예상 절감 효과**: 하루 5~15회 LLM 호출 절감

---

#### 방안 3: Chat 화면 Debounce

```kotlin
// ChatViewModel.kt
private var lastQueryTime = 0L
private val DEBOUNCE_DELAY = 500L // 500ms

fun submit(query: String) {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastQueryTime < DEBOUNCE_DELAY) {
        return // 너무 빠른 연속 호출 무시
    }
    lastQueryTime = currentTime
    
    // 기존 로직...
}
```

**예상 절감 효과**: 사용자 실수로 인한 중복 호출 방지

---

### 5-5. 일정이 2개 이상일 때의 2단계 처리 최적화

**현재 문제**:
- 일정이 2개 이상이면 `extractEventSummary()` + `createEventFromSummary()` N회 호출
- 총 LLM 호출: 1 + N회 (N = 일정 개수)

**개선 방안**:
- 일정이 3개 이상이면 배치 처리로 한 번에 처리
- 또는 1단계에서 모든 일정 정보를 한 번에 추출

**예상 절감 효과**: 일정 3개 이상일 때 **30~50%** 토큰 절감

---

## 6️⃣ 우선순위별 개선 계획

### 🔴 긴급 (즉시 적용 권장)

1. **Push 알림 패턴 기반 필터링 추가**
   - 시스템 알림, 광고, 배달 앱, 로그인 알림 차단
   - 예상 절감: 하루 **50~100회** LLM 호출

2. **Push 알림 중복 체크 추가**
   - `notificationId` 기반 중복 방지
   - 예상 절감: 하루 **10~30회** LLM 호출

3. **IngestItem 기반 중복 체크 추가**
   - 이미 처리된 항목 재처리 방지
   - 예상 절감: 하루 **5~15회** LLM 호출

---

### 🟡 중요 (단기 개선)

4. **`analyzeTimeFromText` 프롬프트 압축**
   - 중복 설명 제거, 예시 최소화
   - 예상 절감: 호출당 **500~800 토큰**

5. **`tryCreateEventFromQuestion` 프롬프트 압축**
   - `analyzeTimeFromText`와 중복되는 내용 제거
   - 예상 절감: 호출당 **800~1,200 토큰**

6. **Chat 화면 Debounce 추가**
   - 빠른 연속 호출 방지
   - 예상 절감: 사용자 실수 방지

---

### 🟢 선택 (중기 개선)

7. **일정 2단계 처리 최적화**
   - 배치 처리 또는 1단계 통합
   - 예상 절감: 일정 3개 이상일 때 **30~50%** 토큰

8. **프롬프트 캐싱**
   - System Prompt를 한 번 생성하여 재사용
   - 예상 절감: 호출당 **50~100 토큰**

---

## 7️⃣ 예상 토큰 절감 효과

### 현재 일일 토큰 사용량 (추정)

| 소스 | 호출 횟수 | 토큰/호출 | 일일 토큰 |
|------|----------|-----------|-----------|
| Push 알림 | 100회 | 3,000 | 300,000 |
| SMS | 30회 | 3,000 | 90,000 |
| Gmail | 50회 | 3,000 | 150,000 |
| OCR | 5회 | 3,000 | 15,000 |
| Chat | 10회 | 2,000 | 20,000 |
| **합계** | **195회** | - | **575,000 토큰** |

### 개선 후 예상 토큰 사용량

| 소스 | 호출 횟수 | 토큰/호출 | 일일 토큰 |
|------|----------|-----------|-----------|
| Push 알림 | 30회 (70% 절감) | 2,200 (27% 절감) | 66,000 |
| SMS | 20회 (33% 절감) | 2,200 (27% 절감) | 44,000 |
| Gmail | 50회 (유지) | 2,200 (27% 절감) | 110,000 |
| OCR | 5회 (유지) | 2,200 (27% 절감) | 11,000 |
| Chat | 10회 (유지) | 1,500 (25% 절감) | 15,000 |
| **합계** | **115회** | - | **246,000 토큰** |

### 절감 효과
- **호출 횟수**: 195회 → 115회 (**41% 절감**)
- **토큰 사용량**: 575,000 → 246,000 토큰 (**57% 절감**)
- **월간 절감**: 약 **9,870,000 토큰** (약 **$30~50** 절감, 모델에 따라 다름)

---

## 8️⃣ 결론 및 권장 사항

### 핵심 발견 사항

1. ⚠️ **Push 알림이 가장 큰 토큰 소비 원인** (하루 100회 호출)
2. ⚠️ **프롬프트 크기가 과도하게 큼** (평균 2,500~3,000 토큰)
3. ⚠️ **중복 호출 방지 로직 부족**
4. ✅ **Chat 화면은 정상** (사용자 입력 시에만 호출)

### 즉시 적용 권장 사항

1. **Push 알림 패턴 기반 필터링** (최우선)
2. **중복 호출 Guard 로직 추가**
3. **프롬프트 압축** (특히 `analyzeTimeFromText`)

### 예상 효과

- **토큰 사용량 50~60% 절감**
- **API 비용 월 $30~50 절감**
- **응답 속도 개선** (프롬프트가 짧아짐)

---

**보고서 작성 완료**

