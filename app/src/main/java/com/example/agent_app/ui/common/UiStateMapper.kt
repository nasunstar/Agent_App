package com.example.agent_app.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.agent_app.R

/**
 * UI 리브랜딩: 상태→문구/아이콘 매핑
 * 모든 화면에서 일관된 상태 표현을 위해 사용
 * 
 * ⚠️ 로직 변경 금지: 표시 전용 매핑만 수행
 */
enum class UiState {
    Waiting,   // 대기 중
    Loading,   // 로딩 중
    Success,   // 성공
    Error,     // 오류
    Empty      // 빈 상태
}

/**
 * 상태에 따른 문자열 리소스 ID와 아이콘 반환
 */
data class StateDisplay(
    @StringRes val textResId: Int,
    val icon: ImageVector
)

/**
 * UiState를 표시용 문구와 아이콘으로 매핑
 */
fun mapUiState(state: UiState): StateDisplay {
    return when (state) {
        UiState.Waiting -> StateDisplay(
            textResId = R.string.state_me_waiting,
            icon = Icons.Filled.Schedule
        )
        UiState.Loading -> StateDisplay(
            textResId = R.string.state_me_loading,
            icon = Icons.Filled.Refresh
        )
        UiState.Success -> StateDisplay(
            textResId = R.string.state_me_success,
            icon = Icons.Filled.CheckCircle
        )
        UiState.Error -> StateDisplay(
            textResId = R.string.state_me_error,
            icon = Icons.Filled.Error
        )
        UiState.Empty -> StateDisplay(
            textResId = R.string.empty_records,
            icon = Icons.Filled.Inbox
        )
    }
}

/**
 * 출처(source)에 따른 뱃지 텍스트 리소스 ID 반환
 */
@StringRes
fun getSourceBadgeText(source: String): Int {
    return when (source.lowercase()) {
        "gmail", "mail" -> R.string.badge_from_mail
        "ocr", "image" -> R.string.badge_from_image
        "chat" -> R.string.badge_from_chat
        "sms", "push_notification", "notification" -> R.string.badge_from_sms
        else -> R.string.badge_from_mail // 기본값
    }
}

/**
 * 신뢰도(confidence)에 따른 라벨 리소스 ID 반환
 */
@StringRes
fun getConfidenceLabel(confidence: Double?): Int {
    return when {
        confidence == null -> R.string.label_confidence_low
        confidence >= 0.8 -> R.string.label_confidence_high
        confidence >= 0.5 -> R.string.label_confidence_mid
        else -> R.string.label_confidence_low
    }
}

