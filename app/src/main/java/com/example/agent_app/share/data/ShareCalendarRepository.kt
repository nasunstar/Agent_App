package com.example.agent_app.share.data

import com.example.agent_app.share.model.CalendarDetailDto
import com.example.agent_app.share.model.CreateCalendarEventRequest
import com.example.agent_app.share.model.CreateCalendarRequest
import com.example.agent_app.share.model.ShareProfileResponse
import com.example.agent_app.share.network.ShareCalendarApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShareCalendarRepository(
    private val api: ShareCalendarApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun getOrCreateProfile(actorEmail: String): Result<ShareProfileResponse> =
        withContext(ioDispatcher) {
            runCatching { api.getOrCreateProfile(actorEmail) }
        }

    suspend fun getProfileByShareId(shareId: String): Result<ShareProfileResponse> =
        withContext(ioDispatcher) {
            runCatching { api.getProfileByShareId(shareId) }
        }

    suspend fun createCalendar(
        actorEmail: String,
        name: String,
        description: String?,
    ): Result<CalendarDetailDto> = withContext(ioDispatcher) {
        runCatching {
            api.createCalendar(
                actorEmail = actorEmail,
                request = CreateCalendarRequest(name = name, description = description),
            )
        }
    }

    suspend fun getCalendarDetail(
        actorEmail: String,
        calendarId: String,
    ): Result<CalendarDetailDto> = withContext(ioDispatcher) {
        runCatching {
            api.getCalendarDetail(
                actorEmail = actorEmail,
                calendarId = calendarId,
            )
        }
    }

    suspend fun createEvent(
        actorEmail: String,
        calendarId: String,
        request: CreateCalendarEventRequest,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            api.createEvent(
                actorEmail = actorEmail,
                calendarId = calendarId,
                request = request,
            )
        }.map { }
    }
}

