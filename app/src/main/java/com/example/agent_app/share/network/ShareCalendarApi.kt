package com.example.agent_app.share.network

import com.example.agent_app.share.model.CalendarDetailDto
import com.example.agent_app.share.model.CreateCalendarRequest
import com.example.agent_app.share.model.ShareProfileResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ShareCalendarApi {

    @GET("calendar/profile")
    suspend fun getOrCreateProfile(
        @Header("X-User-Email") actorEmail: String,
    ): ShareProfileResponse

    @GET("calendar/profile/{shareId}")
    suspend fun getProfileByShareId(
        @Path("shareId") shareId: String,
    ): ShareProfileResponse

    @POST("calendar/groups")
    suspend fun createCalendar(
        @Header("X-User-Email") actorEmail: String,
        @Body request: CreateCalendarRequest,
    ): CalendarDetailDto
}

