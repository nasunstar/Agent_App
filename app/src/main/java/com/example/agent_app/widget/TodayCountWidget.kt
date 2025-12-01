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
 * 1x1 위젯 - 오늘 일정 개수 배지
 */
class TodayCountWidget : GlanceAppWidget() {
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        android.util.Log.d("TodayCountWidget", "provideGlance 시작 - widgetId: $id")
        
        val count = try {
            withContext(Dispatchers.IO) {
                try {
                    val database = AppDatabase.build(context)
                    val widgetRepository = WidgetRepository(
                        eventDao = database.eventDao(),
                        ingestItemDao = database.ingestItemDao(),
                    )
                    
                    val data = widgetRepository.getTodayItems()
                    val totalCount = data.events.size + data.dueItems.size
                    android.util.Log.d("TodayCountWidget", "오늘 일정 개수: $totalCount")
                    totalCount
                } catch (e: Exception) {
                    android.util.Log.e("TodayCountWidget", "일정 개수 조회 실패", e)
                    0
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TodayCountWidget", "일정 개수 조회 중 예외", e)
            0
        }
        
        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.primary)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = GlanceModifier.clickable(actionStartActivity(MainActivity::class.java))
                    ) {
                        Text(
                            text = "$count",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                color = GlanceTheme.colors.onPrimary
                            )
                        )
                        Text(
                            text = "오늘",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = GlanceTheme.colors.onPrimary
                            )
                        )
                    }
                    
                    // 새로고침 버튼 (우측 상단)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(24.dp)
                                .background(GlanceTheme.colors.onPrimary)
                                .clickable(actionRunCallback<RefreshTodayCountCallback>())
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↻",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    color = GlanceTheme.colors.primary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
    
    companion object {
        suspend fun updateAllWidgets(context: Context) = withContext(Dispatchers.IO) {
            TodayCountWidget().updateAll(context)
        }
    }
}

/**
 * 새로고침 콜백
 */
class RefreshTodayCountCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        TodayCountWidget().update(context, glanceId)
    }
}

/**
 * 위젯 리시버
 */
class TodayCountWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayCountWidget()
}

