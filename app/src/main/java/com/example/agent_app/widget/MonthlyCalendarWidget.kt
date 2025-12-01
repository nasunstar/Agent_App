package com.example.agent_app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.agent_app.data.db.AppDatabase
import com.example.agent_app.data.repo.WidgetRepository
import com.example.agent_app.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 4x4 위젯 - 이번달 캘린더
 */
class MonthlyCalendarWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        android.util.Log.d("MonthlyCalendarWidget", "provideGlance 시작 - widgetId: $id")

        val monthData = try {
            withContext(Dispatchers.IO) {
                try {
                    val database = AppDatabase.build(context)
                    val widgetRepository = WidgetRepository(
                        eventDao = database.eventDao(),
                        ingestItemDao = database.ingestItemDao(),
                    )

                    // 이번달 전체 이벤트 조회
                    val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
                    val startOfMonth = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                        set(Calendar.YEAR, calendar.get(Calendar.YEAR))
                        set(Calendar.MONTH, calendar.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    val endOfMonth = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                        set(Calendar.YEAR, calendar.get(Calendar.YEAR))
                        set(Calendar.MONTH, calendar.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis

                    val events = database.eventDao().getEventsBetween(startOfMonth, endOfMonth)
                    android.util.Log.d("MonthlyCalendarWidget", "이번달 데이터 조회 완료 - events: ${events.size}")
                    events
                } catch (e: Exception) {
                    android.util.Log.e("MonthlyCalendarWidget", "이번달 데이터 조회 실패", e)
                    emptyList()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MonthlyCalendarWidget", "이번달 데이터 조회 중 예외", e)
            emptyList()
        }

        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .clickable(actionStartActivity(MainActivity::class.java))
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        // 헤더 (월 제목 + 새로고침 버튼)
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
                            val monthName = SimpleDateFormat("yyyy년 M월", Locale.KOREAN).format(calendar.time)
                            
                            Text(
                                text = monthName,
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                modifier = GlanceModifier.defaultWeight()
                            )

                            // 새로고침 버튼
                            Box(
                                modifier = GlanceModifier
                                    .size(18.dp)
                                    .background(GlanceTheme.colors.primaryContainer)
                                    .clickable(actionRunCallback<RefreshMonthlyCalendarCallback>())
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center,
                                content = {}
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(8.dp))

                        // 요일 헤더
                        MonthDayHeader()

                        Spacer(modifier = GlanceModifier.height(6.dp))

                        // 월간 캘린더 그리드
                        MonthCalendarGrid(events = monthData)
                    }
                }
            }
        }
    }

    /**
     * 요일 헤더
     */
    @Composable
    private fun MonthDayHeader() {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
            dayNames.forEach { dayName ->
                Box(
                    modifier = GlanceModifier.defaultWeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayName,
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }

    /**
     * 월간 캘린더 그리드
     */
    @Composable
    private fun MonthCalendarGrid(events: List<com.example.agent_app.data.entity.Event>) {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        
        // 이번달 1일
        val firstDayOfMonth = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        
        // 1일의 요일 (일요일 = 1, 월요일 = 2, ..., 토요일 = 7)
        val firstDayOfWeek = firstDayOfMonth.get(Calendar.DAY_OF_WEEK)
        
        // 이번달 마지막 날
        val lastDayOfMonth = firstDayOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        // 캘린더 시작일 (이전달 마지막 주 포함)
        val startCalendar = firstDayOfMonth.clone() as Calendar
        startCalendar.add(Calendar.DAY_OF_MONTH, -(firstDayOfWeek - 1))
        
        Column(
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            // 최대 6주 표시
            repeat(6) { weekIndex ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    repeat(7) { dayIndex ->
                        val dayCalendar = startCalendar.clone() as Calendar
                        dayCalendar.add(Calendar.DAY_OF_YEAR, weekIndex * 7 + dayIndex)
                        
                        val dayOfMonth = dayCalendar.get(Calendar.DAY_OF_MONTH)
                        val dayMonth = dayCalendar.get(Calendar.MONTH)
                        val isCurrentMonth = dayMonth == month
                        
                        // 해당 날짜의 이벤트 개수 계산
                        val dayEvents = if (isCurrentMonth) {
                            events.filter { event ->
                                event.startAt?.let { startAt ->
                                    val eventCalendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
                                    eventCalendar.timeInMillis = startAt
                                    
                                    eventCalendar.get(Calendar.YEAR) == dayCalendar.get(Calendar.YEAR) &&
                                    eventCalendar.get(Calendar.MONTH) == dayCalendar.get(Calendar.MONTH) &&
                                    eventCalendar.get(Calendar.DAY_OF_MONTH) == dayCalendar.get(Calendar.DAY_OF_MONTH)
                                } ?: false
                            }
                        } else {
                            emptyList()
                        }
                        
                        // 오늘 날짜인지 확인
                        val today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
                        val isToday = dayCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                     dayCalendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                        
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .height(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 날짜 숫자
                                Text(
                                    text = dayOfMonth.toString(),
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isToday -> GlanceTheme.colors.primary
                                            isCurrentMonth -> GlanceTheme.colors.onSurface
                                            else -> GlanceTheme.colors.onSurfaceVariant
                                        }
                                    )
                                )
                                
                                // 이벤트 인디케이터
                                if (dayEvents.isNotEmpty() && isCurrentMonth) {
                                    Spacer(modifier = GlanceModifier.height(1.dp))
                                    Box(
                                        modifier = GlanceModifier
                                            .size(4.dp)
                                            .background(GlanceTheme.colors.primary),
                                        content = {}
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 마지막 주가 끝나면 중단
                val weekEndCalendar = startCalendar.clone() as Calendar
                weekEndCalendar.add(Calendar.DAY_OF_YEAR, (weekIndex + 1) * 7 - 1)
                if (weekEndCalendar.get(Calendar.MONTH) != month && weekIndex >= 4) {
                    break
                }
            }
        }
    }

    companion object {
        suspend fun updateAllWidgets(context: Context) = withContext(Dispatchers.IO) {
            MonthlyCalendarWidget().updateAll(context)
        }
    }
}

/**
 * 새로고침 콜백
 */
class RefreshMonthlyCalendarCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        MonthlyCalendarWidget().update(context, glanceId)
    }
}

/**
 * 위젯 리시버
 */
class MonthlyCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthlyCalendarWidget()
}
