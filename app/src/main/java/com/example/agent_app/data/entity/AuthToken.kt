package com.example.agent_app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "auth_tokens")
data class AuthToken(
    @PrimaryKey
    val provider: String,
    @ColumnInfo(name = "access_token")
    val accessToken: String,
    @ColumnInfo(name = "refresh_token")
    val refreshToken: String?,
    val scope: String?,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long?,
)
