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
    val parts: List<GmailMessagePart>? = null,
    val body: GmailMessageBody? = null,
    val mimeType: String? = null,
)

@Serializable
data class GmailMessagePart(
    val mimeType: String? = null,
    val body: GmailMessageBody? = null,
    val parts: List<GmailMessagePart>? = null,
    val headers: List<GmailHeader>? = null,
)

@Serializable
data class GmailMessageBody(
    val data: String? = null,
    val size: Int? = null,
)

@Serializable
data class GmailHeader(
    val name: String,
    val value: String? = null,
)
