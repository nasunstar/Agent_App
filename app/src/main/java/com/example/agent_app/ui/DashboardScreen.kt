package com.example.agent_app.ui

/**
 * ⚠️ UI 리브랜딩 안전장치 ⚠️
 * 
 * 이 파일은 UI/UX 리브랜딩 작업 중입니다.
 * 다음 항목은 절대 변경하지 마세요:
 * - Repository/UseCase/DAO/네트워크/도메인 모델/라우팅
 * - 화면 로직과 데이터 흐름 (viewModel.uiState 사용 방식 등)
 * - 이벤트 필터링/정렬 로직 (isToday, isThisWeek 등)
 * 
 * 변경 가능한 항목:
 * - 표시되는 텍스트 (strings.xml 사용)
 * - 컴포넌트 스타일링 (테마 토큰 사용)
 * - 아이콘/색상 표현
 * - 레이아웃 간격/모서리 등 시각적 요소
 */

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent_app.R
import com.example.agent_app.data.entity.Event
import com.example.agent_app.ui.common.UiState
import com.example.agent_app.ui.common.components.*
import com.example.agent_app.ui.theme.*
import com.example.agent_app.util.TimeFormatter
import java.util.*

/**
 * 대시보드 메인 화면
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToInbox: () -> Unit = {},
    onNavigateToNeedsReview: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // MOA-Performance: 불필요한 재계산 방지 - events가 변경될 때만 필터링/정렬
    val todayEvents = remember(uiState.events) {
        derivedStateOf {
            uiState.events.filter { event ->
                event.startAt != null && isToday(event.startAt!!)
            }.sortedBy { it.startAt }
        }
    }.value

    val weekEvents = remember(uiState.events) {
        derivedStateOf {
            uiState.events.filter { event ->
                event.startAt != null && isThisWeek(event.startAt!!)
            }.sortedBy { it.startAt }
        }
    }.value
    
    // MOA-Performance: configuration은 거의 변경되지 않으므로 remember로 캐싱
    val configuration = LocalConfiguration.current
    val isLandscape = remember(configuration) {
        configuration.screenWidthDp > configuration.screenHeightDp
    }
    val spacing = remember(isLandscape) {
        if (isLandscape) Dimens.spacingSM else Dimens.spacingMD
    }
    
    // MOA-Performance: allEmpty는 todayEvents와 weekEvents가 변경될 때만 재계산
    val allEmpty = remember(todayEvents, weekEvents) {
        todayEvents.isEmpty() && weekEvents.isEmpty()
    }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(
                horizontal = Dimens.spacingMD,
                vertical = spacing
            ),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        // 환영 메시지 (1인칭 화법)
        item {
            WelcomeHeader()
        }
        
        // MOA-Needs-Review: 검토 필요 배지
        val needsReviewCount = uiState.needsReviewEvents.size
        if (needsReviewCount > 0) {
            item {
                NeedsReviewBadgeCard(
                    count = needsReviewCount,
                    onClick = onNavigateToNeedsReview,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // 액션 칩 (빠른 액션) - FlowRow로 변경하여 줄바꿈 지원
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSM),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSM)
            ) {
                ActionChip(
                    text = stringResource(R.string.dashboard_actions_today_agenda),
                    icon = Icons.Filled.DateRange,
                    onClick = onNavigateToCalendar,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ActionChip(
                    text = stringResource(R.string.dashboard_actions_open_inbox),
                    icon = Icons.Filled.Email,
                    onClick = onNavigateToInbox,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
        
        // 오늘/이번주 모두 비었을 때 통합 EmptyState
        if (allEmpty) {
            item {
                EmptyState(
                    messageResId = R.string.empty_events_today,
                    icon = Icons.Filled.CalendarToday
                )
            }
        } else {
            // 오늘 일정 카드
            if (todayEvents.isNotEmpty()) {
                item {
                    TodayScheduleCard(
                        events = todayEvents,
                        onViewAll = onNavigateToCalendar,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // 이번주 일정 카드
            if (weekEvents.isNotEmpty()) {
                item {
                    WeekScheduleCard(
                        events = weekEvents,
                        onViewAll = onNavigateToCalendar,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spacingSM)
    ) {
        Text(
            text = stringResource(R.string.dashboard_greeting),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXS))
        Text(
            text = getGreetingMessage(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TodayScheduleCard(
    events: List<Event>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    InfoCard(
        title = stringResource(R.string.dashboard_title_today),
        summary = stringResource(R.string.dashboard_today_summary),
        icon = Icons.Filled.DateRange,
        iconTint = MoaPrimary, // MOA 메인 색상
        accentColor = if (isDark) MoaPrimaryDark.copy(alpha = 0.2f) else MoaPrimaryLight.copy(alpha = 0.15f), // 나뭇잎 테마 녹색 계열
        onClick = onViewAll,
        modifier = modifier
    ) {
        // events는 이미 비어있지 않음이 보장됨 (상위에서 필터링)
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSM)
        ) {
            events.take(3).forEach { event ->
                TimelineItem(
                    title = event.title,
                    time = formatTime(event.startAt),
                    location = event.location,
                    source = event.sourceType ?: "gmail"
                )
            }
            if (events.size > 3) {
                Spacer(modifier = Modifier.height(Dimens.spacingXS))
                Text(
                    text = stringResource(R.string.dashboard_more_events, events.size - 3),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ScheduleItem은 TimelineItem으로 대체됨 (제거)

@Composable
private fun WeekScheduleCard(
    events: List<Event>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    InfoCard(
        title = stringResource(R.string.dashboard_title_week),
        icon = Icons.Filled.CalendarMonth,
        iconTint = MoaPrimary, // MOA 메인 색상
        accentColor = if (isDark) MoaPrimaryDark.copy(alpha = 0.2f) else MoaPrimaryLight.copy(alpha = 0.15f), // 나뭇잎 테마 녹색 계열
        onClick = onViewAll,
        modifier = modifier
    ) {
        // events는 이미 비어있지 않음이 보장됨 (상위에서 필터링)
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSM)
        ) {
            events.take(5).forEach { event ->
                TimelineItem(
                    title = event.title,
                    time = formatTime(event.startAt),
                    location = event.location,
                    source = event.sourceType ?: "gmail"
                )
            }
            if (events.size > 5) {
                Spacer(modifier = Modifier.height(Dimens.spacingXS))
                Text(
                    text = stringResource(R.string.dashboard_more_events, events.size - 5),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// WeekScheduleItem과 DashboardCard는 공통 컴포넌트로 대체됨 (제거)

// 유틸리티 함수들
private fun isToday(timestamp: Long): Boolean {
    val calendar = Calendar.getInstance()
    val today = calendar.get(Calendar.DAY_OF_YEAR)
    val year = calendar.get(Calendar.YEAR)
    
    calendar.timeInMillis = timestamp
    val eventDay = calendar.get(Calendar.DAY_OF_YEAR)
    val eventYear = calendar.get(Calendar.YEAR)
    
    return today == eventDay && year == eventYear
}

private fun formatTime(timestamp: Long?): String {
    if (timestamp == null) return ""
    // TimeFormatter를 사용하여 한국 시간대(Asia/Seoul)로 표시
    val fullTime = TimeFormatter.format(timestamp)
    // "yyyy-MM-dd HH:mm" 형식에서 "HH:mm"만 추출
    return fullTime.split(" ").getOrNull(1) ?: ""
}

private fun isThisWeek(timestamp: Long): Boolean {
    // MOA-Timezone: KST 기준으로 주 계산
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
    val today = calendar.get(Calendar.DAY_OF_YEAR)
    val year = calendar.get(Calendar.YEAR)
    
    // 이번 주 월요일 찾기
    calendar.firstDayOfWeek = Calendar.MONDAY
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
    calendar.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
    val mondayOfWeek = calendar.get(Calendar.DAY_OF_YEAR)
    val mondayYear = calendar.get(Calendar.YEAR)
    
    // 이번 주 일요일
    calendar.add(Calendar.DAY_OF_YEAR, 6)
    val sundayOfWeek = calendar.get(Calendar.DAY_OF_YEAR)
    val sundayYear = calendar.get(Calendar.YEAR)
    
    // 이벤트 날짜 확인
    calendar.timeInMillis = timestamp
    val eventDay = calendar.get(Calendar.DAY_OF_YEAR)
    val eventYear = calendar.get(Calendar.YEAR)
    
    // 오늘보다는 미래이고, 이번 주 일요일 이전이어야 함
    val isTodayOrFuture = (eventYear == year && eventDay >= today) || eventYear > year
    val isBeforeWeekEnd = (eventYear == sundayYear && eventDay <= sundayOfWeek) || eventYear < sundayYear
    
    return isTodayOrFuture && isBeforeWeekEnd
}

private fun formatDayOfWeek(timestamp: Long): String {
    // MOA-Timezone: KST 기준으로 요일 계산
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
    calendar.timeInMillis = timestamp
    
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val days = arrayOf("일", "월", "화", "수", "목", "금", "토")
    
    // 오늘인지 확인
    val today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
    if (calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
        calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
        return "오늘"
    }
    
    // 내일인지 확인
    today.add(Calendar.DAY_OF_YEAR, 1)
    if (calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
        calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
        return "내일"
    }
    
    return days[dayOfWeek - 1] + "요일"
}

@Composable
private fun getGreetingMessage(): String {
    // MOA-Performance: 인사말은 시간대별로 캐싱 (시간이 바뀔 때만 재계산)
    val greeting = remember {
        // 현재 시간을 기반으로 초기값 설정
        val hour = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> stringResource(R.string.greeting_morning)
            in 12..17 -> stringResource(R.string.greeting_afternoon)
            in 18..21 -> stringResource(R.string.greeting_evening)
            else -> stringResource(R.string.greeting_night)
        }
    }
    return greeting
}

// === Preview ===

@Preview(name = "로딩 상태", showBackground = true)
@Composable
private fun DashboardLoadingPreview() {
    AgentAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingMD),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
        ) {
            WelcomeHeader()
            LoadingState()
        }
    }
}

@Preview(name = "빈 상태", showBackground = true)
@Composable
private fun DashboardEmptyPreview() {
    AgentAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingMD),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
        ) {
            WelcomeHeader()
            TodayScheduleCard(
                events = emptyList(),
                onViewAll = {}
            )
        }
    }
}

@Preview(name = "성공 상태 (일정 있음)", showBackground = true)
@Composable
private fun DashboardSuccessPreview() {
    AgentAppTheme {
        val sampleEvents = listOf(
            Event(
                id = 1,
                userId = 1,
                typeId = 1,
                title = "팀 미팅",
                body = null,
                startAt = System.currentTimeMillis() + 3600000, // 1시간 후
                endAt = null,
                location = "회의실 A",
                status = "confirmed",
                sourceType = "gmail",
                sourceId = "1"
            ),
            Event(
                id = 2,
                userId = 1,
                typeId = 1,
                title = "프로젝트 리뷰",
                body = null,
                startAt = System.currentTimeMillis() + 7200000, // 2시간 후
                endAt = null,
                location = null,
                status = "confirmed",
                sourceType = "ocr",
                sourceId = "2"
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingMD),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
        ) {
            WelcomeHeader()
            TodayScheduleCard(
                events = sampleEvents,
                onViewAll = {}
            )
        }
    }
}

@Preview(name = "오류 상태", showBackground = true)
@Composable
private fun DashboardErrorPreview() {
    AgentAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingMD),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
        ) {
            WelcomeHeader()
            StatusIndicator(
                state = UiState.Error,
                message = stringResource(R.string.error_me_retry)
            )
        }
    }
}

