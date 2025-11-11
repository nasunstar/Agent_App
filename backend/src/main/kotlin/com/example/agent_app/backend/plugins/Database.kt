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
        SchemaUtils.createMissingTablesAndColumns(
            AdminAccountsTable,
            ManagedGoogleAccountTable,
            SharedCalendarsTable,
            CalendarMembershipsTable,
            CalendarEventsTable,
            CalendarShareTokensTable,
            CalendarProfilesTable,
        )
    }
    
    log.info("Database configured successfully: ${databaseUrl.take(50)}...")
}

