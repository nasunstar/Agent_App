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
 * 2x2 위젯 - 오늘 일정 요약
 */
class TodaySummaryWidget : GlanceAppWidget() {
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        android.util.Log.d("TodaySummaryWidget", "provideGlance 시작 - widgetId: $id")
        
        val todayData = try {
            withContext(Dispatchers.IO) {
                try {
                    val database = AppDatabase.build(context)
                    val widgetRepository = WidgetRepository(
                        eventDao = database.eventDao(),
                        ingestItemDao = database.ingestItemDao(),
                    )
                    
                    val data = widgetRepository.getTodayItems()
                    android.util.Log.d("TodaySummaryWidget", "오늘 데이터 조회 완료 - events: ${data.events.size}, items: ${data.dueItems.size}")
                    data
                } catch (e: Exception) {
                    android.util.Log.e("TodaySummaryWidget", "오늘 데이터 조회 실패", e)
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TodaySummaryWidget", "오늘 데이터 조회 중 예외", e)
            null
        }
        
        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .clickable(actionStartActivity(MainActivity::class.java))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.Top,
                        horizontalAlignment = Alignment.Start
                    ) {
                        // 헤더
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "오늘 일정",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            
                            // 새로고침 버튼
                            Box(
                                modifier = GlanceModifier
                                    .size(20.dp)
                                    .background(GlanceTheme.colors.primaryContainer)
                                    .clickable(actionRunCallback<RefreshTodaySummaryCallback>())
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
                            
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            
                            // 개수 배지
                            val totalCount = (todayData?.events?.size ?: 0) + (todayData?.dueItems?.size ?: 0)
                            if (totalCount > 0) {
                                Box(
                                    modifier = GlanceModifier
                                        .background(GlanceTheme.colors.primaryContainer)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$totalCount",
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GlanceTheme.colors.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = GlanceModifier.height(16.dp))
                        
                        // 일정 목록 (최대 4개)
                        val displayEvents = todayData?.events?.take(4) ?: emptyList()
                        val displayItems = todayData?.dueItems?.take(4 - displayEvents.size) ?: emptyList()
                        
                        if (displayEvents.isEmpty() && displayItems.isEmpty()) {
                            // 빈 상태
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .defaultWeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "예정된 일정이 없습니다",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        color = GlanceTheme.colors.onSurfaceVariant
                                    )
                                )
                            }
                        } else {
                            Column(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                // 이벤트 표시
                                displayEvents.forEachIndexed { index, event ->
                                    if (index > 0) {
                                        Spacer(modifier = GlanceModifier.height(10.dp))
                                    }
                                    TodaySummaryEventItem(
                                        title = event.title ?: "(제목 없음)",
                                        time = event.startAt?.let { formatTime(it) },
                                        isEvent = true
                                    )
                                }
                                
                                // Due Items 표시
                                displayItems.forEachIndexed { index, item ->
                                    Spacer(modifier = GlanceModifier.height(10.dp))
                                    TodaySummaryEventItem(
                                        title = item.title ?: "(제목 없음)",
                                        time = item.dueDate?.let { formatTime(it) },
                                        isEvent = false
                                    )
                                }
                                
                                // 더보기 표시
                                val remainingCount = ((todayData?.events?.size ?: 0) - displayEvents.size) + 
                                                   ((todayData?.dueItems?.size ?: 0) - displayItems.size)
                                if (remainingCount > 0) {
                                    Spacer(modifier = GlanceModifier.height(8.dp))
                                    Text(
                                        text = "... 더보기 ${remainingCount}개",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            color = GlanceTheme.colors.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    companion object {
        suspend fun updateAllWidgets(context: Context) = withContext(Dispatchers.IO) {
            TodaySummaryWidget().updateAll(context)
        }
    }
}

/**
 * 일정 아이템 컴포넌트 (TodaySummary용)
 */
@Composable
private fun TodaySummaryEventItem(
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
                .size(8.dp)
                .background(
                    if (isEvent) {
                        GlanceTheme.colors.primary
                    } else {
                        GlanceTheme.colors.secondary
                    }
                ),
            content = {}
        )
        
        Spacer(modifier = GlanceModifier.width(10.dp))
        
        // 제목과 시간
        Column(
            modifier = GlanceModifier.defaultWeight()
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            
            if (time != null) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = time,
                    style = TextStyle(
                        fontSize = 11.sp,
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
class RefreshTodaySummaryCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        TodaySummaryWidget().update(context, glanceId)
    }
}

/**
 * 위젯 리시버
 */
class TodaySummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodaySummaryWidget()
}

