package com.example.agent_app.backend.routes

import com.example.agent_app.backend.models.calendar.AddMemberRequest
import com.example.agent_app.backend.models.calendar.CalendarListResponse
import com.example.agent_app.backend.models.calendar.CreateCalendarRequest
import com.example.agent_app.backend.models.calendar.CreateEventRequest
import com.example.agent_app.backend.models.calendar.CreateShareTokenRequest
import com.example.agent_app.backend.services.SharedCalendarService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import java.util.UUID

private const val USER_HEADER = "X-User-Email"

fun Route.calendarRoutes(service: SharedCalendarService) {
    route("/calendar") {
        get("/groups") {
            val actorEmail = call.actorEmailOrNull() ?: return@get
            val response = service.listCalendars(actorEmail)
            call.respond(response)
        }

        get("/profile") {
            val actorEmail = call.actorEmailOrNull() ?: return@get
            val profile = service.getOrCreateShareProfile(actorEmail)
            call.respond(profile)
        }

        get("/profile/{shareId}") {
            val shareId = call.parameters["shareId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing share id.")
            val profile = service.getShareProfile(shareId)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Profile not found.")
            call.respond(profile)
        }

        post("/groups") {
            val actorEmail = call.actorEmailOrNull() ?: return@post
            val body = call.receiveValidated<CreateCalendarRequest>() ?: return@post
            val calendar = service.createCalendar(actorEmail, body)
            call.respond(HttpStatusCode.Created, calendar)
        }

        get("/groups/{id}") {
            val actorEmail = call.actorEmailOrNull() ?: return@get
            val calendarId = call.calendarIdOrNull() ?: return@get
            val calendar = service.getCalendar(calendarId, actorEmail)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Calendar not found or access denied.")
            call.respond(calendar)
        }

        post("/groups/{id}/members") {
            val actorEmail = call.actorEmailOrNull() ?: return@post
            val calendarId = call.calendarIdOrNull() ?: return@post
            val request = call.receiveValidated<AddMemberRequest>() ?: return@post
            val success = service.addMember(calendarId, actorEmail, request)
            if (success) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.Forbidden, "Insufficient permissions or calendar not found.")
            }
        }

        get("/groups/{id}/events") {
            val actorEmail = call.actorEmailOrNull() ?: return@get
            val calendarId = call.calendarIdOrNull() ?: return@get
            val events = service.listEvents(calendarId, actorEmail)
                ?: return@get call.respond(HttpStatusCode.Forbidden, "Access denied.")
            call.respond(mapOf("events" to events))
        }

        post("/groups/{id}/events") {
            val actorEmail = call.actorEmailOrNull() ?: return@post
            val calendarId = call.calendarIdOrNull() ?: return@post
            val request = call.receiveValidated<CreateEventRequest>() ?: return@post
            val event = service.createEvent(calendarId, actorEmail, request)
                ?: return@post call.respond(HttpStatusCode.Forbidden, "Unable to create event.")
            call.respond(HttpStatusCode.Created, event)
        }

        post("/groups/{id}/share-token") {
            val actorEmail = call.actorEmailOrNull() ?: return@post
            val calendarId = call.calendarIdOrNull() ?: return@post
            val request = call.receiveValidated<CreateShareTokenRequest>() ?: return@post
            val token = service.createShareToken(calendarId, actorEmail, request)
                ?: return@post call.respond(HttpStatusCode.Forbidden, "Unable to create share token.")
            call.respond(HttpStatusCode.Created, token)
        }

        get("/share/{token}") {
            val token = call.parameters["token"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Token missing.")
            val calendar = service.getByShareToken(token)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Share token invalid or expired.")
            call.respond(calendar)
        }

        get("/ui/groups/{id}") {
            val token = call.request.queryParameters["token"]
            val builder = StringBuilder()
            val calendar = if (token != null) {
                service.getByShareToken(token)
            } else {
                val actorEmail = call.request.headers[USER_HEADER]
                val calendarId = call.calendarIdOrNull() ?: return@get call.respondText(
                    "Invalid calendar id.",
                    ContentType.Text.Plain,
                    HttpStatusCode.BadRequest,
                )
                if (actorEmail == null) {
                    null
                } else {
                    service.getCalendar(calendarId, actorEmail)
                }
            }

            if (calendar == null) {
                call.respondText(
                    "Calendar not found or access denied.",
                    ContentType.Text.Html,
                    HttpStatusCode.NotFound,
                )
                return@get
            }

            builder.append(
                """
                <html>
                <head>
                    <meta charset="utf-8" />
                    <title>${calendar.name}</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 20px; }
                        h1 { color: #2563eb; }
                        table { border-collapse: collapse; width: 100%; margin-top: 12px; }
                        th, td { border: 1px solid #e5e7eb; padding: 8px; text-align: left; }
                        th { background-color: #f3f4f6; }
                        .section { margin-top: 24px; }
                    </style>
                </head>
                <body>
                    <h1>${calendar.name}</h1>
                    <p>${calendar.description ?: "No description provided."}</p>
                    <div class="section">
                        <h2>Members</h2>
                        <table>
                            <tr><th>Email</th><th>Role</th><th>Joined</th></tr>
                """.trimIndent()
            )
            calendar.members.forEach { member ->
                builder.append("<tr><td>${member.email}</td><td>${member.role}</td><td>${member.joinedAt}</td></tr>")
            }
            builder.append("</table>")
            builder.append(
                """
                    </div>
                    <div class="section">
                        <h2>Events</h2>
                        <table>
                            <tr><th>Title</th><th>Start</th><th>End</th><th>Location</th><th>Created By</th></tr>
                """.trimIndent()
            )
            calendar.events.forEach { event ->
                builder.append(
                    "<tr><td>${event.title}</td><td>${event.startAt}</td><td>${event.endAt ?: "-"}</td><td>${event.location ?: "-"}</td><td>${event.createdBy}</td></tr>"
                )
            }
            builder.append(
                """
                        </table>
                    </div>
                </body>
                </html>
                """.trimIndent()
            )
            call.respondText(builder.toString(), ContentType.Text.Html)
        }
    }
}

suspend inline fun <reified T : Any> ApplicationCall.receiveValidated(): T? =
    try {
        receive<T>()
    } catch (ex: SerializationException) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid payload: ${ex.message}"))
        null
    } catch (ex: Exception) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid payload: ${ex.message}"))
        null
    }

suspend fun ApplicationCall.actorEmailOrNull(): String? {
    val actorEmail = request.headers[USER_HEADER]
    if (actorEmail.isNullOrBlank()) {
        respondText("Missing $USER_HEADER header.", ContentType.Text.Plain, HttpStatusCode.BadRequest)
        return null
    }
    return actorEmail
}

suspend fun ApplicationCall.calendarIdOrNull(): UUID? {
    val id = parameters["id"]
    if (id.isNullOrBlank()) {
        respondText("Missing calendar id.", ContentType.Text.Plain, HttpStatusCode.BadRequest)
        return null
    }
    return try {
        UUID.fromString(id)
    } catch (ex: IllegalArgumentException) {
        respondText("Invalid calendar id.", ContentType.Text.Plain, HttpStatusCode.BadRequest)
        null
    }
}


