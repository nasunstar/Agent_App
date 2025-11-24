package com.example.agent_app.ui.common.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.agent_app.R
import com.example.agent_app.ui.common.UiState
import com.example.agent_app.ui.common.mapUiState
import com.example.agent_app.ui.theme.*
import com.example.agent_app.ui.theme.Dimens

/**
 * UI 리브랜딩: 상태 표시 컴포넌트
 * 대기/로딩/완료/오류 상태를 일관되게 표시
 * 
 * ⚠️ 로직 변경 금지: 표시 전용
 */
@Composable
fun StatusIndicator(
    state: UiState,
    modifier: Modifier = Modifier,
    message: String? = null
) {
    val stateDisplay = mapUiState(state)
    val color = when (state) {
        UiState.Waiting -> StatusWaiting
        UiState.Loading -> StatusLoading
        UiState.Success -> StatusSuccess
        UiState.Error -> StatusError
        UiState.Empty -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.spacingMD),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state == UiState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimens.iconSmall),
                color = color,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = stateDisplay.icon,
                contentDescription = stringResource(stateDisplay.textResId),
                tint = color,
                modifier = Modifier.size(Dimens.iconMedium)
            )
        }
        
        Text(
            text = message ?: stringResource(stateDisplay.textResId),
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

