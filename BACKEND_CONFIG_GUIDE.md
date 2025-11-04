# 백엔드 설정 가이드

이 문서는 HuenDongMin 백엔드 서버와 Android 앱의 설정 방법을 설명합니다.

## 📋 목차

1. [보안 설정](#보안-설정)
2. [백엔드 서버 설정](#백엔드-서버-설정)
3. [Android 앱 설정](#android-앱-설정)
4. [환경별 설정](#환경별-설정)

## 🔒 보안 설정

### 중요: 민감한 정보 관리

**절대 하드코딩하지 마세요!** 다음 정보는 환경 변수나 설정 파일로 관리해야 합니다:

- ✅ Google OAuth Client ID
- ✅ Google OAuth Client Secret
- ✅ Database 연결 정보 (프로덕션 환경)

### 설정 파일 위치

1. **환경 변수** (최우선)
   - `GOOGLE_CLIENT_ID`
   - `GOOGLE_CLIENT_SECRET`
   - `OAUTH_REDIRECT_URI`

2. **local.properties** (프로젝트 루트)
   ```properties
   GOOGLE_WEB_CLIENT_ID=your-client-id-here
   GOOGLE_CLIENT_SECRET=your-client-secret-here
   OAUTH_REDIRECT_URI=http://localhost:8080/admin/auth/google/callback
   ```

3. **application.conf** (백엔드, 빌드 제외 권장)
   - Git에 커밋하지 마세요!

## 🖥️ 백엔드 서버 설정

### 1. Google OAuth 설정

#### Google Cloud Console 설정

1. [Google Cloud Console](https://console.cloud.google.com/) 접속
2. 프로젝트 선택 → **"API 및 서비스"** → **"사용자 인증 정보"**
3. **"사용자 인증 정보 만들기"** → **"OAuth 클라이언트 ID"**
4. 애플리케이션 유형: **"웹 애플리케이션"**
5. **승인된 리디렉션 URI** 추가:
   ```
   http://localhost:8080/admin/auth/google/callback
   ```
6. 클라이언트 ID와 Secret 복사

#### 환경 변수 설정

**Windows (PowerShell):**
```powershell
$env:GOOGLE_CLIENT_ID="your-client-id-here"
$env:GOOGLE_CLIENT_SECRET="your-client-secret-here"
$env:OAUTH_REDIRECT_URI="http://localhost:8080/admin/auth/google/callback"
```

**macOS/Linux (Bash):**
```bash
export GOOGLE_CLIENT_ID="your-client-id-here"
export GOOGLE_CLIENT_SECRET="your-client-secret-here"
export OAUTH_REDIRECT_URI="http://localhost:8080/admin/auth/google/callback"
```

**또는 local.properties 파일 (프로젝트 루트):**
```properties
GOOGLE_WEB_CLIENT_ID=your-client-id-here
GOOGLE_CLIENT_SECRET=your-client-secret-here
OAUTH_REDIRECT_URI=http://localhost:8080/admin/auth/google/callback
```

### 2. 백엔드 서버 실행

```bash
# Windows
.\gradlew.bat :backend:run

# macOS/Linux
./gradlew :backend:run
```

서버가 `http://localhost:8080`에서 시작됩니다.

## 📱 Android 앱 설정

### 자동 설정 (권장)

앱은 자동으로 에뮬레이터를 감지하고 적절한 백엔드 URL을 사용합니다:

- **에뮬레이터**: `http://10.0.2.2:8080` (자동 감지)
- **실제 기기**: `http://192.168.219.104:8080` (기본값)

### 수동 설정

앱에서 백엔드 URL을 수동으로 설정할 수 있습니다:

```kotlin
// SharedPreferences에 저장
BackendConfig.setBackendUrl(context, "http://your-ip:8080")
```

### 설정 확인

1. 앱 실행
2. 왼쪽 위 햄버거 메뉴 → **"개발자 기능"**
3. **"관리자 계정 관리"** 확인

## 🌍 환경별 설정

### 개발 환경 (로컬)

**백엔드:**
- URL: `http://localhost:8080`
- Redirect URI: `http://localhost:8080/admin/auth/google/callback`
- Google Cloud Console에 `localhost` URI 등록

**Android 앱:**
- 에뮬레이터: `http://10.0.2.2:8080` (자동)
- 실제 기기: `http://192.168.x.x:8080` (수동 설정)

### 프로덕션 환경 (배포 시)

**백엔드:**
- HTTPS 사용 필수
- Redirect URI: `https://your-domain.com/admin/auth/google/callback`
- Google Cloud Console에 실제 도메인 등록

**Android 앱:**
- BuildConfig로 프로덕션 URL 설정
- 또는 서버에서 동적으로 제공

## ⚠️ 주의사항

### Google OAuth 제한사항

1. **Private IP 제한**
   - Google OAuth는 private IP (192.168.x.x)를 redirect URI로 허용하지 않습니다
   - **해결책**: `localhost` 사용 (에뮬레이터에서는 `10.0.2.2`)

2. **Redirect URI 일치**
   - Google Cloud Console에 등록된 URI와 정확히 일치해야 합니다
   - 프로토콜(http/https), 포트, 경로 모두 일치해야 함

3. **Desktop Application 타입**
   - 실제 기기에서 private IP를 사용하려면 Desktop Application 타입 필요
   - 하지만 이 경우 redirect URI 처리 방식이 다름

### 보안 권장사항

1. ✅ **환경 변수 사용** (프로덕션)
2. ✅ **local.properties를 .gitignore에 추가**
3. ✅ **application.conf를 .gitignore에 추가**
4. ✅ **HTTPS 사용** (프로덕션)
5. ❌ **하드코딩 금지**
6. ❌ **Git에 Secret 커밋 금지**

## 🔧 문제 해결

### OAuth 오류 발생 시

1. **Redirect URI 확인**
   - Google Cloud Console에 정확히 등록되어 있는지 확인
   - 백엔드 로그에서 사용하는 URI 확인

2. **에뮬레이터 사용**
   - 실제 기기에서 문제가 발생하면 에뮬레이터 사용
   - 에뮬레이터는 자동으로 감지됩니다

3. **환경 변수 확인**
   - 백엔드 서버 재시작 전 환경 변수 설정 확인
   - `local.properties` 파일 확인

### 백엔드 연결 실패 시

1. **서버 실행 확인**
   - `http://localhost:8080/health` 접속하여 확인
   - "OK" 응답이 나와야 함

2. **방화벽 확인**
   - 포트 8080이 열려있는지 확인
   - 실제 기기에서는 컴퓨터와 같은 네트워크에 있어야 함

3. **URL 확인**
   - Android 앱의 백엔드 URL이 올바른지 확인
   - 에뮬레이터 vs 실제 기기 구분

## 📚 추가 자료

- [Google OAuth 2.0 문서](https://developers.google.com/identity/protocols/oauth2)
- [Android 에뮬레이터 네트워킹](https://developer.android.com/studio/run/emulator-networking)

