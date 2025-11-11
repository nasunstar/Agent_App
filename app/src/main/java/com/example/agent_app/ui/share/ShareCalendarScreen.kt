package com.example.agent_app.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ShareCalendarScreen(
    uiState: ShareCalendarUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onLoadProfile: () -> Unit,
    onSearchInputChange: (String) -> Unit,
    onSearchProfile: () -> Unit,
    onMyCalendarClick: (String) -> Unit,
    onDismissPreview: () -> Unit,
    onApplyInternalData: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                MyShareIdSection(
                    uiState = uiState,
                    onLoadProfile = onLoadProfile,
                    onCopyShareId = { shareId ->
                        clipboardManager.setText(AnnotatedString(shareId))
                    },
                )
            }
            item {
                MyCalendarsSection(
                    uiState = uiState,
                    onCalendarClick = onMyCalendarClick,
                )
            }
            item {
                CreateCalendarSection(
                    uiState = uiState,
                    onNameChange = onNameChange,
                    onDescriptionChange = onDescriptionChange,
                    onSubmit = onSubmit,
                )
            }
            item {
                SearchShareIdSection(
                    uiState = uiState,
                    onSearchInputChange = onSearchInputChange,
                    onSearchProfile = onSearchProfile,
                )
            }
        }
    }

    val preview = uiState.myCalendarPreview
    if (preview != null || uiState.isLoadingMyCalendarPreview) {
        MyCalendarPreviewDialog(
            calendar = preview,
            isLoading = uiState.isLoadingMyCalendarPreview,
            isSyncing = uiState.isSyncingInternalEvents,
            onDismiss = onDismissPreview,
            onApplyInternalData = { preview?.id?.let(onApplyInternalData) },
        )
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
private fun MyCalendarsSection(
    uiState: ShareCalendarUiState,
    onCalendarClick: (String) -> Unit,
) {
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
                .heightIn(min = 0.dp, max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.myCalendars, key = { it.id }) { calendar ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    onClick = { onCalendarClick(calendar.id) },
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
private fun MyCalendarPreviewDialog(
    calendar: com.example.agent_app.share.model.CalendarDetailDto?,
    isLoading: Boolean,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onApplyInternalData: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onApplyInternalData,
                enabled = calendar != null && !isLoading && !isSyncing,
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(16.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text("내 일정 공유")
            }
        },
        title = {
            Text("공유 캘린더 상세")
        },
        text = {
            if (isLoading && calendar == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("캘린더 정보를 불러오는 중입니다...")
                }
            } else if (calendar != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = calendar.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    calendar.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = "멤버 ${calendar.members.size}명, 일정 ${calendar.events.size}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SharedCalendarMonthView(events = calendar.events)
                    if (isSyncing) {
                        Text(
                            text = "내부 일정을 공유하는 중입니다...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            } else {
                Text("캘린더 정보를 찾지 못했습니다.")
            }
        }
    )
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

@Composable
private fun SharedCalendarMonthView(
    events: List<com.example.agent_app.share.model.CalendarEventDto>,
) {
    val zone = remember { ZoneId.systemDefault() }
    val parsedEvents = remember(events) {
        events.mapNotNull { event ->
            val startInstant = event.startAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val startDateTime = startInstant.atZone(zone)
            val endTime = event.endAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?.atZone(zone)?.toLocalTime()

            SharedCalendarEventInfo(
                title = event.title ?: "제목 없는 일정",
                description = event.description,
                date = startDateTime.toLocalDate(),
                startTime = startDateTime.toLocalTime(),
                endTime = endTime,
                location = event.location,
            )
        }
    }

    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(selectedMonth) {
        selectedDate = selectedMonth.atDay(1)
    }

    val eventsByDate = remember(parsedEvents) { parsedEvents.groupBy { it.date } }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "이전 달")
            }
            Text(
                text = selectedMonth.format(DateTimeFormatter.ofPattern("yyyy년 MM월")),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "다음 달")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach { day ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when (day) {
                            "일" -> Color.Red
                            "토" -> Color.Blue
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }

        val firstDayOfMonth = selectedMonth.atDay(1)
        val startDate = firstDayOfMonth.minusDays(firstDayOfMonth.dayOfWeek.value.toLong() % 7)
        val allDates = remember(selectedMonth) { List(42) { startDate.plusDays(it.toLong()) } }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            allDates.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    week.forEach { date ->
                        val isCurrentMonth = date.month == selectedMonth.month
                        val isSelected = date == selectedDate
                        val hasEvents = eventsByDate.containsKey(date)
                        val isToday = date == LocalDate.now()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                                .clickable { selectedDate = date }
                                .height(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .height(40.dp)
                                        .widthIn(min = 40.dp)
                                        .background(
                                            color = if (isToday) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(50),
                                        )
                                )
                            }

                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    isToday && isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )

                            if (hasEvents) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 4.dp)
                                        .height(6.dp)
                                        .widthIn(min = 6.dp)
                                        .background(
                                            MaterialTheme.colorScheme.secondary,
                                            shape = RoundedCornerShape(50),
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

    val selectedEvents = eventsByDate[selectedDate] ?: emptyList()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectedEvents.isEmpty()) {
            Text(
                text = "선택한 날짜에 일정이 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            selectedEvents.sortedBy { it.startTime }.forEach { event ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val timeRange = buildString {
                            event.startTime?.let {
                                append(it.format(DateTimeFormatter.ofPattern("HH:mm")))
                            }
                            if (event.endTime != null) {
                                append(" - ")
                                append(event.endTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                            }
                        }
                        if (timeRange.isNotEmpty()) {
                            Text(
                                text = timeRange,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        event.location?.takeIf { it.isNotBlank() }?.let { location ->
                            Text(
                                text = "장소: $location",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        event.description?.takeIf { it.isNotBlank() }?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
}

private data class SharedCalendarEventInfo(
    val title: String,
    val description: String?,
    val date: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val location: String?,
)

