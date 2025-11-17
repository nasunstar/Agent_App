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
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.exec
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
            // 컬럼 존재 여부 확인
            val columnExists = try {
                exec("""
                    SELECT COUNT(*) as cnt
                    FROM information_schema.columns 
                    WHERE table_name = 'shared_calendars' AND column_name = 'share_id'
                """.trimIndent()) { resultSet ->
                    if (resultSet.next()) {
                        resultSet.getInt("cnt") > 0
                    } else {
                        false
                    }
                }
            } catch (e: Exception) {
                // H2 데이터베이스이거나 정보 스키마 접근 실패 시 다른 방법 시도
                try {
                    exec("SELECT share_id FROM shared_calendars LIMIT 1") { }
                    true
                } catch (e2: Exception) {
                    false
                }
            }
            
            if (!columnExists) {
                // 컬럼을 nullable로 추가
                exec("ALTER TABLE shared_calendars ADD COLUMN share_id VARCHAR(64)")
                log.info("Added share_id column manually")
            } else {
                log.debug("share_id column already exists")
            }
        } catch (e: Exception) {
            // 테이블이 아직 없거나 다른 이유로 실패한 경우 무시
            log.debug("Could not check/add share_id column: ${e.message}")
        }
        
        // 기존 캘린더에 shareId가 없으면 생성하는 마이그레이션
        val calendarsWithoutShareId = SharedCalendarsTable.select {
            SharedCalendarsTable.shareId.isNull()
        }
        
        if (calendarsWithoutShareId.count() > 0) {
            log.info("Migrating ${calendarsWithoutShareId.count()} calendars to add shareId...")
            
            calendarsWithoutShareId.forEach { row ->
                val calendarId = row[SharedCalendarsTable.id]
                val newShareId = generateShareId()
                
                // 중복 체크 (거의 불가능하지만 안전을 위해)
                var attempts = 0
                var finalShareId = newShareId
                while (attempts < 10) {
                    val existing = SharedCalendarsTable.select {
                        SharedCalendarsTable.shareId eq finalShareId
                    }.firstOrNull()
                    
                    if (existing == null) {
                        break
                    }
                    finalShareId = generateShareId()
                    attempts++
                }
                
                SharedCalendarsTable.update({ SharedCalendarsTable.id eq calendarId }) {
                    it[SharedCalendarsTable.shareId] = finalShareId
                }
            }
            
            log.info("Migration completed: shareId added to existing calendars")
        }
        
        // unique index 추가 (컬럼이 있고 데이터가 채워진 후)
        try {
            exec("""
                CREATE UNIQUE INDEX IF NOT EXISTS shared_calendars_share_id_unique 
                ON shared_calendars(share_id) 
                WHERE share_id IS NOT NULL
            """.trimIndent())
            log.info("Created unique index on share_id")
        } catch (e: Exception) {
            log.warn("Failed to create unique index on share_id (may already exist): ${e.message}")
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

