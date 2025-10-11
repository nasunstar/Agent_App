package com.example.agent_app.domain.chat.model

data class QueryFilters(
    val startTimeMillis: Long? = null,
    val endTimeMillis: Long? = null,
    val source: String? = null,
    val keywords: List<String> = emptyList(),
)
