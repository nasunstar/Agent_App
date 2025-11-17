package com.example.agent_app.backend.services

import com.example.agent_app.backend.data.calendar.CalendarRole
import com.example.agent_app.backend.models.calendar.AddMemberRequest
import com.example.agent_app.backend.models.calendar.CalendarDetailDto
import com.example.agent_app.backend.models.calendar.CalendarEventDto
import com.example.agent_app.backend.models.calendar.CalendarListResponse
import com.example.agent_app.backend.models.calendar.CalendarMemberDto
import com.example.agent_app.backend.models.calendar.CalendarShareTokenDto
import com.example.agent_app.backend.models.calendar.CalendarSummaryDto
import com.example.agent_app.backend.models.calendar.CreateCalendarRequest
import com.example.agent_app.backend.models.calendar.CreateEventRequest
import com.example.agent_app.backend.models.calendar.CreateShareTokenRequest
import com.example.agent_app.backend.repositories.CalendarEventRecord
import com.example.agent_app.backend.repositories.CalendarMemberRecord
import com.example.agent_app.backend.repositories.CalendarShareTokenRecord
import com.example.agent_app.backend.repositories.SharedCalendarRecord
import com.example.agent_app.backend.repositories.SharedCalendarRepository
import com.example.agent_app.backend.repositories.CalendarProfileRecord
import com.example.agent_app.backend.models.calendar.ShareProfileDto
import java.time.Instant
import java.util.UUID

class SharedCalendarService(
    private val repository: SharedCalendarRepository,
) {

    fun getOrCreateShareProfile(actorEmail: String): ShareProfileDto {
        val profile = repository.getOrCreateProfile(actorEmail)
        var calendars = repository.calendarsOwnedBy(actorEmail)
            .map { it.toSummaryDto() }
        
        // 고유 캘린더가 없으면 생성 (기존 사용자 대응)
        val hasPersonalCalendar = calendars.any { it.description == "나의 고유 캘린더" }
        if (!hasPersonalCalendar) {
            val personalCalendar = repository.createCalendar(
                name = "${actorEmail.split("@")[0]}의 캘린더",
                description = "나의 고유 캘린더",
                ownerEmail = actorEmail,
            )
            repository.insertMember(personalCalendar.id, actorEmail, CalendarRole.OWNER)
            // 캘린더 목록 다시 로드
            calendars = repository.calendarsOwnedBy(actorEmail)
                .map { it.toSummaryDto() }
        }
        
        return profile.toDto(calendars)
    }

    fun getShareProfile(shareId: String): ShareProfileDto? {
        val profile = repository.findProfileByShareId(shareId) ?: return null
        val calendars = repository.calendarsOwnedBy(profile.email)
            .map { it.toSummaryDto() }
        return profile.toDto(calendars)
    }

    fun createCalendar(actorEmail: String, request: CreateCalendarRequest): CalendarDetailDto {
        val calendar = repository.createCalendar(
            name = request.name,
            description = request.description,
            ownerEmail = actorEmail,
        )
        repository.insertMember(calendar.id, actorEmail, CalendarRole.OWNER)
        val members = repository.members(calendar.id)
        return calendar.toDetailDto(members = members, events = emptyList())
    }

    fun listCalendars(actorEmail: String): CalendarListResponse {
        val calendars = repository.calendarsForMember(actorEmail)
            .map { it.toSummaryDto() }
        return CalendarListResponse(calendars)
    }

    fun getCalendar(calendarId: UUID, actorEmail: String): CalendarDetailDto? {
        if (repository.membership(calendarId, actorEmail) == null) {
            return null
        }
        val calendar = repository.findCalendar(calendarId) ?: return null
        val members = repository.members(calendarId)
        val events = repository.events(calendarId)
        return calendar.toDetailDto(members, events)
    }

    fun addMember(calendarId: UUID, actorEmail: String, request: AddMemberRequest): Boolean {
        val calendar = repository.findCalendar(calendarId) ?: return false
        val actorMembership = repository.membership(calendarId, actorEmail) ?: return false
        if (actorMembership.role != CalendarRole.OWNER) {
            return false
        }
        val existing = repository.membership(calendarId, request.email)
        if (existing == null) {
            repository.insertMember(calendarId, request.email, request.role)
        } else {
            repository.updateMemberRole(calendarId, request.email, request.role)
        }
        return true
    }

    fun createEvent(calendarId: UUID, actorEmail: String, request: CreateEventRequest): CalendarEventDto? {
        val calendar = repository.findCalendar(calendarId) ?: return null
        val membership = repository.membership(calendarId, actorEmail) ?: return null
        if (membership.role !in setOf(CalendarRole.OWNER, CalendarRole.EDITOR)) {
            return null
        }
        val event = repository.createEvent(
            calendarId = calendar.id,
            title = request.title,
            description = request.description,
            location = request.location,
            allDay = request.allDay,
            startAt = request.startAt,
            endAt = request.endAt,
            createdBy = actorEmail,
        )
        return event.toDto()
    }

    fun listEvents(calendarId: UUID, actorEmail: String): List<CalendarEventDto>? {
        if (repository.membership(calendarId, actorEmail) == null) {
            return null
        }
        return repository.events(calendarId)
            .map { it.toDto() }
    }

    fun createShareToken(
        calendarId: UUID,
        actorEmail: String,
        request: CreateShareTokenRequest,
    ): CalendarShareTokenDto? {
        val calendar = repository.findCalendar(calendarId) ?: return null
        val membership = repository.membership(calendarId, actorEmail) ?: return null
        if (membership.role != CalendarRole.OWNER) {
            return null
        }
        val token = repository.createShareToken(
            calendarId = calendar.id,
            label = request.label,
            canEdit = request.canEdit,
            expiresAt = request.expiresAt,
            createdBy = actorEmail,
        )
        return token.toDto()
    }

    fun getByShareToken(token: String): CalendarDetailDto? {
        val tokenRecord = repository.findByToken(token) ?: return null
        if (tokenRecord.expiresAt != null && tokenRecord.expiresAt.isBefore(Instant.now())) {
            return null
        }
        val calendar = repository.findCalendar(tokenRecord.calendarId) ?: return null
        val members = repository.members(calendar.id)
        val events = repository.events(calendar.id)
        return calendar.toDetailDto(members, events)
    }

    fun getCalendarByShareId(shareId: String): CalendarDetailDto? {
        val calendar = repository.findCalendarByShareId(shareId) ?: return null
        val members = repository.members(calendar.id)
        val events = repository.events(calendar.id)
        return calendar.toDetailDto(members, events)
    }

    private fun SharedCalendarRecord.toSummaryDto() = CalendarSummaryDto(
        id = id.toString(),
        name = name,
        description = description,
        ownerEmail = ownerEmail,
        shareId = shareId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun SharedCalendarRecord.toDetailDto(
        members: List<CalendarMemberRecord>,
        events: List<CalendarEventRecord>,
    ) = CalendarDetailDto(
        id = id.toString(),
        name = name,
        description = description,
        ownerEmail = ownerEmail,
        shareId = shareId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        members = members.map { it.toDto() },
        events = events.map { it.toDto() },
    )

    private fun CalendarMemberRecord.toDto() = CalendarMemberDto(
        email = email,
        role = role,
        joinedAt = joinedAt,
    )

    private fun CalendarEventRecord.toDto() = CalendarEventDto(
        id = id.toString(),
        title = title,
        description = description,
        location = location,
        allDay = allDay,
        startAt = startAt,
        endAt = endAt,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun CalendarShareTokenRecord.toDto() = CalendarShareTokenDto(
        token = token,
        label = label,
        canEdit = canEdit,
        expiresAt = expiresAt,
        createdBy = createdBy,
        createdAt = createdAt,
    )

    private fun CalendarProfileRecord.toDto(
        calendars: List<CalendarSummaryDto>,
    ) = ShareProfileDto(
        shareId = shareId,
        ownerEmail = email,
        calendars = calendars,
    )
}


