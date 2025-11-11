package com.example.agent_app.share.network

import com.example.agent_app.share.model.CalendarDetailDto
import com.example.agent_app.share.model.CreateCalendarRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ShareCalendarApi {

    @POST("calendar/groups")
    suspend fun createCalendar(
        @Body request: CreateCalendarRequest,
    ): CalendarDetailDto

    @GET("calendar/groups")
    suspend fun listCalendars(): CalendarListResponse
}

@kotlinx.serialization.Serializable
data class CalendarListResponse(
    val calendars: List<com.example.agent_app.share.model.CalendarDetailDto> = emptyList(),
)

