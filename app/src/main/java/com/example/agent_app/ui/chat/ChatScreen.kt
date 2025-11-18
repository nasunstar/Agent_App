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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent_app.R
import com.example.agent_app.ui.common.UiState
import com.example.agent_app.ui.common.components.LoadingState
import com.example.agent_app.ui.common.components.StatusIndicator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.example.agent_app.ui.theme.AgentAppTheme
import com.example.agent_app.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            ChatInput(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    if (input.isNotBlank()) {
                        viewModel.submit(input.trim())
                        input = ""
                    }
                },
                enabled = !state.isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            CurrentTimeHeader()

            ChatHistory(
                entries = state.entries,
                modifier = Modifier.weight(1f),
                onNewMessage = {
                    keyboardController?.hide()
                },
                snackbarHostState = snackbarHostState,
                failedEntryIndex = state.failedEntryIndex,
                onRetry = { index -> viewModel.retryFailedMessage(index) }
            )

            if (state.isProcessing) {
                LoadingState(
                    message = stringResource(R.string.chat_processing),
                    inline = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spacingMD, vertical = Dimens.spacingSM)
                )
            }
        }
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

@Composable
private fun ChatHistory(
    entries: List<ChatThreadEntry>,
    modifier: Modifier = Modifier,
    onNewMessage: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
    failedEntryIndex: Int? = null,
    onRetry: (Int) -> Unit = {}
) {
    val listState = rememberLazyListState()
    
    // 새 메시지가 추가되면 자동으로 스크롤
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            delay(100) // UI 업데이트 대기
            listState.animateScrollToItem(entries.size - 1)
            onNewMessage()
        }
    }
    
    // 키보드 등장 시 자동 스크롤 (IME insets 변화 감지)
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (entries.isNotEmpty() && imeBottom > 0) {
            delay(200) // 키보드 애니메이션 대기
            listState.animateScrollToItem(entries.size - 1)
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.spacingMD, vertical = Dimens.spacingSM),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD),
    ) {
        if (entries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimens.spacingXL * 2),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
                    ) {
                        Text(
                            text = "💬",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Text(
                            text = "안녕하세요! 무엇을 도와드릴까요?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "예: 이번 주 회의 일정 알려줘",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        } else {
            itemsIndexed(entries) { index, entry ->
                ChatEntryCard(
                    entry = entry,
                    snackbarHostState = snackbarHostState,
                    isFailed = failedEntryIndex == index,
                    onRetry = { onRetry(index) }
                )
            }
        }
    }
}

@Composable
private fun ChatEntryCard(
    entry: ChatThreadEntry,
    snackbarHostState: SnackbarHostState,
    isFailed: Boolean = false,
    onRetry: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spacingSM),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
    ) {
        // 사용자 질문 카드 (오른쪽 정렬, Primary Container 색상)
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .align(Alignment.End)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            clipboardManager.setText(AnnotatedString(entry.question))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("제가 메시지를 복사해두었어요.")
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(
                topStart = Dimens.cardCornerRadius,
                topEnd = Dimens.cardCornerRadius,
                bottomStart = Dimens.cardCornerRadius,
                bottomEnd = Dimens.spacingXS
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation)
        ) {
            Column(
                modifier = Modifier.padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXS)
            ) {
                Text(
                    text = stringResource(R.string.chat_question_label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = entry.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
                // 타임스탬프
                Text(
                    text = formatMessageTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = Dimens.spacingXS)
                )
            }
        }
        
        // MOA 답변 카드 (왼쪽 정렬, Surface Variant 색상)
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .align(Alignment.Start)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            clipboardManager.setText(AnnotatedString(entry.answer))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("제가 메시지를 복사해두었어요.")
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(
                topStart = Dimens.cardCornerRadius,
                topEnd = Dimens.cardCornerRadius,
                bottomStart = Dimens.spacingXS,
                bottomEnd = Dimens.cardCornerRadius
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation)
        ) {
            Column(
                modifier = Modifier.padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
            ) {
                Text(
                    text = stringResource(R.string.chat_answer_label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = entry.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )

                if (entry.context.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Dimens.spacingXS))
                    Text(
                        text = stringResource(R.string.chat_context_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    entry.context.forEach { contextItem ->
                        ContextChip(contextItem)
                    }
                }

                Text(
                    text = entry.filtersDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                
                // 타임스탬프
                Text(
                    text = formatMessageTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = Dimens.spacingXS)
                )
                
                // 실패한 메시지 재시도 버튼
                if (isFailed) {
                    Spacer(modifier = Modifier.height(Dimens.spacingXS))
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = Dimens.spacingXS)
                    ) {
                        Text(
                            text = "다시 보내기",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * 메시지 타임스탬프를 자연스러운 형식으로 포맷팅
 * - 오늘: "오후 3:21"
 * - 어제: "어제 오후 9:10"
 * - 이번 주: "월요일 오후 2:30"
 * - 그 외: "12월 15일 오후 3:21"
 */
@Composable
private fun formatMessageTimestamp(timestamp: Long): String {
    val now = Instant.now()
    val messageTime = Instant.ofEpochMilli(timestamp)
    val koreanZone = ZoneId.of("Asia/Seoul")
    
    val nowLocal = now.atZone(koreanZone)
    val messageLocal = messageTime.atZone(koreanZone)
    
    val daysDiff = ChronoUnit.DAYS.between(messageLocal.toLocalDate(), nowLocal.toLocalDate())
    
    val timeFormatter = DateTimeFormatter.ofPattern("a h:mm", java.util.Locale("ko", "KR"))
    val timeStr = messageLocal.format(timeFormatter)
    
    return when {
        daysDiff == 0L -> timeStr // 오늘
        daysDiff == 1L -> "어제 $timeStr"
        daysDiff in 2..6 -> {
            val dayOfWeek = when (messageLocal.dayOfWeek.value) {
                1 -> "월요일"
                2 -> "화요일"
                3 -> "수요일"
                4 -> "목요일"
                5 -> "금요일"
                6 -> "토요일"
                7 -> "일요일"
                else -> ""
            }
            "$dayOfWeek $timeStr"
        }
        else -> {
            val dateFormatter = DateTimeFormatter.ofPattern("M월 d일", java.util.Locale("ko", "KR"))
            "${messageLocal.format(dateFormatter)} $timeStr"
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
    }
}

// === Preview ===

@Preview(name = "빈 채팅 화면", showBackground = true)
@Composable
private fun ChatScreenEmptyPreview() {
    AgentAppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .imePadding()
            ) {
                CurrentTimeHeader()
                ChatHistory(
                    entries = emptyList(),
                    modifier = Modifier.weight(1f),
                    snackbarHostState = remember { SnackbarHostState() }
                )
                ChatInput(
                    value = "",
                    onValueChange = {},
                    onSend = {},
                    enabled = true
                )
            }
        }
    }
}

@Preview(name = "채팅 메시지 있음", showBackground = true)
@Composable
private fun ChatScreenWithMessagesPreview() {
    AgentAppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .imePadding()
            ) {
                CurrentTimeHeader()
                ChatHistory(
                    entries = listOf(
                        ChatThreadEntry(
                            question = "이번 주 회의 일정 알려줘",
                            answer = "이번 주에는 3개의 회의가 예정되어 있습니다.",
                            context = emptyList(),
                            filtersDescription = "필터: 이번 주"
                        )
                    ),
                    modifier = Modifier.weight(1f),
                    snackbarHostState = remember { SnackbarHostState() }
                )
                ChatInput(
                    value = "안녕하세요",
                    onValueChange = {},
                    onSend = {},
                    enabled = true
                )
            }
        }
    }
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // semantics 블록에서 사용할 문자열을 미리 가져옴 (@Composable 함수 내에서만 호출 가능)
    val sendButtonDescription = stringResource(R.string.chat_send_button)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingMD, vertical = Dimens.spacingXS),
            placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
            enabled = enabled,
            singleLine = false,
            maxLines = 4,
            trailingIcon = {
                IconButton(
                    onClick = onSend,
                    enabled = enabled && value.isNotBlank(),
                    modifier = Modifier
                        .minimumInteractiveComponentSize() // 최소 48dp 보장
                        .semantics {
                            role = Role.Button
                            contentDescription = sendButtonDescription
                        }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = sendButtonDescription,
                        tint = if (enabled && value.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (value.isNotBlank() && enabled) {
                        onSend()
                    }
                }
            )
        )
    }
}

@Composable
private fun CurrentTimeHeader() {
    // 현재 시간을 별도 LaunchedEffect로 분리하여 Recomposition 최적화
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
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
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
