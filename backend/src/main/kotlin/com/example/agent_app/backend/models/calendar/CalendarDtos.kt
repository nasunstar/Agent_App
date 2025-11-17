package com.example.agent_app.backend.models.calendar

import com.example.agent_app.backend.data.calendar.CalendarRole
import com.example.agent_app.backend.models.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class CreateCalendarRequest(
    val name: String,
    val description: String? = null,
)

@Serializable
data class CalendarSummaryDto(
    val id: String,
    val name: String,
    val description: String?,
    val ownerEmail: String,
    val shareId: String? = null,  // 기존 데이터 호환성을 위해 nullable로 변경
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
)

@Serializable
data class CalendarMemberDto(
    val email: String,
    val role: CalendarRole,
    @Serializable(with = InstantSerializer::class)
    val joinedAt: Instant,
)

@Serializable
data class CalendarEventDto(
    val id: String,
    val title: String,
    val description: String?,
    val location: String?,
    val allDay: Boolean,
    @Serializable(with = InstantSerializer::class)
    val startAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val endAt: Instant?,
    val createdBy: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
)

@Serializable
data class CalendarDetailDto(
    val id: String,
    val name: String,
    val description: String?,
    val ownerEmail: String,
    val shareId: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
    val members: List<CalendarMemberDto> = emptyList(),
    val events: List<CalendarEventDto> = emptyList(),
)

@Serializable
data class AddMemberRequest(
    val email: String,
    val role: CalendarRole = CalendarRole.VIEWER,
)

@Serializable
data class CreateEventRequest(
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val allDay: Boolean = false,
    @Serializable(with = InstantSerializer::class)
    val startAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val endAt: Instant? = null,
)

@Serializable
data class CreateShareTokenRequest(
    val label: String? = null,
    val canEdit: Boolean = false,
    @Serializable(with = InstantSerializer::class)
    val expiresAt: Instant? = null,
)

@Serializable
data class CalendarShareTokenDto(
    val token: String,
    val label: String?,
    val canEdit: Boolean,
    @Serializable(with = InstantSerializer::class)
    val expiresAt: Instant?,
    val createdBy: String?,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
)

@Serializable
data class CalendarListResponse(
    val calendars: List<CalendarSummaryDto>,
)

@Serializable
data class ShareProfileDto(
    val shareId: String,
    val ownerEmail: String,
    val calendars: List<CalendarSummaryDto>,
)


