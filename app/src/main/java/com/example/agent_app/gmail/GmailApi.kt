package com.example.agent_app.gmail

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface GmailApi {
    @GET("gmail/v1/users/{userId}/messages")
    suspend fun listMessages(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Query("maxResults") maxResults: Int = 20,
        @Query("q") query: String? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("includeSpamTrash") includeSpamTrash: Boolean = false,
    ): GmailMessageListResponse

    @GET("gmail/v1/users/{userId}/messages/{messageId}")
    suspend fun getMessage(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Path("messageId") messageId: String,
        @Query("format") format: String = "metadata",
        @Query("metadataHeaders") metadataHeaders: List<String> = listOf("Subject", "Date"),
    ): GmailMessage
}
