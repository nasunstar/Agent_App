package com.example.agent_app.ui.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.agent_app.ui.theme.Dimens

/**
 * UI 리브랜딩: 정보 카드 컴포넌트
 * 제목/요약/상태/아이콘/강조색을 포함한 재사용 가능한 카드
 * 
 * ⚠️ 로직 변경 금지: 표시 전용
 */
@Composable
fun InfoCard(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 클릭 가능 여부에 따른 스타일 구분
    val containerColor = if (onClick != null) {
        // 클릭 가능: 약간 더 진한 배경 + 프레스 효과
        val baseColor = accentColor?.copy(alpha = 0.15f)
            ?: MaterialTheme.colorScheme.surfaceContainerHighest
        if (isPressed) {
            baseColor.copy(alpha = baseColor.alpha * 0.8f)
        } else {
            baseColor
        }
    } else {
        // 클릭 불가: 기본 배경
        accentColor?.copy(alpha = 0.1f)
            ?: MaterialTheme.colorScheme.surfaceContainerLow
    }
    
    val elevation = if (onClick != null) {
        // 클릭 가능: 더 높은 elevation
        if (isPressed) Dimens.cardElevation else Dimens.cardElevation * 2
    } else {
        Dimens.cardElevation
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null, // Material 기본 리플 사용
                            onClick = onClick
                        )
                        .semantics {
                            role = Role.Button
                            contentDescription = "$title 열기"
                        }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(Dimens.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
        ) {
            // 헤더 (위쪽 여백 추가)
            Spacer(modifier = Modifier.height(Dimens.spacingXS))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSM),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconTint,
                            modifier = Modifier.size(Dimens.iconMedium)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        if (summary != null) {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "$title 열기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconSmall)
                    )
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

