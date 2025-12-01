package com.example.agent_app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
 * 요약 위젯 - 오늘/이번주 일정 표시 (개선된 디자인)
 */
class SummaryWidget : GlanceAppWidget() {
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        android.util.Log.d("SummaryWidget", "provideGlance 시작 - widgetId: $id")
        
        // 데이터를 미리 가져오게 조회
        val todayData = try {
            withContext(Dispatchers.IO) {
                try {
                    android.util.Log.d("SummaryWidget", "오늘 데이터 조회 시작")
                    val database = AppDatabase.build(context)
                    val widgetRepository = WidgetRepository(
                        eventDao = database.eventDao(),
                        ingestItemDao = database.ingestItemDao(),
                    )
                    
                    val data = widgetRepository.getTodayItems()
                    android.util.Log.d("SummaryWidget", "오늘 데이터 조회 완료 - events: ${data.events.size}, items: ${data.dueItems.size}")
                    data
                } catch (e: Exception) {
                    android.util.Log.e("SummaryWidget", "오늘 데이터 조회 실패", e)
                    e.printStackTrace()
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SummaryWidget", "오늘 데이터 조회 중 예외", e)
            e.printStackTrace()
            null
        }
        
        val weekData = try {
            withContext(Dispatchers.IO) {
                try {
                    android.util.Log.d("SummaryWidget", "이번주 데이터 조회 시작")
                    val database = AppDatabase.build(context)
                    val widgetRepository = WidgetRepository(
                        eventDao = database.eventDao(),
                        ingestItemDao = database.ingestItemDao(),
                    )
                    
                    val data = widgetRepository.getWeekItems()
                    android.util.Log.d("SummaryWidget", "이번주 데이터 조회 완료 - events: ${data.events.size}, items: ${data.dueItems.size}")
                    data
                } catch (e: Exception) {
                    android.util.Log.e("SummaryWidget", "이번주 데이터 조회 실패", e)
                    e.printStackTrace()
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SummaryWidget", "이번주 데이터 조회 중 예외", e)
            e.printStackTrace()
            null
        }
        
        android.util.Log.d("SummaryWidget", "provideContent 시작")
        
        provideContent {
            if (todayData != null && weekData != null) {
                GlanceTheme {
                    // 카드 스타일 배경
                    val boxModifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .padding(16.dp)
                        .clickable(actionStartActivity(MainActivity::class.java))
                    
                    Box(modifier = boxModifier) {
                        Column(
                            modifier = GlanceModifier.fillMaxSize(),
                            verticalAlignment = Alignment.Top,
                            horizontalAlignment = Alignment.Start
                        ) {
                            // 헤더
                            Text(
                                text = "일정 요약",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                modifier = GlanceModifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = GlanceModifier.height(16.dp))
                            
                            // 오늘 일정 섹션
                            EventSection(
                                title = "오늘",
                                events = todayData.events,
                                dueItems = todayData.dueItems,
                                isToday = true
                            )
                            
                            Spacer(modifier = GlanceModifier.height(12.dp))
                            
                            // 이번주 일정 섹션
                            EventSection(
                                title = "이번 주",
                                events = weekData.events,
                                dueItems = weekData.dueItems,
                                isToday = false
                            )
                        }
                    }
                }
            } else {
                // 데이터 로딩 실패 시 fallback UI
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.LightGray))
                        .padding(16.dp)
                        .clickable(actionStartActivity(MainActivity::class.java)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "데이터 로딩 중...",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.Black)
                        )
                    )
                }
            }
        }
        android.util.Log.d("SummaryWidget", "provideContent 완료")
    }
    
    companion object {
        /**
         * 모든 위젯 인스턴스 업데이트
         */
        suspend fun updateAllWidgets(context: Context) = withContext(Dispatchers.IO) {
            SummaryWidget().updateAll(context)
        }
    }
}

@Composable
private fun EventSection(
    title: String,
    events: List<com.example.agent_app.data.entity.Event>,
    dueItems: List<com.example.agent_app.data.entity.IngestItem>,
    isToday: Boolean
) {
    Column(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start
    ) {
        // 섹션 제목
        Text(
            text = title,
            style = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = GlanceTheme.colors.primary
            )
        )
        
        Spacer(modifier = GlanceModifier.height(8.dp))
        
        // 일정 목록
        val totalItems = events.size + dueItems.size
        if (totalItems > 0) {
            val displayLimit = if (isToday) 3 else 2
            
            // Event 항목들 표시
            events.take(displayLimit).forEach { event ->
                EventItem(
                    title = event.title ?: "제목 없음",
                    time = event.startAt?.let { formatTime(it) },
                    isEvent = true
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
            
            // 남은 공간에 IngestItem들 표시
            val remainingSlots = displayLimit - events.size
            if (remainingSlots > 0) {
                dueItems.take(remainingSlots).forEach { item ->
                    EventItem(
                        title = item.title ?: "제목 없음",
                        time = item.dueDate?.let { formatTime(it) },
                        isEvent = false
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }
            }
            
            // 더 많은 항목이 있으면 표시
            if (totalItems > displayLimit) {
                val remainingCount = totalItems - displayLimit
                Text(
                    text = "... 더보기 ${remainingCount}개",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        } else {
            Text(
                text = "일정이 없습니다",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun EventItem(
    title: String,
    time: String?,
    isEvent: Boolean
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start
    ) {
        // 아이콘 (이벤트 vs 할 일)
        val iconText = if (isEvent) "📅" else "📝"
        Text(
            text = iconText,
            style = TextStyle(fontSize = 12.sp)
        )
        
        Spacer(modifier = GlanceModifier.width(8.dp))
        
        // 제목과 시간
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )
            
            time?.let { timeStr ->
                Text(
                    text = timeStr,
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
 * 시간 포맷팅 (HH:mm 형식)
 */
private fun formatTime(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    
    return if (minute == 0) {
        "${hour}시"
    } else {
        "${hour}:${minute.toString().padStart(2, '0')}"
    }
}

/**
 * 위젯 리시버 - 자동 업데이트 처리
 */
class SummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryWidget()
}