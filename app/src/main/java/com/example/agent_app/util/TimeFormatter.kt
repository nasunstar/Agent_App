package com.example.agent_app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormatter {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    fun format(timestampMillis: Long): String =
        formatter.format(Instant.ofEpochMilli(timestampMillis))
}
