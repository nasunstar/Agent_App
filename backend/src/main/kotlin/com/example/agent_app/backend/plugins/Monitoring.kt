package com.example.agent_app.backend.plugins

import io.ktor.server.application.*
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    // CallLogging은 Ktor 2.x에서 자동으로 포함되므로 별도 설정 불필요
    // 필요시 나중에 추가 가능
}

