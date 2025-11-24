# 일정 시간 표시 문제 원인 분석 및 수정 제안

## 문제 원인 분석

### 1. Event 생성 시 epoch 계산 코드 분석

#### ✅ 올바른 부분
- **HuenDongMinAiAgent.kt**의 대부분의 epoch 생성 코드는 올바릅니다:
  - Line 395, 432, 3181 등: `atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()`
  - 이 코드는 KST 기준으로 epoch를 생성합니다.

#### ⚠️ 잠재적 문제 부분

**문제 1: 날짜 수정 시 epoch 재계산 (Line 2746-2747)**
```kotlin
val correctedDate = aiDate.withYear(targetYear).withMonth(targetMonth).withDayOfMonth(targetDay)
val correctedStartAt = correctedDate.toInstant().toEpochMilli()
```
- `aiDate`는 이미 `ZonedDateTime`이므로 `toInstant()`는 올바르게 작동합니다.
- 하지만 `withYear()`, `withMonth()`, `withDayOfMonth()`는 시간대 정보를 유지하므로 문제 없습니다.

**문제 2: Fallback 이벤트 생성 (Line 782-783)**
```kotlin
val fallbackDateTime = referenceDate.withHour(0).withMinute(0).withSecond(0).withNano(0)
val startAt = fallbackDateTime.toInstant().toEpochMilli()
```
- `referenceDate`가 이미 `ZonedDateTime`이므로 올바릅니다.

### 2. Event Entity 구조 분석

#### ✅ 정상
- **Event.kt**: `startAt: Long?`, `endAt: Long?` - 타입 정상
- Room annotation 정상
- 타임존 관련 문제 없음

### 3. 캘린더 화면 epoch → LocalDateTime 변환 코드 분석

#### ✅ 올바른 부분
- **MainScreen.kt Line 2850-2852**: 
  ```kotlin
  val eventDate = java.time.Instant.ofEpochMilli(timestamp)
      .atZone(java.time.ZoneId.of("Asia/Seoul"))
      .toLocalDate()
  ```
  ✅ 올바르게 KST로 변환합니다.

- **TimeFormatter.kt**:
  ```kotlin
  private val koreanZoneId = ZoneId.of("Asia/Seoul")
  private val formatter: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(koreanZoneId)
  
  fun format(timestampMillis: Long): String =
      formatter.format(Instant.ofEpochMilli(timestampMillis))
  ```
  ✅ 올바르게 KST로 포맷팅합니다.

#### ⚠️ 문제 발견!

**문제 1: ShareCalendarScreen.kt Line 798**
```kotlin
val zone = remember { ZoneId.systemDefault() }
```
- **원인**: `ZoneId.systemDefault()`를 사용하여 시스템 기본 타임존을 사용합니다.
- **영향**: 에뮬레이터나 기기가 UTC로 설정되어 있으면 9시간 차이가 발생합니다.
- **해결**: `ZoneId.of("Asia/Seoul")`로 강제 적용 필요

**문제 2: DashboardScreen.kt의 Calendar.getInstance() 사용**
- Line 256, 276, 306, 313, 331에서 `Calendar.getInstance()`를 사용
- 시스템 기본 타임존을 사용하므로 UTC 환경에서 문제 발생 가능
- 하지만 이 부분은 시간 표시가 아닌 날짜 비교용이므로 직접적인 문제는 아닙니다.

## 최종 문제 원인

### 주요 원인: ShareCalendarScreen의 ZoneId.systemDefault() 사용

**위치**: `app/src/main/java/com/example/agent_app/ui/share/ShareCalendarScreen.kt:798`

**문제**:
- 공유 캘린더 화면에서 `ZoneId.systemDefault()`를 사용
- 시스템 타임존이 UTC이면 epoch → LocalDateTime 변환 시 9시간 차이 발생
- 예: KST 15:00 저장 → UTC로 해석 → 06:00 표시 (9시간 차이)

### 부차적 원인: DashboardScreen의 Calendar.getInstance() 사용

**위치**: `app/src/main/java/com/example/agent_app/ui/DashboardScreen.kt`

**문제**:
- 여러 곳에서 `Calendar.getInstance()` 사용 (시스템 기본 타임존)
- 직접적인 시간 표시 문제는 아니지만, 일관성 문제 가능

## 수정 제안

### 1. ShareCalendarScreen.kt 수정

**변경 전**:
```kotlin
val zone = remember { ZoneId.systemDefault() }
```

**변경 후**:
```kotlin
val zone = remember { ZoneId.of("Asia/Seoul") }
```

### 2. DashboardScreen.kt 수정 (선택사항)

시스템 기본 타임존 대신 명시적으로 KST 사용하도록 변경 권장.

## Diff Patch

```diff
--- a/app/src/main/java/com/example/agent_app/ui/share/ShareCalendarScreen.kt
+++ b/app/src/main/java/com/example/agent_app/ui/share/ShareCalendarScreen.kt
@@ -795,7 +795,7 @@ private fun SharedCalendarMonthView(
 private fun SharedCalendarMonthView(
     events: List<com.example.agent_app.share.model.CalendarEventDto>,
 ) {
-    val zone = remember { ZoneId.systemDefault() }
+    val zone = remember { ZoneId.of("Asia/Seoul") }
     val parsedEvents = remember(events) {
         events.mapNotNull { event ->
             val startInstant = event.startAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
```

## 추가 검증 권장 사항

1. **MainScreen.kt의 CalendarContent**: 이미 올바르게 구현되어 있음
2. **TimeFormatter.kt**: 이미 올바르게 구현되어 있음
3. **HuenDongMinAiAgent.kt의 epoch 생성**: 이미 올바르게 구현되어 있음

## 결론

**주요 문제**: ShareCalendarScreen에서 `ZoneId.systemDefault()` 사용으로 인한 타임존 불일치

**해결 방법**: `ZoneId.of("Asia/Seoul")`로 강제 적용

**예상 효과**: 공유 캘린더 화면에서 시간이 정확하게 표시됨

