package com.example.agent_app.ui.chat

/**
 * ⚠️ UI 리브랜딩 안전장치 ⚠️
 * 
 * 이 파일은 UI/UX 리브랜딩 작업 중입니다.
 * 다음 항목은 절대 변경하지 마세요:
 * - Repository/UseCase/DAO/네트워크/도메인 모델/라우팅
 * - 화면 로직과 데이터 흐름 (viewModel.uiState 사용 방식 등)
 * 
 * 변경 가능한 항목:
 * - 표시되는 텍스트 (strings.xml 사용)
 * - 컴포넌트 스타일링 (테마 토큰 사용)
 * - 아이콘/색상 표현
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent_app.R
import com.example.agent_app.ui.common.UiState
import com.example.agent_app.ui.common.components.LoadingState
import com.example.agent_app.ui.common.components.StatusIndicator
import com.example.agent_app.ui.theme.Dimens
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    
    // 현재 시간 (매 초마다 업데이트) - 한국 시간대(Asia/Seoul) 사용
    val koreanZoneId = ZoneId.of("Asia/Seoul")
    var currentTime by remember { 
        mutableStateOf(LocalDateTime.now(koreanZoneId)) 
    }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now(koreanZoneId)
            delay(1000) // 1초마다 업데이트
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 상단 시간 표시
            CurrentTimeHeader(currentTime)
            
            ChatHistory(state.entries, modifier = Modifier.weight(1f))
            ChatInput(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    viewModel.submit(input)
                    input = ""
                },
                enabled = !state.isProcessing,
            )
        }
        if (state.isProcessing) {
            LoadingState(message = stringResource(R.string.chat_processing))
        }
        if (state.error != null) {
            AlertDialog(
                onDismissRequest = viewModel::consumeError,
                title = { Text(stringResource(R.string.chat_error_title)) },
                text = { Text(state.error ?: stringResource(R.string.error_me_retry)) },
                confirmButton = {
                    TextButton(onClick = viewModel::consumeError) {
                        Text(stringResource(R.string.chat_confirm))
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatHistory(entries: List<ChatThreadEntry>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.spacingMD, vertical = Dimens.spacingSM),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD),
    ) {
        items(entries) { entry ->
            ChatEntryCard(entry)
        }
    }
}

@Composable
private fun ChatEntryCard(entry: ChatThreadEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation)
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
        ) {
            Text(
                text = stringResource(R.string.chat_question_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = entry.question, style = MaterialTheme.typography.bodyLarge)

            Text(
                text = stringResource(R.string.chat_answer_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = entry.answer, style = MaterialTheme.typography.bodyMedium)

            if (entry.context.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.chat_context_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                entry.context.forEach { contextItem ->
                    ContextChip(contextItem)
                }
            }

            Text(
                text = entry.filtersDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContextChip(item: ContextItemUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spacingXS)
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(Dimens.badgeCornerRadius)
            )
            .padding(Dimens.spacingMD),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXS),
    ) {
        Text(text = item.title, fontWeight = FontWeight.SemiBold)
        Text(
            text = item.preview,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = item.meta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.spacingMD)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
            enabled = enabled,
            singleLine = false,
            maxLines = 4,
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMD))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
            ) {
                Text(stringResource(R.string.chat_send_button))
            }
        }
    }
}

@Composable
private fun CurrentTimeHeader(currentTime: LocalDateTime) {
    // 요일을 한글로 변환
    val dayOfWeekKorean = when (currentTime.dayOfWeek.toString()) {
        "MONDAY" -> "월요일"
        "TUESDAY" -> "화요일"
        "WEDNESDAY" -> "수요일"
        "THURSDAY" -> "목요일"
        "FRIDAY" -> "금요일"
        "SATURDAY" -> "토요일"
        "SUNDAY" -> "일요일"
        else -> currentTime.dayOfWeek.toString()
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMD, vertical = Dimens.spacingSM),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📅 ${stringResource(R.string.chat_current_time_label)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSM))
            
            // 날짜 + 요일
            Text(
                text = "${currentTime.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))} ($dayOfWeekKorean)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(Dimens.spacingXS))
            
            // 시간
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
