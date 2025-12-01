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
 * 4x2 위젯 - 이번주/다음주 캘린더
 */
class WeeklyCalendarWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        android.util.Log.d("WeeklyCalendarWidget", "provideGlance 시작 - widgetId: $id")

        val weekData = try {
            withContext(Dispatchers.IO) {
                try {
                    val database = AppDatabase.build(context)
                    val widgetRepository = WidgetRepository(
                        eventDao = database.eventDao(),
                        ingestItemDao = database.ingestItemDao(),
                    )

                    val data = widgetRepository.getWeekItems()
                    android.util.Log.d("WeeklyCalendarWidget", "이번주 데이터 조회 완료 - events: ${data.events.size}")
                    data
                } catch (e: Exception) {
                    android.util.Log.e("WeeklyCalendarWidget", "이번주 데이터 조회 실패", e)
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeeklyCalendarWidget", "이번주 데이터 조회 중 예외", e)
            null
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
                        // 헤더 (제목 + 새로고침 버튼)
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "이번주 캘린더",
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
                                    .clickable(actionRunCallback<RefreshWeeklyCalendarCallback>())
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center,
                                content = {}
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(8.dp))

                        // 요일 헤더
                        WeekDayHeader()

                        Spacer(modifier = GlanceModifier.height(6.dp))

                        // 이번주 캘린더
                        WeekCalendarGrid(
                            events = weekData?.events ?: emptyList(),
                            isCurrentWeek = true
                        )

                        Spacer(modifier = GlanceModifier.height(8.dp))

                        // 다음주 캘린더
                        WeekCalendarGrid(
                            events = weekData?.events ?: emptyList(),
                            isCurrentWeek = false
                        )
                    }
                }
            }
        }
    }

    /**
     * 요일 헤더
     */
    @Composable
    private fun WeekDayHeader() {
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
     * 주간 캘린더 그리드
     */
    @Composable
    private fun WeekCalendarGrid(
        events: List<com.example.agent_app.data.entity.Event>,
        isCurrentWeek: Boolean
    ) {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
        
        // 이번주 또는 다음주의 시작일 계산
        if (!isCurrentWeek) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
        }
        
        // 주의 시작을 일요일로 설정
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            repeat(7) { dayIndex ->
                val dayCalendar = calendar.clone() as Calendar
                dayCalendar.add(Calendar.DAY_OF_YEAR, dayIndex)
                
                val dayOfMonth = dayCalendar.get(Calendar.DAY_OF_MONTH)
                val dayTimestamp = dayCalendar.timeInMillis
                
                // 해당 날짜의 이벤트 개수 계산
                val dayEvents = events.filter { event ->
                    event.startAt?.let { startAt ->
                        val eventCalendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
                        eventCalendar.timeInMillis = startAt
                        
                        val eventDate = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                            set(Calendar.YEAR, eventCalendar.get(Calendar.YEAR))
                            set(Calendar.MONTH, eventCalendar.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, eventCalendar.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        
                        val targetDate = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                            set(Calendar.YEAR, dayCalendar.get(Calendar.YEAR))
                            set(Calendar.MONTH, dayCalendar.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, dayCalendar.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        
                        eventDate == targetDate
                    } ?: false
                }
                
                // 오늘 날짜인지 확인
                val today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
                val isToday = dayCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                             dayCalendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(32.dp),
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
                                fontSize = 12.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) {
                                    GlanceTheme.colors.primary
                                } else {
                                    GlanceTheme.colors.onSurface
                                }
                            )
                        )
                        
                        // 이벤트 인디케이터
                        if (dayEvents.isNotEmpty()) {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Box(
                                modifier = GlanceModifier
                                    .size(6.dp)
                                    .background(GlanceTheme.colors.primary),
                                content = {}
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        suspend fun updateAllWidgets(context: Context) = withContext(Dispatchers.IO) {
            WeeklyCalendarWidget().updateAll(context)
        }
    }
}

/**
 * 새로고침 콜백
 */
class RefreshWeeklyCalendarCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        WeeklyCalendarWidget().update(context, glanceId)
    }
}

/**
 * 위젯 리시버
 */
class WeeklyCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeeklyCalendarWidget()
}
