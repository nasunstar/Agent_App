package com.example.agent_app.ui.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.agent_app.ui.theme.Dimens

/**
 * UI 리브랜딩: 액션 칩 컴포넌트
 * 짧은 CTA 버튼으로 사용
 * 
 * ⚠️ 로직 변경 금지: 표시 전용
 */
@Composable
fun ActionChip(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(Dimens.chipHeight)
            .minimumInteractiveComponentSize() // 최소 48dp 보장
            .clickable { onClick() }
            .semantics {
                role = Role.Button
                contentDescription = text
            },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Dimens.chipPadding, vertical = Dimens.spacingSM),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = "액션", // 텍스트가 있으므로 null
                    modifier = Modifier.size(Dimens.iconSmall)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

