package com.example.agent_app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "auth_tokens",
    indices = [Index(value = ["provider", "account_email"], unique = true)]
)
data class AuthTokenEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val provider: String,
    @ColumnInfo(name = "account_email")
    val accountEmail: String,
    @ColumnInfo(name = "access_token")
    val accessToken: String? = null,
    @ColumnInfo(name = "refresh_token")
    val refreshToken: String? = null,
    @ColumnInfo(name = "id_token")
    val idToken: String? = null,
    @ColumnInfo(name = "token_type")
    val tokenType: String? = null,
    @ColumnInfo(name = "scopes")
    val scopes: List<String> = emptyList(),
    @ColumnInfo(name = "expires_at")
    val expiresAt: Instant? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now()
)
