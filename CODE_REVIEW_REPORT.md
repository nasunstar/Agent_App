# 🔍 코드 리뷰 리포트 - 발표 전 필수 수정 사항

## 📋 CRITICAL Issues (발표 전 반드시 수정)

### 1. Needs Review 배지 네비게이션 미연결
**파일:** `app/src/main/java/com/example/agent_app/ui/DashboardScreen.kt:99`
**문제:** Needs Review 배지 클릭 시 아무 동작도 하지 않음 (TODO 주석만 있음)
**영향:** 사용자가 검토 필요 일정에 접근할 수 없음
**수정 방법:**
```kotlin
// 현재 (Line 99):
onClick = { /* TODO: 네비게이션 추가 */ },

// 수정 필요:
onClick = onNavigateToNeedsReview,  // MainScreen에서 전달받은 콜백 사용
```

**연결 필요 파일:**
- `app/src/main/java/com/example/agent_app/ui/MainScreen.kt` - `onNavigateToNeedsReview` 콜백 추가 필요
- `AssistantTab` enum에 `NeedsReview` 탭 추가 또는 별도 화면으로 라우팅

---

### 2. ✅ Chat Gateway에서 Context 중복 조회 (수정 완료)
**파일:** `app/src/main/java/com/example/agent_app/data/chat/HuenDongMinChatGatewayImpl.kt`
**상태:** ✅ **수정 완료** - 중복 조회 문제가 이미 해결됨
**확인 내용:**
- `ExecuteChatUseCase.kt:24`에서 `fetchContext`를 1회만 호출
- `requestChatCompletion`에 context를 파라미터로 전달 (중복 호출 없음)
- `ChatViewModel.toThreadEntry()`에서 `contextItems`를 `sources`로 변환하여 활용
- 실제 코드 확인 결과, 중복 조회가 발생하지 않음

**관련 파일:**
- `app/src/main/java/com/example/agent_app/domain/chat/usecase/ExecuteChatUseCase.kt:24,32` - context 1회 조회 후 전달 ✅
- `app/src/main/java/com/example/agent_app/data/chat/HuenDongMinChatGatewayImpl.kt:89-140` - context 파라미터 사용 ✅
- `app/src/main/java/com/example/agent_app/ui/chat/ChatViewModel.kt:121-128` - contextItems를 sources로 변환 ✅

---

### 3. ClassifiedDataRepository에서 needs_review 상태 미설정
**파일:** `app/src/main/java/com/example/agent_app/data/repo/ClassifiedDataRepository.kt:164, 209`
**문제:** `storeAsEvent`와 `storeAsEventFromOcr`에서 항상 `status = "pending"`으로 설정
**영향:** 
- OCR에서 validation mismatch가 발생해도 needs_review로 설정되지 않음
- HuenDongMinAiAgent에서는 needs_review를 설정하지만, ClassifiedDataRepository 경로는 무시됨
**수정 방법:**
```kotlin
// 현재 (Line 164, 209):
status = "pending",

// 수정 필요: classification 결과나 validation mismatch 여부에 따라 needs_review 설정
// 또는 HuenDongMinAiAgent 경로만 사용하도록 통일
```

**관련 파일:**
- `app/src/main/java/com/example/agent_app/ai/HuenDongMinAiAgent.kt:2827` - OCR에서 needs_review 설정 로직 있음
- 두 경로 간 일관성 필요

---

### 4. ✅ Chat Source 표시 시 Context 중복 조회 (수정 완료)
**파일:** `app/src/main/java/com/example/agent_app/ui/chat/ChatViewModel.kt`
**상태:** ✅ **수정 완료** - contextItems를 sources로 변환하여 활용 중
**확인 내용:**
- `ChatResult`에 포함된 `contextItems`를 `ChatViewModel.toThreadEntry()`에서 직접 사용
- `fetchContext` 중복 호출 없이 기존 contextItems를 `sources`로 변환 (Line 121-128)
- `ChatScreen.kt:394-410`에서 sources가 있으면 sources만 표시, 없으면 context 표시 (중복 방지) ✅

---

## ⚠️ IMPORTANT Improvements (발표 전 수정 권장)

### 5. Needs Review 화면 네비게이션 연결 누락
**파일:** `app/src/main/java/com/example/agent_app/ui/MainScreen.kt`
**문제:** NeedsReviewScreen이 생성되었지만 MainScreen에서 라우팅되지 않음
**수정 방법:**
- `AssistantTab` enum에 `NeedsReview` 추가 또는
- 별도 네비게이션 경로 추가
- DashboardScreen의 배지 클릭 시 해당 화면으로 이동

---

### 6. Event 생성 실패 시 에러 처리 부족
**파일:** `app/src/main/java/com/example/agent_app/ai/HuenDongMinAiAgent.kt`
**위치:** `createEventFromAiData`, `processGmailForEvent`, `processSMSForEvent` 등
**문제:** 
- JSON 파싱 실패 시 fallback 처리만 있고 사용자에게 알림 없음
- 네트워크 오류 시 재시도 로직 없음
**영향:** 발표 중 오류 발생 시 사용자 경험 저하
**수정 방법:**
- try-catch로 감싸고 사용자에게 에러 메시지 표시
- 실패한 이벤트는 needs_review로 설정하여 나중에 수정 가능하도록

---

### 7. ChatScreen에서 Sources 표시가 Context와 중복될 수 있음
**파일:** `app/src/main/java/com/example/agent_app/ui/chat/ChatScreen.kt:368-379`
**문제:** 
- `entry.context`와 `entry.sources`가 동일한 정보를 다르게 표시할 수 있음
- UI에서 중복 표시 가능성
**수정 방법:**
- sources는 context의 상위 1-2개만 표시하므로, context 표시를 생략하거나 구분 필요

---

### 8. Needs Review 화면에서 수정 다이얼로그 미구현
**파일:** `app/src/main/java/com/example/agent_app/ui/NeedsReviewScreen.kt:270-280`
**문제:** EventEditDialog 대신 간단한 AlertDialog만 표시
**영향:** 사용자가 일정을 수정할 수 없음
**수정 방법:**
- MainScreen의 EventEditDialog를 재사용하거나
- NeedsReviewScreen에 동일한 수정 다이얼로그 구현

---

### 9. LLM 호출 시 캐싱 누락 가능성
**파일:** `app/src/main/java/com/example/agent_app/data/chat/HuenDongMinChatGatewayImpl.kt:634`
**문제:** `callOpenAiWithChatMessages`에서 캐싱을 사용하지 않음
**영향:** 동일한 질문에 대해 매번 LLM 호출
**수정 방법:**
- Chat Gateway에도 LLMResponseCache 적용 검토

---

### 10. Timezone 일관성 검증 필요
**파일:** 전체 프로젝트
**현황:** 대부분 `Asia/Seoul` 사용 중이지만, 일부 위치에서 검증 필요
**확인 필요 위치:**
- `app/src/main/java/com/example/agent_app/data/repo/ClassifiedDataRepository.kt:148` - timestamp를 그대로 사용하는 경우
- 모든 epoch → LocalDateTime 변환 지점에서 `ZoneId.of("Asia/Seoul")` 사용 확인

---

## 💡 NICE TO HAVE (선택적 개선)

### 11. Needs Review 화면 로딩 상태 표시
**파일:** `app/src/main/java/com/example/agent_app/ui/NeedsReviewScreen.kt`
**개선:** IngestItem 조회 중 로딩 인디케이터 표시

---

### 12. Chat Source 표시 형식 개선
**파일:** `app/src/main/java/com/example/agent_app/ui/chat/ChatScreen.kt:479-510`
**개선:** 
- Source 클릭 시 원본 데이터 보기
- 더 나은 시각적 구분

---

### 13. Dashboard 배지 애니메이션
**파일:** `app/src/main/java/com/example/agent_app/ui/DashboardScreen.kt:97`
**개선:** needs_review 개수 변경 시 부드러운 애니메이션

---

### 14. 에러 바운더리 추가
**파일:** 전체 UI 컴포넌트
**개선:** 예상치 못한 오류 발생 시 크래시 방지

---

## 📊 UX Issues (발표 중 문제 가능성)

### 15. 키보드 처리
**파일:** `app/src/main/java/com/example/agent_app/ui/chat/ChatScreen.kt:200-206`
**현황:** IME insets 처리 있음 ✅
**확인 필요:** 작은 화면에서 키보드가 입력 필드를 가리는 경우

---

### 16. 다이얼로그 dismiss 처리
**파일:** 
- `app/src/main/java/com/example/agent_app/ui/NeedsReviewScreen.kt:261-390`
- `app/src/main/java/com/example/agent_app/ui/MainScreen.kt:3620-3663`
**현황:** `onDismissRequest` 설정됨 ✅
**확인 필요:** 백 버튼으로 dismiss 가능한지

---

### 17. 스크롤 동작
**파일:** `app/src/main/java/com/example/agent_app/ui/chat/ChatScreen.kt:208-214`
**현황:** LazyColumn 사용, 자동 스크롤 구현됨 ✅
**확인 필요:** 긴 대화에서 성능

---

### 18. 작은 화면 대응
**파일:** 전체 UI
**확인 필요:** 
- NeedsReviewItemCard가 작은 화면에서 잘림
- Dashboard 배지가 작은 화면에서 레이아웃 깨짐

---

## 🔧 수정 우선순위 요약

### 즉시 수정 (발표 전 필수)
1. Needs Review 배지 네비게이션 연결
2. ✅ Chat Gateway context 중복 조회 제거 (완료)
3. ClassifiedDataRepository needs_review 상태 설정

### 발표 전 수정 권장
4. Needs Review 화면 네비게이션 연결
5. Event 생성 실패 시 에러 처리
6. Needs Review 수정 다이얼로그 구현

### 선택적 개선
7. Chat Source UI 개선
8. 로딩 상태 표시
9. 에러 바운더리

---

## 📝 파일별 수정 체크리스트

### `app/src/main/java/com/example/agent_app/ui/DashboardScreen.kt`
- [ ] Line 99: `onNavigateToNeedsReview` 콜백 연결

### `app/src/main/java/com/example/agent_app/ui/MainScreen.kt`
- [ ] NeedsReviewScreen 라우팅 추가
- [ ] `onNavigateToNeedsReview` 콜백 구현

### `app/src/main/java/com/example/agent_app/data/chat/HuenDongMinChatGatewayImpl.kt`
- [x] Line 127: context 중복 조회 제거 (완료 - 중복 조회 없음 확인됨)
- [x] ExecuteChatUseCase에서 전달받은 context 활용 (완료)

### `app/src/main/java/com/example/agent_app/data/repo/ClassifiedDataRepository.kt`
- [ ] Line 164, 209: needs_review 상태 설정 로직 추가

### `app/src/main/java/com/example/agent_app/ui/NeedsReviewScreen.kt`
- [ ] Line 270-280: EventEditDialog 구현 또는 재사용

---

**생성일:** 2025-01-XX
**리뷰 범위:** 전체 프로젝트
**우선순위:** CRITICAL > IMPORTANT > NICE TO HAVE

