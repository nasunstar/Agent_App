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
    inline: Boolean = false, // 인라인 모드 (기본값 false로 기존 동작 유지)
    progress: Float? = null, // 진행률 (0.0 ~ 1.0, null이면 무한 로딩)
    modifier: Modifier = Modifier
) {
    if (inline) {
        // 인라인 로딩: Row 형태로 작은 크기
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                progress = { progress ?: 0f }
            )
            Text(
                text = message ?: stringResource(R.string.state_me_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        // 전체 화면 로딩: 기존 동작 유지
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
                strokeWidth = 3.dp,
                progress = { progress ?: 0f }
            )
            
            Text(
                text = message ?: stringResource(R.string.state_me_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            // 진행률이 있으면 LinearProgressIndicator 추가 표시
            if (progress != null) {
                Spacer(modifier = Modifier.height(Dimens.spacingSM))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(0.6f),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

