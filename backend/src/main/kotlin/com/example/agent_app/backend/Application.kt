package com.example.agent_app.backend

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.example.agent_app.backend.plugins.*

fun main() {
    // 포트는 환경 변수에서 읽거나 기본값 8080 사용
    // Railway, Render 등은 PORT 환경 변수를 제공
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    
    embeddedServer(
        factory = Netty,
        module = Application::module,
        host = "0.0.0.0",
        port = port,
        configure = {
            // application.conf 파일에서 설정 로드
        }
    ).start(wait = true)
}

fun Application.module() {
    configureSecurity() // 보안 설정 먼저 적용
    configureSerialization()
    configureDatabase()
    configureRouting()
    configureMonitoring()
}

