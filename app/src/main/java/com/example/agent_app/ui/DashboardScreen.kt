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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterialApi::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToInbox: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Gmail 앱 실행 함수
    val openGmailApp = {
        try {
            // Gmail 패키지명
            val gmailPackageName = "com.google.android.gm"
            
            // Gmail 앱 설치 여부 확인
            val packageManager = context.packageManager
            val isGmailInstalled = try {
                packageManager.getPackageInfo(gmailPackageName, PackageManager.GET_ACTIVITIES)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
            
            if (isGmailInstalled) {
                // Gmail 앱 실행
                val intent = packageManager.getLaunchIntentForPackage(gmailPackageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } else {
                    // Gmail 앱이 설치되어 있지만 실행할 수 없는 경우
                    // Play Store로 이동
                    openGmailInPlayStore(context)
                }
            } else {
                // Gmail 앱이 설치되어 있지 않으면 Play Store로 이동
                openGmailInPlayStore(context)
            }
        } catch (e: Exception) {
            // 오류 발생 시 Play Store로 이동
            openGmailInPlayStore(context)
        }
    }
    
    // onNavigateToInbox를 Gmail 앱 실행으로 변경
    val handleInboxClick = {
        openGmailApp()
    }
    // 오늘 일정: 시작일이 오늘이거나, 범위 일정이 오늘을 포함하는 경우
    val todayEvents = uiState.events.filter { event ->
        event.startAt != null && isEventIncludingToday(event)
    }.sortedBy { it.startAt }

    // 앞으로 7일 일정: 오늘을 제외한 다음 7일간의 일정
    val weekEvents = uiState.events.filter { event ->
        event.startAt != null && 
        isNext7Days(event.startAt!!) && 
        !isToday(event.startAt!!)
    }.sortedBy { it.startAt }
    
    // 다크모드 자동 감지 (공통 컴포넌트에서 처리)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val spacing = if (isLandscape) Dimens.spacingSM else Dimens.spacingMD
    
    // 오늘/이번주 모두 비었는지 확인
    val allEmpty = todayEvents.isEmpty() && weekEvents.isEmpty()
    
    // Pull-to-refresh 상태
    val isRefreshing = viewModel.isRefreshing.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                viewModel.loadClassifiedData()
            }
        }
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
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
                    onClick = handleInboxClick,
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
        
        // Pull-to-refresh 인디케이터 (LazyColumn 밖에 위치)
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
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

/**
 * 일정이 오늘을 포함하는지 확인 (범위 일정 포함)
 * 날짜 단위로 비교하여 범위 일정도 올바르게 처리
 */
private fun isEventIncludingToday(event: com.example.agent_app.data.entity.Event): Boolean {
    if (event.startAt == null) return false
    
    val calendar = Calendar.getInstance()
    val today = calendar.get(Calendar.DAY_OF_YEAR)
    val todayYear = calendar.get(Calendar.YEAR)
    
    // 시작일의 날짜
    calendar.timeInMillis = event.startAt
    val startDay = calendar.get(Calendar.DAY_OF_YEAR)
    val startYear = calendar.get(Calendar.YEAR)
    
    // 종료일의 날짜 (없으면 시작일과 동일)
    val endDay = if (event.endAt != null) {
        calendar.timeInMillis = event.endAt
        calendar.get(Calendar.DAY_OF_YEAR)
    } else {
        startDay
    }
    val endYear = if (event.endAt != null) {
        calendar.timeInMillis = event.endAt
        calendar.get(Calendar.YEAR)
    } else {
        startYear
    }
    
    // 오늘이 시작일과 종료일 사이에 있는지 확인
    // 같은 연도인 경우
    if (todayYear == startYear && todayYear == endYear) {
        return today >= startDay && today <= endDay
    }
    
    // 다른 연도인 경우 (예: 12월 30일 ~ 1월 2일)
    // 오늘이 시작일 연도인 경우
    if (todayYear == startYear) {
        return today >= startDay
    }
    
    // 오늘이 종료일 연도인 경우
    if (todayYear == endYear) {
        return today <= endDay
    }
    
    // 오늘이 시작일과 종료일 사이의 연도인 경우
    return todayYear > startYear && todayYear < endYear
}

private fun formatTime(timestamp: Long?): String {
    if (timestamp == null) return ""
    // TimeFormatter를 사용하여 한국 시간대(Asia/Seoul)로 표시
    val fullTime = TimeFormatter.format(timestamp)
    // "yyyy-MM-dd HH:mm" 형식에서 "HH:mm"만 추출
    return fullTime.split(" ").getOrNull(1) ?: ""
}

/**
 * 앞으로 7일 동안의 일정인지 확인 (오늘 제외)
 * 오늘보다 미래이고, 오늘부터 7일 후까지의 일정
 */
private fun isNext7Days(timestamp: Long): Boolean {
    val calendar = Calendar.getInstance()
    val today = calendar.timeInMillis
    
    // 7일 후 시간 계산
    calendar.add(Calendar.DAY_OF_YEAR, 7)
    val sevenDaysLater = calendar.timeInMillis
    
    // 이벤트 시간
    val eventTime = timestamp
    
    // 오늘보다는 미래이고, 7일 후 이전이어야 함
    return eventTime > today && eventTime <= sevenDaysLater
}

private fun formatDayOfWeek(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val days = arrayOf("일", "월", "화", "수", "목", "금", "토")
    
    // 오늘인지 확인
    val today = Calendar.getInstance()
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
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> stringResource(R.string.greeting_morning)
        in 12..17 -> stringResource(R.string.greeting_afternoon)
        in 18..21 -> stringResource(R.string.greeting_evening)
        else -> stringResource(R.string.greeting_night)
    }
}

/**
 * Play Store에서 Gmail 앱 설치 페이지 열기
 */
private fun openGmailInPlayStore(context: android.content.Context) {
    try {
        val gmailPackageName = "com.google.android.gm"
        val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$gmailPackageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        // Play Store 앱이 없으면 웹 브라우저로 열기
        if (playStoreIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(playStoreIntent)
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$gmailPackageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    } catch (e: Exception) {
        // 오류 발생 시 웹 브라우저로 열기
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gm")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (e2: Exception) {
            android.util.Log.e("DashboardScreen", "Gmail Play Store 열기 실패", e2)
        }
    }
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

