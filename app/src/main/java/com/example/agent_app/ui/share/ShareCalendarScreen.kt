package com.example.agent_app.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun ShareCalendarScreen(
    uiState: ShareCalendarUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onLoadProfile: () -> Unit,
    onSearchInputChange: (String) -> Unit,
    onSearchProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            MyShareIdSection(
                uiState = uiState,
                onLoadProfile = onLoadProfile,
                onCopyShareId = { shareId ->
                    clipboardManager.setText(AnnotatedString(shareId))
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            MyCalendarsSection(uiState)

            Spacer(modifier = Modifier.height(32.dp))

            CreateCalendarSection(
                uiState = uiState,
                onNameChange = onNameChange,
                onDescriptionChange = onDescriptionChange,
                onSubmit = onSubmit,
            )

            Spacer(modifier = Modifier.height(32.dp))

            SearchShareIdSection(
                uiState = uiState,
                onSearchInputChange = onSearchInputChange,
                onSearchProfile = onSearchProfile,
            )
        }
    }
}

@Composable
private fun MyShareIdSection(
    uiState: ShareCalendarUiState,
    onLoadProfile: () -> Unit,
    onCopyShareId: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "내 공유 ID",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = onLoadProfile,
                    enabled = !uiState.isLoadingProfile,
                ) {
                    if (uiState.isLoadingProfile) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "불러오기",
                        )
                    }
                }
            }

            if (uiState.myShareId != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = uiState.myShareId,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = { onCopyShareId(uiState.myShareId) }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "복사",
                        )
                    }
                }
                Text(
                    text = "이 ID를 공유하면 다른 사용자가 내 공유 캘린더를 조회할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "아직 공유 ID가 발급되지 않았습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = onLoadProfile,
                    enabled = !uiState.isLoadingProfile,
                ) {
                    Text("공유 ID 발급하기")
                }
            }
        }
    }
}

@Composable
private fun MyCalendarsSection(uiState: ShareCalendarUiState) {
    Text(
        text = "내 공유 캘린더",
        style = MaterialTheme.typography.titleMedium,
    )
    if (uiState.isLoadingProfile && uiState.myCalendars.isEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        CircularProgressIndicator()
    } else if (uiState.myCalendars.isEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "아직 공유 캘린더가 없습니다. 아래에서 새로 만들어 보세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.myCalendars, key = { it.id }) { calendar ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = calendar.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        calendar.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateCalendarSection(
    uiState: ShareCalendarUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Text(
        text = "새 공유 캘린더 만들기",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = "팀이나 가족과 나누고 싶은 일정 모음을 만들어 공유 ID 아래에 추가하세요.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = uiState.name,
        onValueChange = onNameChange,
        label = { Text("캘린더 이름") },
        placeholder = { Text("예: 가족 일정, 프로젝트 플래너") },
        isError = uiState.showNameValidationError,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (uiState.showNameValidationError) {
        Text(
            text = "캘린더 이름을 입력해 주세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = uiState.description,
        onValueChange = onDescriptionChange,
        label = { Text("설명 (선택)") },
        placeholder = { Text("공유 목적이나 사용 규칙을 적어보세요.") },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onSubmit,
        enabled = uiState.name.isNotBlank() && !uiState.isCreating,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (uiState.isCreating) {
            CircularProgressIndicator(
                modifier = Modifier
                    .height(20.dp)
                    .padding(end = 12.dp),
                strokeWidth = 2.dp,
            )
        }
        Text(if (uiState.isCreating) "생성 중..." else "캘린더 만들기")
    }

    uiState.lastCreatedCalendarName?.let { calendarName ->
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "\"$calendarName\" 캘린더가 추가되었습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SearchShareIdSection(
    uiState: ShareCalendarUiState,
    onSearchInputChange: (String) -> Unit,
    onSearchProfile: () -> Unit,
) {
    Text(
        text = "다른 사람의 공유 ID로 조회하기",
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = uiState.searchInput,
        onValueChange = onSearchInputChange,
        label = { Text("공유 ID 입력") },
        placeholder = { Text("예: ABCDEF1234") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = onSearchProfile,
        enabled = uiState.searchInput.isNotBlank() && !uiState.isSearching,
    ) {
        if (uiState.isSearching) {
            CircularProgressIndicator(
                modifier = Modifier
                    .height(20.dp)
                    .padding(end = 12.dp),
                strokeWidth = 2.dp,
            )
        }
        Text(if (uiState.isSearching) "찾는 중..." else "공유 캘린더 열기")
    }

    uiState.searchResult?.let { profile ->
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "소유자: ${profile.ownerEmail}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (profile.calendars.isEmpty()) {
                    Text(
                        text = "공유된 캘린더가 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "공유 중인 캘린더 ${profile.calendars.size}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    profile.calendars.forEach { calendar ->
                        Text(
                            text = "• ${calendar.name}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

