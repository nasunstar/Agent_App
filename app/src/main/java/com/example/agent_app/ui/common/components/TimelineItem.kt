package com.example.agent_app.ui.common.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.agent_app.R
import com.example.agent_app.ui.common.getSourceBadgeText
import com.example.agent_app.ui.theme.Dimens
import com.example.agent_app.ui.theme.getSourceColor

/**
 * UI 리브랜딩: 타임라인 아이템 컴포넌트
 * 시간순 아이템을 표시하며 출처별 색상/뱃지 지원
 * 
 * ⚠️ 로직 변경 금지: 표시 전용
 */
@Composable
fun TimelineItem(
    title: String,
    time: String,
    location: String? = null,
    source: String? = null,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spacingSM),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMD),
        verticalAlignment = Alignment.Top
    ) {
        // 시간 표시 (아이콘 추가로 시각적 강조)
        Row(
            modifier = Modifier.width(70.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXS),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = "시간",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // 내용
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXS)
        ) {
            // 제목
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
            
            // 시간, 장소, 출처를 한 줄로 표시 (보조 텍스트)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!location.isNullOrBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = "장소",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 출처 뱃지
                if (source != null) {
                    val sourceColor = getSourceColor(source, isDark)
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = sourceColor.copy(alpha = 0.1f),
                        contentColor = sourceColor
                    ) {
                        Text(
                            text = stringResource(getSourceBadgeText(source)),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(
                                horizontal = Dimens.badgePadding,
                                vertical = Dimens.spacingXS
                            )
                        )
                    }
                }
            }
        }
    }
}

