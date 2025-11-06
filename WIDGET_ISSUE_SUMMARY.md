# 위젯 오류 문제 요약

## 문제 상황
- 위젯이 홈스크린에 추가되지만 "Can't show content" 메시지가 표시됨
- 빌드는 성공하지만 런타임에서 위젯 콘텐츠가 렌더링되지 않음

## 로그 확인 결과
```
provideGlance 시작 - widgetId: AppWidgetId(appWidgetId=7)
오늘 데이터 조회 시작
오늘 데이터 조회 완료 - events: 1, items: 1
이번주 데이터 조회 시작
이번주 데이터 조회 완료 - events: 1, items: 1
데이터 준비 완료 - today: 19자, week: 19자
provideContent 시작
```
- **중요**: `provideContent 완료` 로그가 나타나지 않음
- 이는 `provideContent` 블록 내부에서 예외가 발생하고 있음을 의미

## 해결 시도한 내용
1. ✅ `initialLayout` 추가 (missing defaultLayout 오류 해결)
2. ✅ `previewLayout` 제거
3. ✅ `GlanceTheme.colors`를 `provideContent` 블록 내부로 이동
4. ✅ `try-catch` 제거 (Composable 함수 호출 주변에서는 지원되지 않음)
5. ✅ `clickable` 제거 (문제 격리 시도)

## 현재 코드 상태
- `SummaryWidget.kt`: Glance 기반 위젯 구현
- `summary_widget_info.xml`: 위젯 설정 파일 (initialLayout 포함)
- `AndroidManifest.xml`: Receiver 등록 완료

## 기술 스택
- Glance 1.1.0
- Android Gradle Plugin 8.6.1
- Kotlin 2.0.20
- compileSdk 36

## 추정 원인
1. `provideContent` 블록 내부에서 Glance API 사용 시 예외 발생
2. `GlanceTheme.colors` 접근 문제
3. Glance 버전 호환성 문제
4. 위젯 컨텍스트에서의 제약사항

## 필요한 정보
다른 개발자에게 문의할 때 다음 정보를 공유하세요:
1. 전체 Logcat 로그 (tag:SummaryWidget 필터)
2. 위젯 추가 시 발생하는 모든 오류 메시지
3. Android 버전 및 기기 정보
4. Glance 관련 의존성 버전

## 참고 파일
- `app/src/main/java/com/example/agent_app/widget/SummaryWidget.kt`
- `app/src/main/res/xml/summary_widget_info.xml`
- `app/src/main/AndroidManifest.xml`

