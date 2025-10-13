package com.example.agent_app.util

import com.example.agent_app.data.dao.EventDao
import com.example.agent_app.data.entity.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Event 데이터 정리 유틸리티
 * 잘못 저장된 Event 데이터를 수정하거나 삭제합니다.
 */
class EventDataCleaner(
    private val eventDao: EventDao
) {
    
    /**
     * 잘못 저장된 Event 데이터를 정리합니다.
     * - 제목에 시간 정보가 있지만 startAt이 메일 수신 시간인 경우
     * - TimeResolver로 재해석하여 수정
     */
    suspend fun cleanEventData(): Int = withContext(Dispatchers.IO) {
        val events = eventDao.getAllEvents()
        var cleanedCount = 0
        
        events.forEach { event ->
            val title = event.title
            val body = event.body ?: ""
            
            // 제목이나 본문에 시간 정보가 있는지 확인
            if (containsTimeInfo(title) || containsTimeInfo(body)) {
                // TimeResolver로 시간 재해석
                val timeResolution = TimeResolver.resolve("$title $body")
                
                if (timeResolution != null) {
                    val newStartAt = timeResolution.timestampMillis
                    
                    // 기존 startAt과 다르면 업데이트
                    if (event.startAt != newStartAt) {
                        val updatedEvent = event.copy(startAt = newStartAt)
                        eventDao.update(updatedEvent)
                        cleanedCount++
                        
                        android.util.Log.d("EventDataCleaner", 
                            "Event 수정 - ID: ${event.id}, 제목: $title, " +
                            "기존: ${event.startAt}, 수정: $newStartAt")
                    }
                }
            }
        }
        
        android.util.Log.d("EventDataCleaner", "Event 데이터 정리 완료 - ${cleanedCount}개 수정")
        cleanedCount
    }
    
    /**
     * 텍스트에 시간 정보가 포함되어 있는지 확인
     */
    private fun containsTimeInfo(text: String): Boolean {
        val timeKeywords = listOf(
            "다음주", "이번주", "지난주", "내일", "모레", "오늘", "어제",
            "월", "일", "시", "분", "오전", "오후", "AM", "PM"
        )
        return timeKeywords.any { text.contains(it) }
    }
}
