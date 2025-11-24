# 🔥 Firebase Crashlytics 설정 가이드

## 개요

MOA 앱에 Firebase Crashlytics가 통합되었습니다. 크래시를 자동으로 수집하고 분석할 수 있습니다.

## 설정 방법

### 1. Firebase Console에서 프로젝트 생성

1. [Firebase Console](https://console.firebase.google.com/) 접속
2. "프로젝트 추가" 클릭
3. 프로젝트 이름 입력 (예: "MOA Agent App")
4. Google Analytics 설정 (선택사항)

### 2. Android 앱 추가

1. Firebase 프로젝트 대시보드에서 "Android 앱 추가" 클릭
2. 패키지 이름 입력: `com.example.agent_app`
3. 앱 닉네임 입력 (선택사항)
4. SHA-1 인증서 지문 추가 (선택사항, Google Sign-In 사용 시 필요)

### 3. google-services.json 다운로드

1. "google-services.json 다운로드" 버튼 클릭
2. 다운로드한 파일을 `app/google-services.json`으로 복사
3. 현재는 플레이스홀더 파일이 있으므로 교체하면 됩니다

### 4. 빌드 및 테스트

```bash
./gradlew assembleDebug
```

## 기능

### 자동 크래시 수집

- 앱이 크래시되면 자동으로 Firebase에 전송
- 스택 트레이스, 기기 정보, OS 버전 등 자동 수집

### 로그 전송 (Release 빌드)

- Release 빌드에서 ERROR 레벨 이상의 로그가 Crashlytics로 자동 전송
- 민감 정보는 자동으로 마스킹됨

### 사용자 정보 설정

```kotlin
FirebaseCrashlytics.getInstance().setUserId("user123")
FirebaseCrashlytics.getInstance().setCustomKey("email", "user@example.com")
```

## 주의사항

- **google-services.json이 없어도 앱은 정상 작동합니다**
- Crashlytics는 google-services.json이 있을 때만 활성화됩니다
- Debug 빌드에서는 Crashlytics가 비활성화되어 있습니다 (Timber.DebugTree 사용)

## 확인 방법

1. Firebase Console > Crashlytics 탭에서 크래시 확인
2. 테스트 크래시 발생:
   ```kotlin
   FirebaseCrashlytics.getInstance().recordException(Exception("테스트 크래시"))
   ```

## 비용

- Firebase Crashlytics는 **무료**입니다
- 일일 크래시 보고서 수 제한 없음
- 대용량 앱도 무료로 사용 가능

