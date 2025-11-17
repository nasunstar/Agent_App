package com.example.agent_app.backend.plugins

import io.ktor.server.application.*
import java.net.URI
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.example.agent_app.backend.data.AdminAccountsTable
import com.example.agent_app.backend.data.ManagedGoogleAccountTable
import com.example.agent_app.backend.data.calendar.CalendarEventsTable
import com.example.agent_app.backend.data.calendar.CalendarMembershipsTable
import com.example.agent_app.backend.data.calendar.CalendarShareTokensTable
import com.example.agent_app.backend.data.calendar.SharedCalendarsTable
import com.example.agent_app.backend.data.calendar.CalendarProfilesTable
import com.example.agent_app.backend.config.ConfigLoader
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.security.SecureRandom

fun Application.configureDatabase() {
    // 데이터베이스 URL을 환경 변수에서 읽기
    // 프로덕션: DATABASE_URL (PostgreSQL)
    // 로컬 개발: DATABASE_URL이 없으면 H2 사용
    val databaseUrl = System.getenv("DATABASE_URL")
        ?: ConfigLoader.getProperty("DATABASE_URL")
        ?: "jdbc:h2:./build/admin_accounts_db;DB_CLOSE_DELAY=-1"
    
    val database = when {
        // PostgreSQL (Railway, Render 등에서 제공)
        databaseUrl.startsWith("jdbc:postgresql://") || databaseUrl.startsWith("postgresql://") -> {
            if (databaseUrl.startsWith("jdbc:postgresql://")) {
                Database.connect(
                    url = databaseUrl,
                    driver = "org.postgresql.Driver"
                )
            } else {
                val uri = URI(databaseUrl)
                val userInfo = uri.userInfo ?: throw IllegalArgumentException("DATABASE_URL must include credentials.")
                val (username, password) = userInfo.split(":", limit = 2).let {
                    val user = it.getOrNull(0) ?: ""
                    val pass = it.getOrNull(1) ?: ""
                    user to pass
                }
                val query = uri.rawQuery?.let { "?$it" } ?: ""
                val portPart = if (uri.port != -1) ":${uri.port}" else ""
                val jdbcUrl = "jdbc:postgresql://${uri.host}$portPart${uri.path}$query"

                Database.connect(
                    url = jdbcUrl,
                    driver = "org.postgresql.Driver",
                    user = username,
                    password = password
                )
            }
        }
        // H2 (로컬 개발용)
        else -> {
            Database.connect(
                url = databaseUrl,
                driver = "org.h2.Driver"
            )
        }
    }
    
    // 테이블 생성 (없으면 자동 생성)
    transaction(database) {
        // 먼저 다른 테이블들 생성
        SchemaUtils.createMissingTablesAndColumns(
            AdminAccountsTable,
            ManagedGoogleAccountTable,
            CalendarMembershipsTable,
            CalendarEventsTable,
            CalendarShareTokensTable,
            CalendarProfilesTable,
        )
        
        // SharedCalendarsTable은 별도로 처리 (share_id 컬럼 마이그레이션)
        // 먼저 테이블 생성 시도
        try {
            SchemaUtils.createMissingTablesAndColumns(SharedCalendarsTable)
        } catch (e: Exception) {
            log.warn("Failed to auto-create share_id column: ${e.message}")
        }
        
        // 컬럼이 없으면 수동으로 추가 시도 (기존 데이터 호환)
        // PostgreSQL과 H2 모두 지원하는 방식으로 컬럼 존재 여부 확인 후 추가
        try {
            // 컬럼 존재 여부 확인 - 간단하게 select 시도로 확인
            val columnExists = try {
                SharedCalendarsTable.select { }.limit(1).firstOrNull()
                // 테이블이 존재하면 shareId 컬럼 접근 시도
                try {
                    SharedCalendarsTable.select { SharedCalendarsTable.shareId.isNull() }.limit(1).firstOrNull()
                    true
                } catch (e: Exception) {
                    false
                }
            } catch (e: Exception) {
                // 테이블이 아직 없음
                false
            }
            
            if (!columnExists) {
                // 컬럼을 nullable로 추가 - Exposed의 SchemaUtils 사용
                try {
                    SchemaUtils.createMissingTablesAndColumns(SharedCalendarsTable)
                    log.info("Added share_id column via SchemaUtils")
                } catch (e: Exception) {
                    // SchemaUtils로 실패하면 raw SQL 시도
                    try {
                        val connection = TransactionManager.current().connection
                        val statement = connection.createStatement()
                        statement.executeUpdate("ALTER TABLE shared_calendars ADD COLUMN share_id VARCHAR(64)")
                        statement.close()
                        log.info("Added share_id column manually via raw SQL")
                    } catch (e2: Exception) {
                        log.debug("Could not add share_id column: ${e2.message}")
                    }
                }
            } else {
                log.debug("share_id column already exists")
            }
        } catch (e: Exception) {
            // 테이블이 아직 없거나 다른 이유로 실패한 경우 무시
            log.debug("Could not check/add share_id column: ${e.message}")
        }
        
        // 기존 캘린더에 shareId가 없으면 생성하는 마이그레이션
        try {
            val calendarsWithoutShareId = SharedCalendarsTable.select {
                SharedCalendarsTable.shareId.isNull()
            }
            
            val count = calendarsWithoutShareId.count().toInt()
            if (count > 0) {
                log.info("Migrating $count calendars to add shareId...")
                
                calendarsWithoutShareId.forEach { row ->
                    val calendarId = row[SharedCalendarsTable.id]
                    var newShareId = generateShareId()
                    
                    // 중복 체크 (거의 불가능하지만 안전을 위해)
                    var attempts = 0
                    while (attempts < 10) {
                        val existing = SharedCalendarsTable.select {
                            SharedCalendarsTable.shareId eq newShareId
                        }.firstOrNull()
                        
                        if (existing == null) {
                            break
                        }
                        newShareId = generateShareId()
                        attempts++
                    }
                    
                    SharedCalendarsTable.update({ SharedCalendarsTable.id eq calendarId }) {
                        it[SharedCalendarsTable.shareId] = newShareId
                    }
                }
                
                log.info("Migration completed: shareId added to existing calendars")
            }
        } catch (e: Exception) {
            log.warn("Could not migrate calendars: ${e.message}")
        }
        
        // unique index 추가 (컬럼이 있고 데이터가 채워진 후)
        try {
            val connection = TransactionManager.current().connection
            val statement = connection.createStatement()
            try {
                statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS shared_calendars_share_id_unique 
                    ON shared_calendars(share_id) 
                    WHERE share_id IS NOT NULL
                """.trimIndent())
                log.info("Created unique index on share_id")
            } catch (e: Exception) {
                // PostgreSQL에서는 IF NOT EXISTS를 지원하지만, H2나 다른 DB에서는 다를 수 있음
                log.warn("Failed to create unique index on share_id (may already exist): ${e.message}")
            } finally {
                statement.close()
            }
        } catch (e: Exception) {
            log.warn("Could not create unique index on share_id: ${e.message}")
        }
    }
    
    log.info("Database configured successfully: ${databaseUrl.take(50)}...")
}

private fun generateShareId(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val random = SecureRandom()
    return (0 until 10)
        .map { chars[random.nextInt(chars.length)] }
        .joinToString("")
}

