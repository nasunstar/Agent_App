package com.example.agent_app.ui.common.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.agent_app.R
import com.example.agent_app.ui.theme.Dimens

/**
 * UI 리브랜딩: 빈 상태 표시 컴포넌트
 * 데이터가 없을 때 일관된 빈 화면 표시
 * 
 * ⚠️ 로직 변경 금지: 표시 전용
 */
@Composable
fun EmptyState(
    @androidx.annotation.StringRes messageResId: Int = R.string.empty_message, // MOA 톤 기본 메시지
    icon: ImageVector? = Icons.Filled.Inbox, // 기본 아이콘, null이면 표시 안 함
    actionLabel: String? = null, // 액션 버튼 레이블 (옵션)
    onAction: (() -> Unit)? = null, // 액션 버튼 클릭 핸들러 (옵션)
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.spacingXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconLarge),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        
        Text(
            text = stringResource(messageResId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSM))
            Button(
                onClick = onAction,
                modifier = Modifier
                    .minimumInteractiveComponentSize() // 최소 48dp 보장
            ) {
                Text(actionLabel)
            }
        }
    }
}

