# 보안 가이드

이 문서는 HuenDongMin 백엔드 서버의 보안 설정과 권장사항을 설명합니다.

## 🔒 구현된 보안 기능

### 1. CORS (Cross-Origin Resource Sharing)
- ✅ 허용된 Origin 설정 가능
- ✅ 환경 변수 `ALLOWED_ORIGINS`로 제어
- ✅ 개발 환경: 모든 origin 허용
- ✅ 프로덕션: 특정 도메인만 허용

### 2. 보안 HTTP 헤더
- ✅ `X-Content-Type-Options: nosniff` - MIME 타입 스니핑 방지
- ✅ `X-Frame-Options: DENY` - Clickjacking 방지
- ✅ `X-XSS-Protection: 1; mode=block` - XSS 공격 방지
- ✅ `Referrer-Policy: strict-origin-when-cross-origin` - Referrer 정보 제한
- ✅ `Permissions-Policy` - 브라우저 기능 접근 제한

### 3. HTTPS 강제 (프로덕션)
- ✅ 프로덕션 환경에서 HTTP 요청을 HTTPS로 리다이렉트
- ✅ 환경 변수 `ENVIRONMENT=production` 설정 시 활성화

### 4. Rate Limiting
- ✅ IP별 요청 제한: 1분에 60회
- ✅ DDoS 공격 완화
- ⚠️ 현재는 인메모리 구현 (프로덕션에서는 Redis 사용 권장)

### 5. 민감 정보 보호
- ✅ 토큰 전체를 로그에 남기지 않음
- ✅ 환경 변수로 Secret 관리
- ✅ 데이터베이스에 암호화된 토큰 저장

### 6. 입력 검증
- ✅ OAuth 콜백 파라미터 검증
- ✅ 에러 메시지에서 상세 정보 제한

## ⚠️ 보안 체크리스트

### 배포 전 필수 확인

#### 1. 환경 변수 설정
```bash
# 필수
GOOGLE_CLIENT_ID=your-client-id
GOOGLE_CLIENT_SECRET=your-client-secret
OAUTH_REDIRECT_URI=https://your-domain.com/admin/auth/google/callback
DATABASE_URL=postgresql://...

# 보안 강화 (권장)
ENVIRONMENT=production
ALLOWED_ORIGINS=https://your-domain.com,https://app.your-domain.com
```

#### 2. Google Cloud Console 설정
- ✅ OAuth redirect URI에 HTTPS URL만 등록
- ✅ HTTP redirect URI 제거
- ✅ 승인된 JavaScript origins에 HTTPS 도메인만 추가

#### 3. 데이터베이스 보안
- ✅ PostgreSQL 사용 (프로덕션)
- ✅ 데이터베이스 연결 암호화 (SSL)
- ✅ 데이터베이스 접근 제한 (IP 화이트리스트)

#### 4. 서버 설정
- ✅ HTTPS 인증서 설정 (Let's Encrypt 등)
- ✅ 방화벽 설정 (필요한 포트만 열기)
- ✅ 정기적인 보안 업데이트

## 🚨 추가 보안 권장사항

### 1. 인증/인가 추가 (고급)

현재는 `/admin` 엔드포인트에 접근 제한이 없습니다. 프로덕션에서는 다음을 추가하세요:

```kotlin
// 예시: API Key 기반 인증
fun Route.requireApiKey() {
    intercept(ApplicationCallPipeline.Call) {
        val apiKey = call.request.header("X-API-Key")
        if (apiKey != System.getenv("API_KEY")) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid API Key")
            return@intercept finish()
        }
    }
}
```

### 2. Rate Limiting 개선

현재는 인메모리 구현입니다. 프로덕션에서는 Redis 사용:

```kotlin
// Redis를 사용한 Rate Limiting
implementation("io.github.microutils:kotlin-logging:3.0.5")
implementation("redis.clients:jedis:4.4.0")
```

### 3. 로깅 강화

- ✅ 민감 정보는 로그에 남기지 않음
- ✅ 구조화된 로깅 사용 (JSON 형식)
- ✅ 로그 모니터링 및 알림 설정

### 4. 입력 검증 강화

```kotlin
// 예시: 입력 검증
fun validateEmail(email: String): Boolean {
    return email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))
}

fun sanitizeInput(input: String): String {
    return input.trim().take(1000) // 최대 길이 제한
}
```

### 5. SQL Injection 방지

Exposed ORM을 사용하므로 SQL Injection 위험이 낮지만, 직접 쿼리 작성 시 주의:

```kotlin
// ❌ 나쁜 예
val query = "SELECT * FROM users WHERE email = '$email'"

// ✅ 좋은 예
Users.select { Users.email eq email }
```

### 6. XSS 방지

HTML 응답 시 이스케이프 처리:

```kotlin
import org.apache.commons.text.StringEscapeUtils

fun escapeHtml(input: String): String {
    return StringEscapeUtils.escapeHtml4(input)
}
```

### 7. CSRF 보호

세션 기반 인증 사용 시 CSRF 토큰 추가:

```kotlin
// CSRF 토큰 생성 및 검증
fun generateCsrfToken(): String {
    return UUID.randomUUID().toString()
}

fun validateCsrfToken(token: String, sessionToken: String): Boolean {
    return token == sessionToken
}
```

## 📊 보안 모니터링

### 1. 로그 모니터링
- ✅ 실패한 인증 시도 감지
- ✅ Rate limit 초과 시도 감지
- ✅ 비정상적인 요청 패턴 감지

### 2. 알림 설정
- ✅ 여러 번의 실패한 인증 시도 시 알림
- ✅ 서버 오류 시 알림
- ✅ Rate limit 초과 시 알림

### 3. 정기 점검
- ✅ 의존성 보안 업데이트 확인
- ✅ 취약점 스캔
- ✅ 로그 리뷰

## 🔐 환경 변수 보안

### 안전한 저장 방법

1. **배포 플랫폼 환경 변수** (권장)
   - Railway, Render 등은 환경 변수를 안전하게 저장
   - UI에서 직접 설정 가능

2. **Secret Manager 사용** (프로덕션)
   - AWS Secrets Manager
   - Google Cloud Secret Manager
   - Azure Key Vault

3. **절대 하지 말 것**
   - ❌ 코드에 하드코딩
   - ❌ Git에 커밋
   - ❌ 로그에 출력

## 📚 추가 자료

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Ktor Security](https://ktor.io/docs/security.html)
- [OWASP API Security Top 10](https://owasp.org/www-project-api-security/)

## 🆘 보안 이슈 발견 시

1. 즉시 해당 기능 비활성화
2. 로그 확인
3. 영향 범위 파악
4. 수정 및 배포
5. 사용자에게 알림 (필요시)

---

**마지막 업데이트**: 2025-01-04

