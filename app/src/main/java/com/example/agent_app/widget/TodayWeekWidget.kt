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
import java.util.*

/**
 * 4x2 위젯 - 오늘 + 이번주 일정
 */
class TodayWeekWidget : GlanceAppWidget() {
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        android.util.Log.d("TodayWeekWidget", "provideGlance 시작 - widgetId: $id")
        
        val todayData = try {
            withContext(Dispatchers.IO) {
                try {
                    val database = AppDatabase.build(context)
                    val widgetRepository = WidgetRepository(
                        eventDao = database.eventDao(),
                        ingestItemDao = database.ingestItemDao(),
                    )
                    
                    val data = widgetRepository.getTodayItems()
                    android.util.Log.d("TodayWeekWidget", "오늘 데이터 조회 완료 - events: ${data.events.size}, items: ${data.dueItems.size}")
                    data
                } catch (e: Exception) {
                    android.util.Log.e("TodayWeekWidget", "오늘 데이터 조회 실패", e)
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TodayWeekWidget", "오늘 데이터 조회 중 예외", e)
            null
        }
        
        val weekData = try {
            withContext(Dispatchers.IO) {
                try {
                    val database = AppDatabase.build(context)
                    val widgetRepository = WidgetRepository(
                        eventDao = database.eventDao(),
                        ingestItemDao = database.ingestItemDao(),
                    )
                    
                    val data = widgetRepository.getWeekItems()
                    android.util.Log.d("TodayWeekWidget", "이번주 데이터 조회 완료 - events: ${data.events.size}, items: ${data.dueItems.size}")
                    data
                } catch (e: Exception) {
                    android.util.Log.e("TodayWeekWidget", "이번주 데이터 조회 실패", e)
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TodayWeekWidget", "이번주 데이터 조회 중 예외", e)
            null
        }
        
        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        // 새로고침 버튼 (우측 상단)
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .size(20.dp)
                                    .background(GlanceTheme.colors.primaryContainer)
                                    .clickable(actionRunCallback<RefreshTodayWeekCallback>())
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "↻",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = GlanceTheme.colors.onPrimaryContainer
                                    )
                                )
                            }
                        }
                        
                        Spacer(modifier = GlanceModifier.height(8.dp))
                        
                        Row(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxWidth()
                                .clickable(actionStartActivity(MainActivity::class.java)),
                            horizontalAlignment = Alignment.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                        // 오늘 일정 섹션 (왼쪽)
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            TodayWeekEventSection(
                                title = "오늘",
                                events = todayData?.events ?: emptyList(),
                                dueItems = todayData?.dueItems ?: emptyList(),
                                isToday = true
                            )
                        }
                        
                        Spacer(modifier = GlanceModifier.width(16.dp))
                        
                        // 구분선
                        Box(
                            modifier = GlanceModifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(GlanceTheme.colors.outline),
                            content = {}
                        )
                        
                        Spacer(modifier = GlanceModifier.width(16.dp))
                        
                        // 이번주 일정 섹션 (오른쪽)
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            TodayWeekEventSection(
                                title = "이번 주",
                                events = weekData?.events ?: emptyList(),
                                dueItems = weekData?.dueItems ?: emptyList(),
                                isToday = false
                            )
                        }
                        }
                    }
                }
            }
        }
    }
    
    companion object {
        suspend fun updateAllWidgets(context: Context) = withContext(Dispatchers.IO) {
            TodayWeekWidget().updateAll(context)
        }
    }
}

/**
 * 일정 섹션 컴포넌트 (TodayWeek용)
 */
@Composable
private fun TodayWeekEventSection(
    title: String,
    events: List<com.example.agent_app.data.entity.Event>,
    dueItems: List<com.example.agent_app.data.entity.IngestItem>,
    isToday: Boolean
) {
    val totalCount = events.size + dueItems.size
    val displayEvents = events.take(4)
    val displayItems = dueItems.take(4 - displayEvents.size)
    
    Column(
        modifier = GlanceModifier.fillMaxWidth()
    ) {
        // 섹션 헤더
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            
            // 카운트 배지
            if (totalCount > 0) {
                Box(
                    modifier = GlanceModifier
                        .background(
                            if (isToday) {
                                GlanceTheme.colors.primaryContainer
                            } else {
                                GlanceTheme.colors.secondaryContainer
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$totalCount",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) {
                                GlanceTheme.colors.onPrimaryContainer
                            } else {
                                GlanceTheme.colors.onSecondaryContainer
                            }
                        )
                    )
                }
            }
        }
        
        Spacer(modifier = GlanceModifier.height(12.dp))
        
        // 일정 목록
        if (displayEvents.isEmpty() && displayItems.isEmpty()) {
            Text(
                text = "예정된 일정이 없습니다",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )
        } else {
            Column(
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                // 이벤트 표시
                displayEvents.forEachIndexed { index, event ->
                    if (index > 0) {
                        Spacer(modifier = GlanceModifier.height(8.dp))
                    }
                    TodayWeekEventItem(
                        title = event.title ?: "(제목 없음)",
                        time = event.startAt?.let { formatTime(it) },
                        isEvent = true
                    )
                }
                
                // Due Items 표시
                displayItems.forEachIndexed { index, item ->
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    TodayWeekEventItem(
                        title = item.title ?: "(제목 없음)",
                        time = item.dueDate?.let { formatTime(it) },
                        isEvent = false
                    )
                }
                
                // 더보기 표시
                val remainingCount = (events.size - displayEvents.size) + (dueItems.size - displayItems.size)
                if (remainingCount > 0) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = "... 더보기 ${remainingCount}개",
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        ),
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 일정 아이템 컴포넌트 (TodayWeek용)
 */
@Composable
private fun TodayWeekEventItem(
    title: String,
    time: String?,
    isEvent: Boolean
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 인디케이터 점
        Box(
            modifier = GlanceModifier
                .size(6.dp)
                .background(
                    if (isEvent) {
                        GlanceTheme.colors.primary
                    } else {
                        GlanceTheme.colors.secondary
                    }
                ),
            content = {}
        )
        
        Spacer(modifier = GlanceModifier.width(8.dp))
        
        // 제목과 시간
        Column(
            modifier = GlanceModifier.defaultWeight()
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            
            if (time != null) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = time,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}

/**
 * 시간 포맷팅
 */
private fun formatTime(timestamp: Long): String {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
        timeInMillis = timestamp
    }
    
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    
    return if (minute == 0) {
        "${hour}시"
    } else {
        "${hour}:${minute.toString().padStart(2, '0')}"
    }
}

/**
 * 새로고침 콜백
 */
class RefreshTodayWeekCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        TodayWeekWidget().update(context, glanceId)
    }
}

/**
 * 위젯 리시버
 */
class TodayWeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWeekWidget()
}

