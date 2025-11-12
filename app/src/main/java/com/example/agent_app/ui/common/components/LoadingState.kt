package com.example.agent_app.ui.common.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.agent_app.R
import com.example.agent_app.ui.theme.Dimens

/**
 * UI 리브랜딩: 로딩 상태 컴포넌트
 * 잔잔한 로딩 표시 + 문구
 * 
 * ⚠️ 로직 변경 금지: 표시 전용
 */
@Composable
fun LoadingState(
    message: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.spacingXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Dimens.iconLarge),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp
        )
        
        Text(
            text = message ?: stringResource(R.string.state_me_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

