package com.example.agent_app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
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
 * 요약 위젯 - 오늘/이번주 할일 표시
 */
class SummaryWidget : GlanceAppWidget() {
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 데이터를 안전하게 조회
        val todayItemsText = try {
            withContext(Dispatchers.IO) {
                val database = AppDatabase.build(context)
                val widgetRepository = WidgetRepository(
                    eventDao = database.eventDao(),
                    ingestItemDao = database.ingestItemDao(),
                )
                
                val todayData = widgetRepository.getTodayItems()
                
                buildList {
                    if (todayData.events.isEmpty() && todayData.dueItems.isEmpty()) {
                        add("오늘 특별한 일정은 없습니다")
                    } else {
                        todayData.events.take(3).forEach { event ->
                            add("• ${event.title ?: "(제목 없음)"}")
                        }
                        if (todayData.events.size > 3) {
                            add("... 외 ${todayData.events.size - 3}개")
                        }
                        todayData.dueItems.take(3).forEach { item ->
                            add("• ${item.title ?: "(제목 없음)"}")
                        }
                        if (todayData.dueItems.size > 3) {
                            add("... 외 ${todayData.dueItems.size - 3}개")
                        }
                    }
                }.joinToString("\n")
            }
        } catch (e: Exception) {
            "오늘 특별한 일정은 없습니다"
        }
        
        val weekItemsText = try {
            withContext(Dispatchers.IO) {
                val database = AppDatabase.build(context)
                val widgetRepository = WidgetRepository(
                    eventDao = database.eventDao(),
                    ingestItemDao = database.ingestItemDao(),
                )
                
                val weekData = widgetRepository.getWeekItems()
                
                buildList {
                    if (weekData.events.isEmpty() && weekData.dueItems.isEmpty()) {
                        add("이번주 특별한 일정은 없습니다")
                    } else {
                        weekData.events.take(3).forEach { event ->
                            add("• ${event.title ?: "(제목 없음)"}")
                        }
                        if (weekData.events.size > 3) {
                            add("... 외 ${weekData.events.size - 3}개")
                        }
                        weekData.dueItems.take(3).forEach { item ->
                            add("• ${item.title ?: "(제목 없음)"}")
                        }
                        if (weekData.dueItems.size > 3) {
                            add("... 외 ${weekData.dueItems.size - 3}개")
                        }
                    }
                }.joinToString("\n")
            }
        } catch (e: Exception) {
            "이번주 특별한 일정은 없습니다"
        }
        
        val componentName = android.content.ComponentName(context, MainActivity::class.java)
        
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.background)
                    .clickable(actionStartActivity(componentName))
                    .padding(12)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    // 오늘 할일
                    Text(
                        text = "오늘 할일",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = TextUnit(16f, TextUnitType.Sp)
                        ),
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = GlanceModifier.height(4))
                    
                    // 오늘 할일 내용 (여러 줄을 하나의 Text로 표시)
                    Text(
                        text = todayItemsText,
                        style = TextStyle(fontSize = TextUnit(11f, TextUnitType.Sp)),
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = GlanceModifier.height(8))
                    
                    // 구분선 (Box content를 명시적으로 지정)
                    Box(
                        content = {
                            // 구분선은 modifier만으로 충분
                        },
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(1)
                            .background(GlanceTheme.colors.onSurfaceVariant)
                    )
                    
                    Spacer(modifier = GlanceModifier.height(8))
                    
                    // 이번주 할일
                    Text(
                        text = "이번주 할일",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = TextUnit(16f, TextUnitType.Sp)
                        ),
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = GlanceModifier.height(4))
                    
                    // 이번주 할일 내용 (여러 줄을 하나의 Text로 표시)
                    Text(
                        text = weekItemsText,
                        style = TextStyle(fontSize = TextUnit(11f, TextUnitType.Sp)),
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                }
            }
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
 * 요약 행 컴포넌트
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
                fontSize = TextUnit(12f, TextUnitType.Sp),
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
                        style = TextStyle(fontSize = TextUnit(11f, TextUnitType.Sp)),
                        modifier = GlanceModifier.padding(end = 4)
                    )
                }
                if (dueItemsCount > 0) {
                    Text(
                        text = "마감 $dueItemsCount",
                        style = TextStyle(fontSize = TextUnit(11f, TextUnitType.Sp))
                    )
                }
            }
        } else {
            Text(
                text = "없음",
                style = TextStyle(fontSize = TextUnit(11f, TextUnitType.Sp))
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
