package com.example.agent_app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "auth_tokens",
    primaryKeys = ["provider", "email"]
)
data class AuthToken(
    val provider: String,
    @ColumnInfo(name = "email")
    val email: String = "", // Google 계정 이메일 (여러 계정 구분용, 빈 문자열 = 기본 계정)
    @ColumnInfo(name = "access_token")
    val accessToken: String,
    @ColumnInfo(name = "refresh_token")
    val refreshToken: String?,
    val scope: String?,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long?,
)
