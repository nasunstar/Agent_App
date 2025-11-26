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
                            events = todayData?.events ?: emptyList(),
                            dueItems = todayData?.dueItems ?: emptyList(),
                            isToday = true
                        )
                        
                        Spacer(modifier = GlanceModifier.height(12.dp))
                        
                        // 구분선
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(GlanceTheme.colors.outline),
                            content = {}
                        )
                        
                        Spacer(modifier = GlanceModifier.height(12.dp))
                        
                        // 이번주 일정 섹션
                        EventSection(
                            title = "이번 주",
                            events = weekData?.events ?: emptyList(),
                            dueItems = weekData?.dueItems ?: emptyList(),
                            isToday = false
                        )
                    }
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

/**
 * 일정 섹션 컴포넌트
 */
@Composable
fun EventSection(
    title: String,
    events: List<com.example.agent_app.data.entity.Event>,
    dueItems: List<com.example.agent_app.data.entity.IngestItem>,
    isToday: Boolean
) {
    val totalCount = events.size + dueItems.size
    val displayItems = (events.take(3) + dueItems.take(3)).take(3)
    
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
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
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    content = {
                        Text(
                            text = "$totalCount",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) {
                                    GlanceTheme.colors.onPrimaryContainer
                                } else {
                                    GlanceTheme.colors.onSecondaryContainer
                                }
                            )
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = GlanceModifier.height(8.dp))
        
        // 일정 목록
        if (displayItems.isEmpty() && events.isEmpty() && dueItems.isEmpty()) {
            Text(
                text = "예정된 일정이 없습니다",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )
        } else {
            displayItems.forEachIndexed { index, _ ->
                if (index > 0) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
                
                val event = events.getOrNull(index)
                val item = if (event == null) dueItems.getOrNull(index - events.size.coerceAtMost(3)) else null
                
                EventItem(
                    title = event?.title ?: item?.title ?: "(제목 없음)",
                    time = event?.startAt?.let { formatTime(it) },
                    isEvent = event != null
                )
            }
            
            // 더보기 표시
            val remainingCount = (events.size - 3).coerceAtLeast(0) + (dueItems.size - 3).coerceAtLeast(0)
            if (remainingCount > 0) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "... 더보기 ${remainingCount}개",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 일정 아이템 컴포넌트
 */
@Composable
fun EventItem(
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
 * 위젯 리시버 - 자동 업데이트 처리
 */
class SummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryWidget()
}
