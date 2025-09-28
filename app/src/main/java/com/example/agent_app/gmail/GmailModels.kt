package com.example.agent_app.gmail

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GmailMessageListResponse(
    val messages: List<GmailMessageReference> = emptyList(),
    @SerialName("nextPageToken")
    val nextPageToken: String? = null,
)

@Serializable
data class GmailMessageReference(
    val id: String,
    val threadId: String? = null,
)

@Serializable
data class GmailMessage(
    val id: String,
    val threadId: String? = null,
    val snippet: String? = null,
    val labelIds: List<String>? = null,
    val internalDate: String? = null,
    val payload: GmailPayload? = null,
)

@Serializable
data class GmailPayload(
    val headers: List<GmailHeader>? = null,
)

@Serializable
data class GmailHeader(
    val name: String,
    val value: String? = null,
)
