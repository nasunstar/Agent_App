package com.example.agent_app.backend.repositories

import com.example.agent_app.backend.data.calendar.CalendarEventsTable
import com.example.agent_app.backend.data.calendar.CalendarMembershipsTable
import com.example.agent_app.backend.data.calendar.CalendarProfilesTable
import com.example.agent_app.backend.data.calendar.CalendarRole
import com.example.agent_app.backend.data.calendar.CalendarShareTokensTable
import com.example.agent_app.backend.data.calendar.SharedCalendarsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

data class SharedCalendarRecord(
    val id: UUID,
    val name: String,
    val description: String?,
    val ownerEmail: String,
    val shareId: String?,  // 기존 데이터 호환성을 위해 nullable로 변경
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CalendarMemberRecord(
    val calendarId: UUID,
    val email: String,
    val role: CalendarRole,
    val joinedAt: Instant,
)

data class CalendarEventRecord(
    val id: UUID,
    val calendarId: UUID,
    val title: String,
    val description: String?,
    val location: String?,
    val allDay: Boolean,
    val startAt: Instant,
    val endAt: Instant?,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CalendarShareTokenRecord(
    val id: Long,
    val calendarId: UUID,
    val token: String,
    val label: String?,
    val canEdit: Boolean,
    val expiresAt: Instant?,
    val createdBy: String?,
    val createdAt: Instant,
)

data class CalendarProfileRecord(
    val id: UUID,
    val email: String,
    val shareId: String,
    val createdAt: Instant,
)

class SharedCalendarRepository {

    fun createCalendar(
        name: String,
        description: String?,
        ownerEmail: String,
    ): SharedCalendarRecord = transaction {
        val now = Instant.now()
        val id = UUID.randomUUID()
        val shareId = generateShareId()
        SharedCalendarsTable.insert {
            it[SharedCalendarsTable.id] = id
            it[SharedCalendarsTable.name] = name
            it[SharedCalendarsTable.description] = description
            it[SharedCalendarsTable.ownerEmail] = ownerEmail
            it[SharedCalendarsTable.shareId] = shareId
            it[SharedCalendarsTable.createdAt] = now
            it[SharedCalendarsTable.updatedAt] = now
        }
        SharedCalendarRecord(
            id = id,
            name = name,
            description = description,
            ownerEmail = ownerEmail,
            shareId = shareId,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun findCalendar(id: UUID): SharedCalendarRecord? = transaction {
        SharedCalendarsTable.select { SharedCalendarsTable.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toCalendarRecord()
    }

    fun findCalendarByShareId(shareId: String): SharedCalendarRecord? = transaction {
        SharedCalendarsTable.select { SharedCalendarsTable.shareId eq shareId }
            .limit(1)
            .firstOrNull()
            ?.toCalendarRecord()
    }

    fun calendarsForMember(email: String): List<SharedCalendarRecord> = transaction {
        (SharedCalendarsTable innerJoin CalendarMembershipsTable)
            .select { CalendarMembershipsTable.memberEmail eq email }
            .map { it.toCalendarRecord() }
            .distinctBy { it.id }
    }

    fun calendarsOwnedBy(email: String): List<SharedCalendarRecord> = transaction {
        SharedCalendarsTable
            .select { SharedCalendarsTable.ownerEmail eq email }
            .map { it.toCalendarRecord() }
    }

    fun insertMember(
        calendarId: UUID,
        email: String,
        role: CalendarRole,
    ): CalendarMemberRecord = transaction {
        val now = Instant.now()
        CalendarMembershipsTable.insert {
            it[CalendarMembershipsTable.calendarId] = calendarId
            it[memberEmail] = email
            it[CalendarMembershipsTable.role] = role
            it[joinedAt] = now
        }
        CalendarMemberRecord(calendarId, email, role, now)
    }

    fun updateMemberRole(
        calendarId: UUID,
        email: String,
        role: CalendarRole,
    ): Boolean = transaction {
        CalendarMembershipsTable.update(
            where = {
                (CalendarMembershipsTable.calendarId eq calendarId) and
                    (CalendarMembershipsTable.memberEmail eq email)
            }
        ) {
            it[CalendarMembershipsTable.role] = role
        } > 0
    }

    fun removeMember(
        calendarId: UUID,
        email: String,
    ): Boolean = transaction {
        CalendarMembershipsTable.deleteWhere {
            (CalendarMembershipsTable.calendarId eq calendarId) and
                (CalendarMembershipsTable.memberEmail eq email)
        } > 0
    }

    fun membership(calendarId: UUID, email: String): CalendarMemberRecord? = transaction {
        CalendarMembershipsTable
            .select {
                (CalendarMembershipsTable.calendarId eq calendarId) and
                    (CalendarMembershipsTable.memberEmail eq email)
            }
            .firstOrNull()
            ?.toMemberRecord()
    }

    fun members(calendarId: UUID): List<CalendarMemberRecord> = transaction {
        CalendarMembershipsTable
            .select { CalendarMembershipsTable.calendarId eq calendarId }
            .map { it.toMemberRecord() }
    }

    fun createEvent(
        calendarId: UUID,
        title: String,
        description: String?,
        location: String?,
        allDay: Boolean,
        startAt: Instant,
        endAt: Instant?,
        createdBy: String,
    ): CalendarEventRecord = transaction {
        val now = Instant.now()
        val id = UUID.randomUUID()
        CalendarEventsTable.insert {
            it[CalendarEventsTable.id] = id
            it[CalendarEventsTable.calendarId] = calendarId
            it[CalendarEventsTable.title] = title
            it[CalendarEventsTable.description] = description
            it[CalendarEventsTable.location] = location
            it[CalendarEventsTable.allDay] = allDay
            it[CalendarEventsTable.startAt] = startAt
            it[CalendarEventsTable.endAt] = endAt
            it[CalendarEventsTable.createdBy] = createdBy
            it[CalendarEventsTable.createdAt] = now
            it[CalendarEventsTable.updatedAt] = now
        }
        CalendarEventRecord(
            id = id,
            calendarId = calendarId,
            title = title,
            description = description,
            location = location,
            allDay = allDay,
            startAt = startAt,
            endAt = endAt,
            createdBy = createdBy,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun events(calendarId: UUID): List<CalendarEventRecord> = transaction {
        CalendarEventsTable
            .select { CalendarEventsTable.calendarId eq calendarId }
            .orderBy(CalendarEventsTable.startAt)
            .map { it.toEventRecord() }
    }

    fun createShareToken(
        calendarId: UUID,
        label: String?,
        canEdit: Boolean,
        expiresAt: Instant?,
        createdBy: String?,
    ): CalendarShareTokenRecord = transaction {
        val token = generateToken()
        val now = Instant.now()
        CalendarShareTokensTable.insert {
            it[CalendarShareTokensTable.calendarId] = calendarId
            it[CalendarShareTokensTable.token] = token
            it[CalendarShareTokensTable.label] = label
            it[CalendarShareTokensTable.canEdit] = canEdit
            it[CalendarShareTokensTable.expiresAt] = expiresAt
            it[CalendarShareTokensTable.createdBy] = createdBy
            it[CalendarShareTokensTable.createdAt] = now
        }
        CalendarShareTokensTable
            .select { CalendarShareTokensTable.token eq token }
            .first()
            .toShareTokenRecord()
    }

    fun tokens(calendarId: UUID): List<CalendarShareTokenRecord> = transaction {
        CalendarShareTokensTable
            .select { CalendarShareTokensTable.calendarId eq calendarId }
            .map { it.toShareTokenRecord() }
    }

    fun findByToken(token: String): CalendarShareTokenRecord? = transaction {
        CalendarShareTokensTable
            .select { CalendarShareTokensTable.token eq token }
            .firstOrNull()
            ?.toShareTokenRecord()
    }

    fun getOrCreateProfile(email: String): CalendarProfileRecord = transaction {
        val existing = CalendarProfilesTable
            .select { CalendarProfilesTable.email eq email }
            .firstOrNull()
            ?.toProfileRecord()
        if (existing != null) {
            existing
        } else {
            val shareId = generateShareId()
            CalendarProfilesTable.insert {
                it[CalendarProfilesTable.email] = email
                it[CalendarProfilesTable.shareId] = shareId
            }
            CalendarProfilesTable
                .select { CalendarProfilesTable.email eq email }
                .first()
                .toProfileRecord()
        }
    }

    fun findProfileByShareId(shareId: String): CalendarProfileRecord? = transaction {
        CalendarProfilesTable
            .select { CalendarProfilesTable.shareId eq shareId }
            .firstOrNull()
            ?.toProfileRecord()
    }

    private fun ResultRow.toCalendarRecord(): SharedCalendarRecord = SharedCalendarRecord(
        id = this[SharedCalendarsTable.id],
        name = this[SharedCalendarsTable.name],
        description = this[SharedCalendarsTable.description],
        ownerEmail = this[SharedCalendarsTable.ownerEmail],
        shareId = this[SharedCalendarsTable.shareId],
        createdAt = this[SharedCalendarsTable.createdAt],
        updatedAt = this[SharedCalendarsTable.updatedAt],
    )

    private fun ResultRow.toMemberRecord(): CalendarMemberRecord = CalendarMemberRecord(
        calendarId = this[CalendarMembershipsTable.calendarId],
        email = this[CalendarMembershipsTable.memberEmail],
        role = this[CalendarMembershipsTable.role],
        joinedAt = this[CalendarMembershipsTable.joinedAt],
    )

    private fun ResultRow.toEventRecord(): CalendarEventRecord = CalendarEventRecord(
        id = this[CalendarEventsTable.id],
        calendarId = this[CalendarEventsTable.calendarId],
        title = this[CalendarEventsTable.title],
        description = this[CalendarEventsTable.description],
        location = this[CalendarEventsTable.location],
        allDay = this[CalendarEventsTable.allDay],
        startAt = this[CalendarEventsTable.startAt],
        endAt = this[CalendarEventsTable.endAt],
        createdBy = this[CalendarEventsTable.createdBy],
        createdAt = this[CalendarEventsTable.createdAt],
        updatedAt = this[CalendarEventsTable.updatedAt],
    )

    private fun ResultRow.toShareTokenRecord(): CalendarShareTokenRecord = CalendarShareTokenRecord(
        id = this[CalendarShareTokensTable.id],
        calendarId = this[CalendarShareTokensTable.calendarId],
        token = this[CalendarShareTokensTable.token],
        label = this[CalendarShareTokensTable.label],
        canEdit = this[CalendarShareTokensTable.canEdit],
        expiresAt = this[CalendarShareTokensTable.expiresAt],
        createdBy = this[CalendarShareTokensTable.createdBy],
        createdAt = this[CalendarShareTokensTable.createdAt],
    )

    private fun ResultRow.toProfileRecord(): CalendarProfileRecord = CalendarProfileRecord(
        id = this[CalendarProfilesTable.id],
        email = this[CalendarProfilesTable.email],
        shareId = this[CalendarProfilesTable.shareId],
        createdAt = this[CalendarProfilesTable.createdAt],
    )

    private fun generateToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateShareId(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (0 until 10)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }
}


