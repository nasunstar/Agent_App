package com.example.agent_app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormatter {
    private val koreanZoneId = ZoneId.of("Asia/Seoul")
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(koreanZoneId)

    fun format(timestampMillis: Long): String =
        formatter.format(Instant.ofEpochMilli(timestampMillis))
        
    fun formatSyncTime(): String {
        val now = Instant.now()
        return formatter.format(now)
    }
}
