package com.example.agent_app.backend.plugins

import com.example.agent_app.backend.repositories.SharedCalendarRepository
import com.example.agent_app.backend.routes.adminAccountRoutes
import com.example.agent_app.backend.routes.adminApiRoutes
import com.example.agent_app.backend.routes.adminAuthRoutes
import com.example.agent_app.backend.routes.calendarRoutes
import com.example.agent_app.backend.services.SharedCalendarService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val calendarRepository = SharedCalendarRepository()
    val calendarService = SharedCalendarService(calendarRepository)

    routing {
        get("/") {
            call.respondText("HuenDongMin Backend Server is running!")
        }

        get("/health") {
            call.respondText("OK")
        }

        // 공유 캘린더 라우트
        calendarRoutes(calendarService)

        // 관리자 인증 라우트 (1-3단계)
        adminAuthRoutes()

        // 관리자 API 라우트 (5단계)
        adminApiRoutes()

        // 관리자 계정 관리 라우트 (기존)
        adminAccountRoutes()
    }
}

