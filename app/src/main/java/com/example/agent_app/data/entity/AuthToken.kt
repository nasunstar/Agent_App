package com.example.agent_app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "auth_tokens")
data class AuthToken(
    @PrimaryKey
    val provider: String,
    @ColumnInfo(name = "account_email")
    val accountEmail: String,
    @ColumnInfo(name = "access_token")
    val accessTokenKey: String?,
    @ColumnInfo(name = "refresh_token")
    val refreshTokenKey: String?,
    val scope: String?,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long?,
    @ColumnInfo(name = "server_auth_code")
    val serverAuthCode: String?,
    @ColumnInfo(name = "id_token")
    val idTokenKey: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
