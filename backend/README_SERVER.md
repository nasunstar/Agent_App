# 백엔드 서버 실행 가이드

## 방법 1: IntelliJ IDEA에서 실행 (가장 권장)

1. IntelliJ IDEA에서 `backend/src/main/kotlin/com/example/agent_app/backend/Application.kt` 파일 열기
2. `main` 함수 옆의 실행 버튼(▶) 클릭
3. 또는 `Shift + F10` 키 누르기

## 방법 2: Android Studio 터미널에서 실행

1. Android Studio 하단의 **Terminal** 탭 클릭
2. 다음 명령 실행:
   ```bash
   .\gradlew.bat :backend:run
   ```

## 방법 3: Windows PowerShell 새 창에서 실행

1. Windows 키 → PowerShell 검색 → **새 창 열기**
2. 프로젝트 폴더로 이동:
   ```powershell
   cd C:\Users\USER\AndroidStudioProjects\Agent_App
   ```
3. 서버 실행:
   ```powershell
   .\gradlew.bat :backend:run
   ```

## 서버 시작 확인

서버가 시작되면 (~10-30초 소요):
- 브라우저에서 `http://localhost:8080/health` 접속
- `OK`가 표시되면 정상 작동 중

## 주의사항

- 서버는 계속 실행 상태를 유지해야 합니다
- 터미널 창을 닫으면 서버가 종료됩니다
- 서버를 중지하려면 `Ctrl + C`를 누르세요

