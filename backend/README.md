# HuenDongMin Backend Server

Kotlin Ktor 기반 백엔드 서버로, 관리자가 여러 Google 계정을 연결하고 관리할 수 있는 기능을 제공합니다.

## 설정

### 1. Google OAuth 클라이언트 생성

1. [Google Cloud Console](https://console.cloud.google.com/)에 접속
2. 프로젝트 생성 또는 선택
3. "API 및 서비스" → "OAuth 동의 화면" 설정
4. "사용자 인증 정보" → "사용자 인증 정보 만들기" → "OAuth 클라이언트 ID"
5. 애플리케이션 유형: "웹 애플리케이션"
6. 승인된 리디렉션 URI 추가:
   ```
   http://localhost:8080/admin/accounts/connect/google/callback
   ```
7. 클라이언트 ID와 클라이언트 Secret 저장

### 2. 환경 변수 설정

다음 환경 변수를 설정하세요:

```bash
export GOOGLE_CLIENT_ID="your-client-id"
export GOOGLE_CLIENT_SECRET="your-client-secret"
export OAUTH_REDIRECT_URI="http://localhost:8080/admin/accounts/connect/google/callback"
```

또는 `.env` 파일 생성:

```
GOOGLE_CLIENT_ID=your-client-id
GOOGLE_CLIENT_SECRET=your-client-secret
OAUTH_REDIRECT_URI=http://localhost:8080/admin/accounts/connect/google/callback
```

## 실행

### Gradle로 실행

```bash
./gradlew :backend:run
```

### JAR로 실행

```bash
./gradlew :backend:build
java -jar backend/build/libs/backend-1.0.0.jar
```

서버가 `http://localhost:8080`에서 실행됩니다.

## 배포

### 1. 로컬 또는 자체 서버에 JAR 배포

1. 환경 변수 설정 (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `OAUTH_REDIRECT_URI`, 필요 시 `DATABASE_URL`, `ENCRYPTION_KEY`, `ALLOWED_ORIGINS`, `ENVIRONMENT=production`)
2. 빌드:
   ```bash
   ./gradlew :backend:build
   ```
3. 산출물: `backend/build/libs/backend-1.0.0.jar`
4. 서버에서 실행:
   ```bash
   java -jar backend/build/libs/backend-1.0.0.jar
   ```
5. 시스템 서비스로 등록하거나 프로세스 매니저(pm2, systemd 등)에 연결하면 재시작/모니터링이 쉽습니다.

### 2. Docker 배포

프로젝트 루트에서 Docker 이미지를 빌드하고 실행할 수 있습니다.

```bash
# 이미지 빌드
docker build -t agent-app-backend ./backend

# 실행 (환경 변수는 상황에 맞게 지정)
docker run -d \
  -p 8080:8080 \
  -e GOOGLE_CLIENT_ID=... \
  -e GOOGLE_CLIENT_SECRET=... \
  -e OAUTH_REDIRECT_URI=https://your-domain/admin/accounts/connect/google/callback \
  -e DATABASE_URL=jdbc:postgresql://... \
  -e ENCRYPTION_KEY=... \
  -e ENVIRONMENT=production \
  -e ALLOWED_ORIGINS=https://your-app.com \
  --name agent-app-backend \
  agent-app-backend
```

Docker 이미지는 `backend/Dockerfile`을 사용하며, 빌드 시 Gradle을 통해 JAR를 생성한 뒤 컨테이너에 포함합니다.

### 3. Railway / Render 등 PaaS 배포

1. 해당 플랫폼에서 새 서비스 생성 (Dockerfile 또는 Gradle 빌드 이용)
2. 환경 변수(UI에서 제공) 설정
3. 데이터베이스(PostgreSQL) 프로비저닝 후 `DATABASE_URL`로 연결
4. 빌드 & 디플로이 설정: `./gradlew :backend:build` 또는 Dockerfile 사용
5. 도메인 연결 후 `ALLOWED_ORIGINS`, `OAUTH_REDIRECT_URI` 값을 실제 도메인으로 갱신

Railway에 배포할 때는 `railway.json`에 정의된 설정을 참고할 수 있습니다.

## API 엔드포인트

### 1. Google 계정 연결 시작

```
GET /admin/accounts/connect/google
```

브라우저에서 이 URL을 열면 Google 로그인 페이지로 리다이렉트됩니다.

### 2. OAuth 콜백 (자동 처리)

```
GET /admin/accounts/connect/google/callback
```

Google이 인증 완료 후 자동으로 호출합니다.

### 3. 연결된 계정 목록 조회

```
GET /admin/accounts
```

응답 예시:
```json
{
  "accounts": [
    {
      "id": 1,
      "email": "user@example.com",
      "scopes": [
        "https://www.googleapis.com/auth/gmail.readonly",
        "https://www.googleapis.com/auth/userinfo.email"
      ],
      "expiresAt": "2025-11-03T10:00:00Z",
      "createdAt": "2025-11-03T09:00:00Z",
      "updatedAt": "2025-11-03T09:00:00Z"
    }
  ]
}
```

### 4. 계정 삭제

```
DELETE /admin/accounts/{email}
```

예시:
```
DELETE /admin/accounts/user@example.com
```

## 데이터베이스

H2 데이터베이스를 사용하며, 파일은 `backend/build/admin_accounts_db.mv.db`에 저장됩니다.

### 테이블 스키마: admin_accounts

| 컬럼        | 타입      | 설명                   |
|-------------|-----------|------------------------|
| id          | BIGINT    | Primary Key            |
| email       | VARCHAR   | 이메일 (Unique)        |
| access_token| TEXT      | 액세스 토큰            |
| refresh_token| TEXT     | 리프레시 토큰          |
| id_token    | TEXT      | ID 토큰                |
| scopes      | TEXT      | 권한 목록 (JSON 배열)  |
| expires_at  | TIMESTAMP | 토큰 만료 시간         |
| created_at  | TIMESTAMP | 생성 시간              |
| updated_at  | TIMESTAMP | 업데이트 시간          |

## 보안 주의사항

- **프로덕션 환경**에서는 HTTPS 필수
- 환경 변수에 민감한 정보 저장
- 데이터베이스 암호화 권장
- 적절한 인증/인가 메커니즘 추가 필요

## 개발 팁

### 로그 레벨 변경

`backend/src/main/resources/logback.xml` 파일에서 로그 레벨 조정 가능

### 포트 변경

`backend/src/main/kotlin/com/example/agent_app/backend/Application.kt` 파일의 `port` 파라미터 수정

