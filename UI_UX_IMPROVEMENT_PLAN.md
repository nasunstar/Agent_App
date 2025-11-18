# MOA UI/UX 개선 작업 계획

## 📋 변경 파일 목록

### 1. 공통 컴포넌트 개선
- `app/src/main/java/com/example/agent_app/ui/common/components/EmptyState.kt` - 옵션 추가 (icon, actionLabel, onAction)
- `app/src/main/java/com/example/agent_app/ui/common/components/LoadingState.kt` - 인라인 옵션 추가
- `app/src/main/java/com/example/agent_app/ui/common/components/InfoCard.kt` - 클릭 가능 여부 시각적 구분, 접근성 개선
- `app/src/main/java/com/example/agent_app/ui/common/components/ActionChip.kt` - 접근성 개선, 일관된 스타일
- `app/src/main/java/com/example/agent_app/ui/common/components/TimelineItem.kt` - 정보 표시 개선 (시간, 장소, 출처)

### 2. 화면별 개선
- `app/src/main/java/com/example/agent_app/ui/DashboardScreen.kt` - LazyColumn 전환, EmptyState 통합, 정보 표시 개선
- `app/src/main/java/com/example/agent_app/ui/chat/ChatScreen.kt` - 입력창 UX, 메시지 카드 구분, 인라인 로딩
- `app/src/main/java/com/example/agent_app/ui/MainScreen.kt` - CalendarContent (오늘 강조, 이벤트 마커), InboxContent (필터 상태, 섹션 헤더)
- `app/src/main/java/com/example/agent_app/ui/share/ShareCalendarScreen.kt` - 폼 개선, 검색 결과 시각화

### 3. 디자인 토큰
- `app/src/main/java/com/example/agent_app/ui/theme/Color.kt` - MOA 브랜드 색상 정리 (확인 필요)
- `app/src/main/java/com/example/agent_app/ui/theme/Dimens.kt` - 추가 토큰 필요 시 확장

### 4. 문자열 리소스
- `app/src/main/res/values/strings.xml` - 빈 상태 메시지 개선 (1인칭 화법 강화)

---

## 🔄 작업 순서

### Phase 1: 공통 컴포넌트 개선 (기반 작업)
1. EmptyState - 옵션 추가
2. LoadingState - 인라인 옵션 추가
3. InfoCard - 클릭 가능 여부 구분, 접근성
4. ActionChip - 접근성 개선
5. TimelineItem - 정보 표시 개선

### Phase 2: DashboardScreen 개선
6. LazyColumn 전환
7. EmptyState 통합 (오늘/이번주 모두 비었을 때)
8. TimelineItem을 통한 정보 표시 개선
9. ActionChip 레이아웃 개선 (FlowRow)

### Phase 3: ChatScreen 개선
10. 입력창 UX (trailingIcon, ImeAction)
11. 메시지 카드 구분 (질문/답변)
12. 인라인 로딩 상태

### Phase 4: Calendar 화면 개선
13. 오늘 날짜 강조
14. 이벤트 마커 표시
15. 일정 리스트 Typography/간격 정리

### Phase 5: Inbox 화면 개선
16. 필터 상태 명확화
17. 섹션 헤더 추가
18. 아이템 카드 레이아웃 개선

### Phase 6: 공유 캘린더 화면 개선
19. 폼 필드 구분 (필수/선택)
20. 검색 결과 시각화
21. EmptyState 통일

### Phase 7: 접근성 및 최종 정리
22. 주요 버튼/아이콘 contentDescription 추가
23. 터치 타겟 크기 확인 및 수정
24. 디자인 토큰 하드코딩 제거

---

## ✅ 검증 체크리스트

각 단계 완료 후 확인:
- [ ] 기존 기능이 정상 작동하는가?
- [ ] 비즈니스 로직이 변경되지 않았는가?
- [ ] 새로운 기능이 추가되지 않았는가?
- [ ] 1인칭 화법이 유지되는가?
- [ ] 접근성이 개선되었는가?
- [ ] 디자인 일관성이 유지되는가?

