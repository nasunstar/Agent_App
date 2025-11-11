package com.example.agent_app.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ShareCalendarScreen(
    uiState: ShareCalendarUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Text(
                text = "공유 캘린더 생성",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "팀원이나 가족과 일정 요약을 나누고 싶다면 새로운 캘린더를 만들어보세요.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text("캘린더 이름") },
                placeholder = { Text("예: 가족 일정, 스터디 그룹") },
                singleLine = true,
                isError = uiState.showNameValidationError,
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

            OutlinedTextField(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                label = { Text("설명 (선택)") },
                placeholder = { Text("공유 목적이나 사용 규칙을 적어두면 좋아요.") },
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSubmit,
                enabled = uiState.name.isNotBlank() && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.isLoading) "생성 중..." else "캘린더 만들기")
            }

            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
            }

            Spacer(modifier = Modifier.height(32.dp))

            uiState.lastCreatedCalendar?.let { calendar ->
                Text(
                    text = "최근 생성: ${calendar.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } ?: Text(
                text = "곧 일정 공유 옵션이 추가될 예정입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

