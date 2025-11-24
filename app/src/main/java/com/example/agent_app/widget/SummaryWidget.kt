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

/**
 * 요약 위젯 - 오늘/이번주 일정 표시
 */
class SummaryWidget : GlanceAppWidget() {
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        android.util.Log.d("SummaryWidget", "provideGlance 시작 - widgetId: $id")
        
        // 데이터를 미리 가져오게 조회
        val todayItemsText = try {
            withContext(Dispatchers.IO) {
                try {
                    android.util.Log.d("SummaryWidget", "오늘 데이터 조회 시작")
                    val database = AppDatabase.build(context)
                    val widgetRepository = WidgetRepository(
                        eventDao = database.eventDao(),
                        ingestItemDao = database.ingestItemDao(),
                    )
                    
                    val todayData = widgetRepository.getTodayItems()
                    android.util.Log.d("SummaryWidget", "오늘 데이터 조회 완료 - events: ${todayData.events.size}, items: ${todayData.dueItems.size}")
                    
                    buildList {
                        if (todayData.events.isEmpty() && todayData.dueItems.isEmpty()) {
                            add("오늘 예정된 일정이 없습니다")
                        } else {
                            todayData.events.take(3).forEach { event ->
                                add("• ${event.title ?: "(제목 없음)"}")
                            }
                            if (todayData.events.size > 3) {
                                add("... 더보기 ${todayData.events.size - 3}개")
                            }
                            todayData.dueItems.take(3).forEach { item ->
                                add("• ${item.title ?: "(제목 없음)"}")
                            }
                            if (todayData.dueItems.size > 3) {
                                add("... 더보기 ${todayData.dueItems.size - 3}개")
                            }
                        }
                    }.joinToString("\n").ifEmpty { "오늘 예정된 일정이 없습니다" }
                } catch (e: Exception) {
                    android.util.Log.e("SummaryWidget", "오늘 데이터 조회 실패", e)
                    e.printStackTrace()
                    "오늘 예정된 일정이 없습니다"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SummaryWidget", "오늘 데이터 조회 중 예외", e)
            e.printStackTrace()
            "오늘 예정된 일정이 없습니다"
        }
        
        val weekItemsText = try {
            withContext(Dispatchers.IO) {
                try {
                    android.util.Log.d("SummaryWidget", "이번주 데이터 조회 시작")
                    val database = AppDatabase.build(context)
                    val widgetRepository = WidgetRepository(
                        eventDao = database.eventDao(),
                        ingestItemDao = database.ingestItemDao(),
                    )
                    
                    val weekData = widgetRepository.getWeekItems()
                    android.util.Log.d("SummaryWidget", "이번주 데이터 조회 완료 - events: ${weekData.events.size}, items: ${weekData.dueItems.size}")
                    
                    buildList {
                        if (weekData.events.isEmpty() && weekData.dueItems.isEmpty()) {
                            add("이번주 예정된 일정이 없습니다")
                        } else {
                            weekData.events.take(3).forEach { event ->
                                add("• ${event.title ?: "(제목 없음)"}")
                            }
                            if (weekData.events.size > 3) {
                                add("... 더보기 ${weekData.events.size - 3}개")
                            }
                            weekData.dueItems.take(3).forEach { item ->
                                add("• ${item.title ?: "(제목 없음)"}")
                            }
                            if (weekData.dueItems.size > 3) {
                                add("... 더보기 ${weekData.dueItems.size - 3}개")
                            }
                        }
                    }.joinToString("\n").ifEmpty { "이번주 예정된 일정이 없습니다" }
                } catch (e: Exception) {
                    android.util.Log.e("SummaryWidget", "이번주 데이터 조회 실패", e)
                    e.printStackTrace()
                    "이번주 예정된 일정이 없습니다"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SummaryWidget", "이번주 데이터 조회 중 예외", e)
            e.printStackTrace()
            "이번주 예정된 일정이 없습니다"
        }
        
        android.util.Log.d("SummaryWidget", "데이터 준비 완료 - today: ${todayItemsText.length}자, week: ${weekItemsText.length}자")
        
        android.util.Log.d("SummaryWidget", "provideContent 시작")
        try {
            provideContent {
                try {
                    GlanceTheme {
                        // 기본 modifier
                        val boxModifier = GlanceModifier
                            .fillMaxSize()
                            .background(GlanceTheme.colors.background)
                            .padding(12.dp)
                            .clickable(actionStartActivity(MainActivity::class.java))
                        
                        Box(modifier = boxModifier) {
                            Column(
                                modifier = GlanceModifier.fillMaxSize(),
                                verticalAlignment = Alignment.Top,
                                horizontalAlignment = Alignment.Start
                            ) {
                                // 오늘 일정
                                Text(
                                    text = "오늘 일정",
                                    style = TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    modifier = GlanceModifier.fillMaxWidth()
                                )
                                
                                Spacer(modifier = GlanceModifier.height(4.dp))
                                
                                // 오늘 일정 내용
                                Text(
                                    text = todayItemsText,
                                    style = TextStyle(fontSize = 11.sp),
                                    modifier = GlanceModifier.fillMaxWidth()
                                )
                                
                                Spacer(modifier = GlanceModifier.height(8.dp))
                                
                                // 구분선
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(GlanceTheme.colors.onSurfaceVariant)
                                ) {
                                    // 구분선은 modifier만으로 표시
                                }
                                
                                Spacer(modifier = GlanceModifier.height(8.dp))
                                
                                // 이번주 일정
                                Text(
                                    text = "이번주 일정",
                                    style = TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    modifier = GlanceModifier.fillMaxWidth()
                                )
                                
                                Spacer(modifier = GlanceModifier.height(4.dp))
                                
                                // 이번주 일정 내용
                                Text(
                                    text = weekItemsText,
                                    style = TextStyle(fontSize = 11.sp),
                                    modifier = GlanceModifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SummaryWidget", "provideContent 내부 오류", e)
                    // 오류 발생 시 기본 텍스트만 표시
                    GlanceTheme {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .background(GlanceTheme.colors.background)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "위젯 로드 중 오류가 발생했습니다",
                                style = TextStyle(fontSize = 12.sp)
                            )
                        }
                    }
                }
            }
            android.util.Log.d("SummaryWidget", "provideContent 완료")
        } catch (e: Exception) {
            android.util.Log.e("SummaryWidget", "provideContent 호출 실패", e)
            e.printStackTrace()
        }
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
 * 요약 컴포넌트
 */
@Composable
fun SummaryRow(label: String, eventsCount: Int, dueItemsCount: Int) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = GlanceModifier.defaultWeight()
        )
        
        if (eventsCount > 0 || dueItemsCount > 0) {
            Row(
                horizontalAlignment = Alignment.End
            ) {
                if (eventsCount > 0) {
                    Text(
                        text = "일정 $eventsCount",
                        style = TextStyle(fontSize = 11.sp),
                        modifier = GlanceModifier.padding(end = 4.dp)
                    )
                }
                if (dueItemsCount > 0) {
                    Text(
                        text = "마감 $dueItemsCount",
                        style = TextStyle(fontSize = 11.sp)
                    )
                }
            }
        } else {
            Text(
                text = "없음",
                style = TextStyle(fontSize = 11.sp)
            )
        }
    }
}

/**
 * 위젯 리시버 - 자동 업데이트 처리
 */
class SummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryWidget()
}
