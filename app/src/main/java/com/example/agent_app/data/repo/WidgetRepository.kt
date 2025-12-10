package com.example.agent_app.data.repo

import com.example.agent_app.data.dao.EventDao
import com.example.agent_app.data.dao.IngestItemDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 위젯에서 사용할 데이터를 조회하는 Repository
 */
class WidgetRepository(
    private val eventDao: EventDao,
    private val ingestItemDao: IngestItemDao,
) {
    
    /**
     * 오늘의 일정 및 마감일 조회
     */
    suspend fun getTodayItems(): WidgetData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfToday = calendar.timeInMillis
        
        val events = eventDao.searchByTimeRange(startOfToday, endOfToday - 1, limit = 20)
        val items = ingestItemDao.getByTimestampRange(startOfToday, endOfToday - 1)
            .filter { it.dueDate != null && it.dueDate!! >= startOfToday && it.dueDate!! < endOfToday }
        
        WidgetData(
            events = events,
            dueItems = items,
            period = "오늘"
        )
    }
    
    /**
     * 이번 주의 일정 및 마감일 조회 (일요일 ~ 토요일 기준)
     */
    suspend fun getWeekItems(): WidgetData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
        }
        
        // 이번주 일요일 찾기 (현재 날짜에서 일요일로 이동)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=일요일, 7=토요일
        val daysFromSunday = if (dayOfWeek == Calendar.SUNDAY) 0 else dayOfWeek - Calendar.SUNDAY
        calendar.add(Calendar.DAY_OF_YEAR, -daysFromSunday)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis
        
        // 이번주 토요일 23:59:59
        calendar.add(Calendar.DAY_OF_YEAR, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfWeek = calendar.timeInMillis
        
        val events = eventDao.searchByTimeRange(startOfWeek, endOfWeek, limit = 50)
        val items = ingestItemDao.getByTimestampRange(startOfWeek, endOfWeek)
            .filter { it.dueDate != null && it.dueDate!! >= startOfWeek && it.dueDate!! <= endOfWeek }
        
        WidgetData(
            events = events,
            dueItems = items,
            period = "이번 주"
        )
    }
    
    /**
     * 이번 달의 일정 및 마감일 조회
     */
    suspend fun getMonthItems(): WidgetData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = calendar.timeInMillis
        
        calendar.add(Calendar.MONTH, 1)
        val endOfMonth = calendar.timeInMillis
        
        val events = eventDao.searchByTimeRange(startOfMonth, endOfMonth - 1, limit = 100)
        val items = ingestItemDao.getByTimestampRange(startOfMonth, endOfMonth - 1)
            .filter { it.dueDate != null && it.dueDate!! >= startOfMonth && it.dueDate!! < endOfMonth }
        
        WidgetData(
            events = events,
            dueItems = items,
            period = "이번 달"
        )
    }
    
    /**
     * 다가오는 일정 조회 (내일부터 3일 이내)
     */
    suspend fun getUpcomingEvents(): List<com.example.agent_app.data.entity.Event> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1) // 내일부터
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_YEAR, 3) // 3일 후까지
        val endTime = calendar.timeInMillis
        
        try {
            eventDao.getUpcomingEvents(startTime, endTime, limit = 10)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * 위젯 데이터 모델
 */
data class WidgetData(
    val events: List<com.example.agent_app.data.entity.Event>,
    val dueItems: List<com.example.agent_app.data.entity.IngestItem>,
    val period: String
)

