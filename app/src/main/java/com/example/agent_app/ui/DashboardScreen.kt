package com.example.agent_app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent_app.data.entity.Event
import com.example.agent_app.util.TimeFormatter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 대시보드 메인 화면
 */
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToInbox: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val todayEvents = uiState.events.filter { event ->
        event.startAt != null && isToday(event.startAt!!)
    }.sortedBy { it.startAt }

    val weekEvents = uiState.events.filter { event ->
        event.startAt != null && isThisWeek(event.startAt!!)
    }.sortedBy { it.startAt }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 환영 메시지
        WelcomeHeader()
        
        // 오늘 일정 카드
        TodayScheduleCard(
            events = todayEvents,
            onViewAll = onNavigateToCalendar,
            modifier = Modifier.fillMaxWidth()
        )
        
        // 이번주 일정 카드
        WeekScheduleCard(
            events = weekEvents,
            onViewAll = onNavigateToCalendar,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun WelcomeHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "안녕하세요, 고객님!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
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
    DashboardCard(
        title = "오늘 일정",
        icon = Icons.Filled.DateRange,
        iconTint = Color(0xFF9C27B0), // 보라색
        onViewAll = onViewAll,
        modifier = modifier
    ) {
        if (events.isEmpty()) {
            Text(
                text = "오늘 예정된 일정이 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            events.take(3).forEach { event ->
                ScheduleItem(event = event)
                if (event != events.take(3).last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            if (events.size > 3) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "... 외 ${events.size - 3}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScheduleItem(event: Event) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 시간
        Text(
            text = formatTime(event.startAt),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(60.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (!event.location.isNullOrBlank()) {
                Text(
                    text = event.location ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeekScheduleCard(
    events: List<Event>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    DashboardCard(
        title = "이번주 일정",
        icon = Icons.Filled.DateRange,
        iconTint = Color(0xFF9C27B0), // 보라색
        onViewAll = onViewAll,
        modifier = modifier
    ) {
        if (events.isEmpty()) {
            Text(
                text = "이번주 예정된 일정이 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            events.take(5).forEach { event ->
                WeekScheduleItem(event = event)
                if (event != events.take(5).last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            if (events.size > 5) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "... 외 ${events.size - 5}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeekScheduleItem(event: Event) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 날짜와 시간
        Column(modifier = Modifier.width(80.dp)) {
            if (event.startAt != null) {
                Text(
                    text = formatDayOfWeek(event.startAt!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(event.startAt),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (!event.location.isNullOrBlank()) {
                Text(
                    text = event.location ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    onViewAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onViewAll != null) { onViewAll?.invoke() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (onViewAll != null) {
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = "전체 보기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // 내용
            content()
        }
    }
}

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
    val calendar = Calendar.getInstance()
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

private fun getGreetingMessage(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "좋은 아침이에요! 오늘도 화이팅하세요."
        in 12..17 -> "좋은 오후네요! 오늘 하루는 어떠신가요?"
        in 18..21 -> "좋은 저녁이에요! 오늘 하루 고생하셨어요."
        else -> "안녕하세요! 오늘도 수고하셨어요."
    }
}

