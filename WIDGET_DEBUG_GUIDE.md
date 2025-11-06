# 위젯 오류 진단 가이드

## 🔍 확인해야 할 정보

위젯 오류를 진단하기 위해 다음 정보를 확인해주세요:

### 1. **빌드 로그 (Build Output)**
- Android Studio 하단의 "Build" 탭에서 오류 메시지
- 특히 Kotlin 컴파일 오류나 경고

### 2. **런타임 로그 (Logcat)**
- Android Studio 하단의 "Logcat" 탭
- 필터: `SummaryWidget` 또는 `Glance`
- 빨간색 오류 메시지 확인

### 3. **위젯 동작 상태**
- 홈스크린에 위젯을 추가했을 때 나타나는 메시지
- "콘텐츠를 표시할 수 없습니다" 같은 구체적인 메시지
- 위젯이 아예 나타나지 않는지, 빈 화면인지

### 4. **앱 동작**
- 앱 자체는 정상 실행되는지
- 데이터베이스에 데이터가 있는지 (대시보드에서 확인)

## 🛠️ 일반적인 문제점

### 문제 1: 데이터베이스 접근 실패
**증상**: 위젯이 빈 화면 또는 "콘텐츠를 표시할 수 없습니다"

**확인 방법**:
- Logcat에서 `SummaryWidget` 태그로 검색
- "오늘 데이터 조회 실패" 또는 "이번주 데이터 조회 실패" 메시지 확인

### 문제 2: Glance 버전 호환성
**증상**: 빌드 오류 또는 런타임 크래시

**확인 방법**:
- `gradle/libs.versions.toml`에서 Glance 버전 확인
- 최신 Android SDK와 호환되는지 확인

### 문제 3: 위젯 권한 문제
**증상**: 위젯이 추가되지 않음

**확인 방법**:
- AndroidManifest.xml에서 receiver가 올바르게 등록되었는지
- `android:exported="true"` 설정 확인

### 문제 4: 리소스 파일 누락
**증상**: 위젯 추가 시 오류

**확인 방법**:
- `res/xml/summary_widget_info.xml` 파일 존재 확인
- `res/values/strings.xml`에 `widget_description` 확인

## 📝 로그 확인 방법

1. Android Studio에서 Logcat 탭 열기
2. 필터에 `tag:SummaryWidget` 입력
3. 위젯을 추가하거나 업데이트할 때 로그 확인
4. 오류 메시지 전체 내용 복사

## 🔧 빠른 해결 시도

### 시도 1: 앱 재설치
```bash
adb uninstall com.example.agent_app
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 시도 2: 위젯 완전 제거 후 재추가
1. 홈스크린에서 위젯 삭제
2. 앱 재시작
3. 위젯 다시 추가

### 시도 3: 데이터 확인
- 앱을 열어서 대시보드에서 데이터가 있는지 확인
- Gmail 동기화나 일정이 있는지 확인

## 📤 공유할 정보

다음 정보를 공유해주시면 더 정확한 진단이 가능합니다:

1. **Logcat 로그** (SummaryWidget 태그 필터)
2. **빌드 오류 메시지** (있다면)
3. **위젯 추가 시 나타나는 정확한 메시지**
4. **Android 버전** (기기 정보)
5. **앱에서 데이터가 있는지 여부** (대시보드 확인)

