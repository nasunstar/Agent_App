package com.example.agent_app.ui.theme

import androidx.compose.ui.graphics.Color

// === MOA 브랜딩: 메인 색상 팔레트 ===
// 메인 포인트: 신뢰, 편안함, 휴식 (#77BFA3)
val MoaPrimary = Color(0xFF77BFA3) // 메인 민트 그린
val MoaPrimaryLight = Color(0xFFA8D5C4) // 밝은 민트
val MoaPrimaryDark = Color(0xFF5A9A85) // 진한 민트

// 서브 포인트: 활력, 에너지 (#FFC96F)
val MoaSecondary = Color(0xFFFFC96F) // 서브 노란색
val MoaSecondaryLight = Color(0xFFFFD99A) // 밝은 노란색
val MoaSecondaryDark = Color(0xFFFFB844) // 진한 노란색

// 배경 색상
val MoaBackgroundLight = Color(0xFFF5F8F6) // 라이트 모드 배경
val MoaBackgroundDark = Color(0xFF1A1A1A) // 다크 모드 배경

// 텍스트 색상
val MoaTextLight = Color(0xFF2D3748) // 라이트 모드 텍스트
val MoaTextDark = Color(0xFFE2E8F0) // 다크 모드 텍스트

// === 기존 Material 색상 (하위 호환성 유지) ===
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// === UI 리브랜딩: 출처별 강조 색상 ===
// 메일 💌 (하늘색 계열)
val SourceMailLight = Color(0xFF4FC3F7) // 밝은 하늘색
val SourceMailDark = Color(0xFF0288D1)  // 진한 하늘색
val SourceMailContainer = Color(0xFFE1F5FE) // 라이트 모드 컨테이너
val SourceMailContainerDark = Color(0xFF01579B) // 다크 모드 컨테이너

// 사진 📸 (주황색 계열)
val SourceImageLight = Color(0xFFFF9800) // 밝은 주황색
val SourceImageDark = Color(0xFFE65100)   // 진한 주황색
val SourceImageContainer = Color(0xFFFFE0B2) // 라이트 모드 컨테이너
val SourceImageContainerDark = Color(0xFFBF360C) // 다크 모드 컨테이너

// 대화 💬 (보라색 계열)
val SourceChatLight = Color(0xFF9C27B0) // 밝은 보라색
val SourceChatDark = Color(0xFF6A1B9A)  // 진한 보라색
val SourceChatContainer = Color(0xFFE1BEE7) // 라이트 모드 컨테이너
val SourceChatContainerDark = Color(0xFF4A148C) // 다크 모드 컨테이너

// 문자 📱 (초록색 계열)
val SourceSmsLight = Color(0xFF66BB6A) // 밝은 초록색
val SourceSmsDark = Color(0xFF2E7D32)  // 진한 초록색
val SourceSmsContainer = Color(0xFFC8E6C9) // 라이트 모드 컨테이너
val SourceSmsContainerDark = Color(0xFF1B5E20) // 다크 모드 컨테이너

// === 상태 색상 (MOA 톤) ===
val StatusWaiting = Color(0xFF9E9E9E) // 회색
val StatusLoading = Color(0xFF77BFA3) // MOA 메인 색상 사용
val StatusSuccess = Color(0xFF77BFA3) // MOA 메인 색상 사용
val StatusError = Color(0xFFE57373)   // 부드러운 빨간색
