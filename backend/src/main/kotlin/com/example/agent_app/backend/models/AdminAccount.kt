package com.example.agent_app.backend.models

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class AdminAccount(
    val id: Long,
    val email: String,
    val scopes: List<String>,
    @Serializable(with = InstantSerializer::class)
    val expiresAt: Instant?,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant
)

@Serializable
data class AdminAccountResponse(
    val accounts: List<AdminAccount>
)

