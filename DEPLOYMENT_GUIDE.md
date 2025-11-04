# 온라인 서버 배포 가이드

이 가이드는 HuenDongMin 백엔드 서버를 온라인 서버에 배포하는 방법을 설명합니다.

## 🚀 추천 배포 플랫폼

### 1. Railway (가장 추천 ⭐)
- ✅ 무료 플랜 제공 (월 $5 크레딧)
- ✅ PostgreSQL 무료 제공
- ✅ Git 연동으로 자동 배포
- ✅ 간단한 설정
- 📍 [railway.app](https://railway.app)

### 2. Render
- ✅ 무료 플랜 제공
- ✅ PostgreSQL 무료 제공
- ✅ 자동 HTTPS
- 📍 [render.com](https://render.com)

### 3. Fly.io
- ✅ 무료 플랜 제공
- ✅ 전 세계 분산
- 📍 [fly.io](https://fly.io)

## 📋 배포 전 준비사항

### 1. Google Cloud Console 설정

온라인 서버의 도메인을 Google OAuth에 등록해야 합니다.

1. [Google Cloud Console](https://console.cloud.google.com/) 접속
2. 프로젝트 선택 → **"API 및 서비스"** → **"사용자 인증 정보"**
3. OAuth 클라이언트 ID 편집
4. **승인된 리디렉션 URI**에 다음 추가:
   ```
   https://your-app.railway.app/admin/auth/google/callback
   https://your-app.onrender.com/admin/auth/google/callback
   ```
   (배포 후 실제 URL로 변경)

### 2. 환경 변수 준비

다음 환경 변수들을 준비하세요:

- `GOOGLE_CLIENT_ID`: Google OAuth 클라이언트 ID
- `GOOGLE_CLIENT_SECRET`: Google OAuth 클라이언트 Secret
- `OAUTH_REDIRECT_URI`: 배포된 서버의 redirect URI
  - 예: `https://your-app.railway.app/admin/auth/google/callback`

## 🚂 Railway 배포 (추천)

### 1단계: Railway 가입 및 프로젝트 생성

1. [railway.app](https://railway.app) 접속
2. GitHub 계정으로 로그인
3. **"New Project"** 클릭
4. **"Deploy from GitHub repo"** 선택
5. 이 저장소 선택

### 2단계: PostgreSQL 데이터베이스 추가

1. Railway 프로젝트에서 **"+ New"** 클릭
2. **"Database"** → **"Add PostgreSQL"** 선택
3. 데이터베이스가 자동으로 생성됩니다
4. **"Variables"** 탭에서 `DATABASE_URL` 복사 (나중에 사용)

### 3단계: 환경 변수 설정

Railway 프로젝트에서 **"Variables"** 탭으로 이동하여 다음 환경 변수 추가:

```bash
GOOGLE_CLIENT_ID=your-client-id-here
GOOGLE_CLIENT_SECRET=your-client-secret-here
OAUTH_REDIRECT_URI=https://your-app.railway.app/admin/auth/google/callback
PORT=8080
```

**참고**: `DATABASE_URL`은 PostgreSQL 추가 시 자동으로 설정됩니다.

### 4단계: 배포 설정

Railway 프로젝트에서 **"Settings"** 탭으로 이동:

1. **"Root Directory"**: `backend` 설정
2. **"Build Command**: `./gradlew :backend:build` (또는 `gradlew.bat :backend:build`)
3. **"Start Command**: `java -jar backend/build/libs/backend-1.0.0.jar`

또는 **railway.json** 파일 생성:

```json
{
  "$schema": "https://railway.app/railway.schema.json",
  "build": {
    "builder": "NIXPACKS",
    "buildCommand": "./gradlew :backend:build"
  },
  "deploy": {
    "startCommand": "java -jar backend/build/libs/backend-1.0.0.jar",
    "restartPolicyType": "ON_FAILURE",
    "restartPolicyMaxRetries": 10
  }
}
```

### 5단계: 배포

1. GitHub에 코드 푸시
2. Railway가 자동으로 감지하여 빌드 및 배포 시작
3. 배포 완료 후 **"Generate Domain"** 클릭하여 도메인 생성
4. 생성된 도메인으로 서버 접근: `https://your-app.railway.app/health`

### 6단계: Google OAuth 설정 업데이트

1. 생성된 Railway 도메인 확인 (예: `https://your-app.railway.app`)
2. Google Cloud Console에서 redirect URI 업데이트:
   ```
   https://your-app.railway.app/admin/auth/google/callback
   ```
3. Railway 환경 변수 `OAUTH_REDIRECT_URI` 업데이트

## 🎨 Render 배포

### 1단계: Render 가입

1. [render.com](https://render.com) 접속
2. GitHub 계정으로 로그인

### 2단계: PostgreSQL 데이터베이스 생성

1. **"New +"** → **"PostgreSQL"** 선택
2. 데이터베이스 이름 입력
3. **"Create Database"** 클릭
4. **"Connections"** 탭에서 `Internal Database URL` 복사

### 3단계: Web Service 생성

1. **"New +"** → **"Web Service"** 선택
2. GitHub 저장소 연결
3. 설정:
   - **Name**: `huendongmin-backend`
   - **Environment**: `Docker` 또는 `Shell`
   - **Root Directory**: `backend`
   - **Build Command**: `./gradlew :backend:build`
   - **Start Command**: `java -jar backend/build/libs/backend-1.0.0.jar`

### 4단계: 환경 변수 설정

Render 대시보드에서 **"Environment"** 섹션에 다음 추가:

```bash
GOOGLE_CLIENT_ID=your-client-id-here
GOOGLE_CLIENT_SECRET=your-client-secret-here
OAUTH_REDIRECT_URI=https://your-app.onrender.com/admin/auth/google/callback
DATABASE_URL=postgresql://user:password@host:port/dbname
PORT=8080
```

### 5단계: 배포

1. **"Create Web Service"** 클릭
2. 자동으로 빌드 및 배포 시작
3. 완료 후 자동으로 HTTPS 도메인 제공

## 🐳 Docker 배포 (고급)

### Docker 이미지 빌드

```bash
cd backend
docker build -t huendongmin-backend .
```

### Docker 실행

```bash
docker run -d \
  -p 8080:8080 \
  -e GOOGLE_CLIENT_ID=your-id \
  -e GOOGLE_CLIENT_SECRET=your-secret \
  -e OAUTH_REDIRECT_URI=https://your-domain.com/admin/auth/google/callback \
  -e DATABASE_URL=postgresql://user:pass@host:5432/dbname \
  huendongmin-backend
```

## 📱 Android 앱 설정 업데이트

온라인 서버 배포 후 Android 앱의 백엔드 URL을 업데이트해야 합니다.

### 방법 1: 앱에서 직접 설정

```kotlin
// 개발자 메뉴에서 백엔드 URL 설정 기능 추가 권장
BackendConfig.setBackendUrl(context, "https://your-app.railway.app")
```

### 방법 2: BuildConfig 사용

`app/build.gradle.kts`에 추가:

```kotlin
android {
    buildTypes {
        release {
            buildConfigField("String", "BACKEND_URL", "\"https://your-app.railway.app\"")
        }
        debug {
            buildConfigField("String", "BACKEND_URL", "\"http://10.0.2.2:8080\"")
        }
    }
}
```

그리고 `BackendConfig.kt`에서:

```kotlin
fun getBackendUrl(context: Context, useEmulator: Boolean = false): String {
    // BuildConfig 사용
    val buildConfigUrl = BuildConfig.BACKEND_URL
    if (buildConfigUrl.isNotEmpty()) {
        return buildConfigUrl
    }
    // 기존 로직...
}
```

## ✅ 배포 확인

### 1. 서버 상태 확인

브라우저에서 다음 URL 접속:
```
https://your-app.railway.app/health
```

"OK"가 표시되면 정상 작동 중입니다.

### 2. 로그 확인

- Railway: 프로젝트 → **"Deployments"** → 로그 확인
- Render: **"Logs"** 탭에서 확인

### 3. Google OAuth 테스트

1. Android 앱에서 "계정 추가" 클릭
2. Google 로그인 진행
3. 성공하면 계정이 추가됨

## 🔒 보안 체크리스트

- ✅ 환경 변수에 Secret 저장 (하드코딩 금지)
- ✅ HTTPS 사용 (프로덕션)
- ✅ Google OAuth redirect URI 정확히 일치
- ✅ 데이터베이스 연결 암호화
- ✅ CORS 설정 (필요시)

## 🐛 문제 해결

### 데이터베이스 연결 실패

1. `DATABASE_URL` 환경 변수 확인
2. PostgreSQL 데이터베이스가 실행 중인지 확인
3. Railway/Render에서 데이터베이스 상태 확인

### OAuth 오류

1. Google Cloud Console의 redirect URI 확인
2. 환경 변수 `OAUTH_REDIRECT_URI` 확인
3. HTTPS 사용 확인 (프로덕션)

### 포트 오류

1. `PORT` 환경 변수가 설정되어 있는지 확인
2. Railway/Render는 자동으로 `PORT` 제공
3. 로컬에서는 기본값 8080 사용

## 📚 추가 자료

- [Railway 문서](https://docs.railway.app/)
- [Render 문서](https://render.com/docs)
- [Ktor 배포 가이드](https://ktor.io/docs/deploy.html)

