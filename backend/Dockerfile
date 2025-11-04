# Kotlin Ktor 백엔드 서버 Dockerfile
FROM gradle:8.5-jdk17 AS builder

# 작업 디렉토리 설정
WORKDIR /app

# Gradle 캐시를 활용하기 위해 build.gradle.kts 먼저 복사
COPY backend/build.gradle.kts backend/
COPY backend/settings.gradle.kts backend/ 2>/dev/null || true
COPY gradle/ gradle/
COPY gradlew .
COPY gradlew.bat .

# 의존성 다운로드 (캐시 활용)
RUN gradle :backend:dependencies --no-daemon || true

# 소스 코드 복사
COPY backend/ backend/

# 애플리케이션 빌드
RUN gradle :backend:build --no-daemon

# 실행 단계
FROM openjdk:17-jre-slim

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/backend/build/libs/*.jar app.jar

# 포트 노출 (환경 변수에서 읽을 수 있음)
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]

