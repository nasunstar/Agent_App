# 관리자 계정 관리 기능 가이드

HuenDongMin Assistant 앱의 관리자 계정 관리 기능은 여러 Google 계정을 백엔드 서버에 연결하여 Gmail 데이터를 수집할 수 있도록 합니다.

## 아키텍처 개요

```
Android 앱 (개발자 기능) ─┐
                         │
                         ├─ Custom Tabs (브라우저)
                         │
                         ↓
            Ktor 백엔드 서버 (localhost:8080)
                         │
                         ├─ Google OAuth2 플로우
                         │
                         └─ H2 Database (계정 정보 저장)
```

## 1. 백엔드 서버 설정

### 1.1 Google OAuth 클라이언트 ID 생성

1. [Google Cloud Console](https://console.cloud.google.com/)에 접속
2. 프로젝트 선택 또는 생성
3. **"API 및 서비스" → "OAuth 동의 화면"**
   - 사용자 유형: 외부 (또는 내부)
   - 앱 이름, 사용자 지원 이메일 설정
   - 개발자 연락처 정보 입력
   - 저장 후 계속

4. **"사용자 인증 정보" → "사용자 인증 정보 만들기" → "OAuth 클라이언트 ID"**
   - 애플리케이션 유형: **웹 애플리케이션**
   - 이름: `HuenDongMin Backend`
   - **승인된 리디렉션 URI 추가:**
     ```
     http://localhost:8080/admin/accounts/connect/google/callback
     ```
   - 생성 클릭

5. **클라이언트 ID**와 **클라이언트 Secret** 복사

### 1.2 환경 변수 설정

백엔드 서버 실행 전에 다음 환경 변수를 설정하세요:

#### Windows (PowerShell)
```powershell
$env:GOOGLE_CLIENT_ID="your-client-id-here"
$env:GOOGLE_CLIENT_SECRET="your-client-secret-here"
$env:OAUTH_REDIRECT_URI="http://localhost:8080/admin/accounts/connect/google/callback"
```

#### macOS/Linux (Bash)
```bash
export GOOGLE_CLIENT_ID="your-client-id-here"
export GOOGLE_CLIENT_SECRET="your-client-secret-here"
export OAUTH_REDIRECT_URI="http://localhost:8080/admin/accounts/connect/google/callback"
```

또는 프로젝트 루트에 `.env` 파일 생성:
```bash
GOOGLE_CLIENT_ID=your-client-id-here
GOOGLE_CLIENT_SECRET=your-client-secret-here
OAUTH_REDIRECT_URI=http://localhost:8080/admin/accounts/connect/google/callback
```

### 1.3 백엔드 서버 실행

```bash
# Gradle로 실행
./gradlew :backend:run

# 또는 JAR 빌드 후 실행
./gradlew :backend:build
java -jar backend/build/libs/backend-1.0.0.jar
```

서버가 `http://localhost:8080`에서 시작됩니다.

#### 서버 상태 확인
```bash
curl http://localhost:8080/health
# 응답: OK
```

## 2. Android 앱 설정

### 2.1 백엔드 서버 URL 설정

#### Android Emulator 사용 시
- 기본 설정 그대로 사용 (`http://10.0.2.2:8080`)
- Emulator에서 `10.0.2.2`는 호스트 머신의 `localhost`를 가리킵니다

#### 실제 기기 사용 시
1. 컴퓨터의 IP 주소 확인:
   - Windows: `ipconfig`
   - macOS/Linux: `ifconfig` 또는 `ip addr`

2. 다음 파일들의 URL 수정:
   - `app/src/main/java/com/example/agent_app/backend/AdminBackendServiceFactory.kt`
     ```kotlin
     private const val BASE_URL = "http://YOUR_IP:8080"
     ```
   - `app/src/main/java/com/example/agent_app/ui/AdminAccountCard.kt`
     ```kotlin
     val backendUrl = "http://YOUR_IP:8080/admin/accounts/connect/google"
     ```

3. 방화벽에서 포트 8080 허용

### 2.2 앱 빌드 및 실행

```bash
./gradlew :app:assembleDebug
```

## 3. 사용 방법

### 3.1 계정 추가

1. 앱 실행 후 **왼쪽 위 햄버거 메뉴** 클릭
2. **"개발자 기능"** 선택
3. **"관리자 계정 관리"** 카드에서 **"계정 추가"** 버튼 클릭
4. 브라우저가 열리면서 Google 로그인 페이지 표시
5. 연결할 Google 계정으로 로그인
6. 권한 동의 화면에서 **"허용"** 클릭
7. "계정 연결 성공" 메시지 확인
8. 브라우저 창 닫기
9. 앱으로 돌아와서 계정 목록 새로고침

### 3.2 계정 확인

- "관리자 계정 관리" 카드에 연결된 계정 목록이 표시됩니다
- 각 계정에는 이메일 주소와 등록 시간이 표시됩니다

### 3.2 계정 삭제

- 계정 카드 오른쪽의 **🗑️ 삭제** 버튼 클릭
- 계정이 즉시 삭제되고 목록에서 제거됩니다

## 4. API 엔드포인트

백엔드 서버는 다음 API를 제공합니다:

### 4.1 계정 연결 시작
```
GET /admin/accounts/connect/google
```
Google OAuth 인증 페이지로 리다이렉트

### 4.2 OAuth 콜백 (자동 처리)
```
GET /admin/accounts/connect/google/callback?code=...
```
Google이 인증 완료 후 자동 호출

### 4.3 계정 목록 조회
```
GET /admin/accounts
```

응답 예시:
```json
{
  "accounts": [
    {
      "id": 1,
      "email": "admin@example.com",
      "scopes": [
        "https://www.googleapis.com/auth/gmail.readonly",
        "https://www.googleapis.com/auth/userinfo.email"
      ],
      "expiresAt": "2025-11-04T10:00:00Z",
      "createdAt": "2025-11-03T09:00:00Z",
      "updatedAt": "2025-11-03T09:00:00Z"
    }
  ]
}
```

### 4.4 계정 삭제
```
DELETE /admin/accounts/{email}
```

## 5. 문제 해결

### 5.1 "계정 목록 로드 실패: Failed to connect"

**원인:** 백엔드 서버가 실행되지 않았거나 URL이 잘못되었습니다.

**해결 방법:**
1. 백엔드 서버가 실행 중인지 확인:
   ```bash
   curl http://localhost:8080/health
   ```
2. Android Emulator 사용 시 `10.0.2.2` 사용
3. 실제 기기 사용 시 컴퓨터 IP 주소로 URL 변경

### 5.2 "OAuth 인증 시작 실패"

**원인:** Google OAuth 클라이언트 ID나 Secret이 설정되지 않았습니다.

**해결 방법:**
1. 환경 변수가 올바르게 설정되었는지 확인
2. 백엔드 서버 로그 확인:
   ```
   GOOGLE_CLIENT_ID: YOUR_CLIENT_ID_HERE (잘못됨)
   ```
3. 올바른 Client ID와 Secret으로 환경 변수 설정 후 서버 재시작

### 5.3 "redirect_uri_mismatch" 오류

**원인:** Google Cloud Console에 등록된 리디렉션 URI와 실제 사용하는 URI가 다릅니다.

**해결 방법:**
1. Google Cloud Console → OAuth 클라이언트 설정 확인
2. 승인된 리디렉션 URI에 다음이 정확히 등록되어 있는지 확인:
   ```
   http://localhost:8080/admin/accounts/connect/google/callback
   ```
3. 변경 후 몇 분 대기 (Google 설정 반영 시간)

### 5.4 브라우저가 열리지 않음

**원인:** Custom Tabs를 지원하지 않는 디바이스이거나 브라우저가 설치되지 않았습니다.

**해결 방법:**
1. Chrome 브라우저 설치
2. 디바이스 재부팅
3. 다른 Android 디바이스나 Emulator에서 테스트

## 6. 보안 고려사항

### 6.1 프로덕션 환경

프로덕션 환경에서는 다음 사항을 반드시 고려하세요:

1. **HTTPS 사용:** HTTP 대신 HTTPS 사용
2. **환경 변수 보안:** 민감한 정보를 코드에 하드코딩하지 않기
3. **데이터베이스 암호화:** H2 대신 PostgreSQL 등 프로덕션 DB 사용
4. **인증/인가:** 관리자 인증 메커니즘 추가
5. **Rate Limiting:** API 요청 제한
6. **로그 관리:** 민감한 정보 로그에 출력하지 않기

### 6.2 토큰 보안

- Access Token과 Refresh Token은 데이터베이스에 저장됩니다
- 프로덕션에서는 토큰 암호화 권장
- 정기적인 토큰 갱신 로직 구현

## 7. 데이터베이스

### 7.1 위치
```
backend/build/admin_accounts_db.mv.db
```

### 7.2 스키마
```sql
CREATE TABLE admin_accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) UNIQUE NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    id_token TEXT,
    scopes TEXT,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 7.3 데이터베이스 초기화

데이터베이스를 초기화하려면:
```bash
rm backend/build/admin_accounts_db.mv.db
```
서버 재시작 시 새 데이터베이스가 자동 생성됩니다.

## 8. 향후 개선 사항

- [ ] 토큰 자동 갱신 기능
- [ ] 관리자 인증 시스템
- [ ] 계정별 Gmail 데이터 수집 스케줄러
- [ ] 웹 대시보드 (계정 관리 UI)
- [ ] 다중 OAuth 제공자 지원 (Microsoft, Yahoo 등)
- [ ] 계정 활성화/비활성화 토글
- [ ] 계정별 데이터 수집 통계

## 9. 참고 자료

- [Ktor 공식 문서](https://ktor.io/)
- [Google OAuth2 가이드](https://developers.google.com/identity/protocols/oauth2)
- [Gmail API 문서](https://developers.google.com/gmail/api)
- [Android Custom Tabs](https://developer.android.com/develop/ui/views/launch/custom-tabs)

