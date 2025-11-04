package com.example.agent_app.backend.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * 관리자가 등록한 Google 계정 정보 테이블
 */
object AdminAccountsTable : Table("admin_accounts") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val accessToken = text("access_token").nullable()
    val refreshToken = text("refresh_token").nullable()
    val idToken = text("id_token").nullable()
    val scopes = text("scopes") // JSON 배열 형태로 저장
    val expiresAt = timestamp("expires_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
    
    override val primaryKey = PrimaryKey(id)
}

