package com.example.agent_app.share.data

import com.example.agent_app.share.model.CalendarDetailDto
import com.example.agent_app.share.model.CreateCalendarRequest
import com.example.agent_app.share.network.ShareCalendarApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShareCalendarRepository(
    private val api: ShareCalendarApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun createCalendar(
        name: String,
        description: String?,
    ): Result<CalendarDetailDto> = withContext(ioDispatcher) {
        runCatching {
            api.createCalendar(CreateCalendarRequest(name = name, description = description))
        }
    }
}

