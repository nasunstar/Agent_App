package com.example.agent_app.share.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateCalendarRequest(
    val name: String,
    val description: String? = null,
)

@Serializable
data class CalendarDetailDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val ownerEmail: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val members: List<CalendarMemberDto> = emptyList(),
    val events: List<CalendarEventDto> = emptyList(),
)

@Serializable
data class CalendarMemberDto(
    val email: String? = null,
    val role: String? = null,
    val joinedAt: String? = null,
)

@Serializable
data class CalendarEventDto(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    @SerialName("allDay")
    val allDay: Boolean = false,
    val startAt: String? = null,
    val endAt: String? = null,
    val createdBy: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

