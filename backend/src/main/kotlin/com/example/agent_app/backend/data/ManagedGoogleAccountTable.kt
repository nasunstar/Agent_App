package com.example.agent_app.backend.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * 관리자가 연결한 Google 계정 정보 테이블
 * googleEmail과 encryptedRefreshToken을 저장합니다.
 */
object ManagedGoogleAccountTable : Table("managed_google_accounts") {
    val id = long("id").autoIncrement()
    val googleEmail = varchar("google_email", 255).uniqueIndex()
    val encryptedRefreshToken = text("encrypted_refresh_token")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
    
    override val primaryKey = PrimaryKey(id)
}

