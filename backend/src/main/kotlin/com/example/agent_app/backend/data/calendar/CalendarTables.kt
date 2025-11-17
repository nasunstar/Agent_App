package com.example.agent_app.backend.data.calendar

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.util.UUID

/**
 * 공유 캘린더 모드를 위한 도메인 테이블 정의
 */
enum class CalendarRole {
    OWNER,
    EDITOR,
    VIEWER
}

object SharedCalendarsTable : Table("shared_calendars") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val name = varchar("name", 200)
    val description = text("description").nullable()
    val ownerEmail = varchar("owner_email", 255)
    val shareId = varchar("share_id", 64).uniqueIndex()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, ownerEmail)
        index(false, shareId)
    }
}

object CalendarMembershipsTable : Table("calendar_memberships") {
    val id = long("id").autoIncrement()
    val calendarId = uuid("calendar_id")
        .references(
            SharedCalendarsTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.CASCADE
        )
    val memberEmail = varchar("member_email", 255)
    val role = enumerationByName("role", 16, CalendarRole::class)
    val joinedAt = timestamp("joined_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_calendar_member_unique", calendarId, memberEmail)
        index(false, memberEmail)
    }
}

object CalendarEventsTable : Table("calendar_events") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val calendarId = uuid("calendar_id")
        .references(
            SharedCalendarsTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.CASCADE
        )
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val location = varchar("location", 255).nullable()
    val allDay = bool("all_day").default(false)
    val startAt = timestamp("start_at")
    val endAt = timestamp("end_at").nullable()
    val createdBy = varchar("created_by", 255)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, calendarId)
        index(false, startAt)
    }
}

object CalendarShareTokensTable : Table("calendar_share_tokens") {
    val id = long("id").autoIncrement()
    val calendarId = uuid("calendar_id")
        .references(
            SharedCalendarsTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.CASCADE
        )
    val token = varchar("token", 64).uniqueIndex()
    val label = varchar("label", 120).nullable()
    val canEdit = bool("can_edit").default(false)
    val expiresAt = timestamp("expires_at").nullable()
    val createdBy = varchar("created_by", 255).nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, calendarId)
        index(false, expiresAt)
    }
}

object CalendarProfilesTable : Table("calendar_profiles") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val email = varchar("email", 255).uniqueIndex()
    val shareId = varchar("share_id", 64).uniqueIndex()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

