package com.example.agent_app.backend.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Google UserInfo API 응답 DTO
 */
@Serializable
data class UserInfo(
    val id: String,
    val email: String,
    @SerialName("verified_email")
    val verifiedEmail: Boolean,
    val name: String? = null,
    @SerialName("given_name")
    val givenName: String? = null,
    @SerialName("family_name")
    val familyName: String? = null,
    val picture: String? = null,
    val locale: String? = null
)

