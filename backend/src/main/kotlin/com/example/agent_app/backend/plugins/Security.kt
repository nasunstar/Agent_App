package com.example.agent_app.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.response.*
import com.example.agent_app.backend.config.ConfigLoader

/**
 * 보안 플러그인 설정
 * CORS, 보안 헤더, HTTPS 강제 등을 설정합니다.
 */
fun Application.configureSecurity() {
    // Forwarded Headers (프록시 뒤에서 실행 시 실제 IP 확인)
    install(XForwardedHeaders)
    
    // 기본 보안 헤더
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("X-XSS-Protection", "1; mode=block")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
    }
    
    // HTTPS 강제 (프로덕션 환경)
    val isProduction = System.getenv("ENVIRONMENT") == "production" 
        || ConfigLoader.getProperty("ENVIRONMENT") == "production"
    
    // 허용된 Origin 목록 (환경 변수에서 읽기)
    val allowedOrigins = System.getenv("ALLOWED_ORIGINS")
        ?: ConfigLoader.getProperty("ALLOWED_ORIGINS")
        ?: "*" // 개발 환경에서는 모든 origin 허용 (프로덕션에서는 특정 도메인만)
    
    // CORS 설정
    install(CORS) {
        if (allowedOrigins == "*") {
            // 개발 환경: 모든 origin 허용
            allowAnyOrigin = true
        } else {
            // 프로덕션: 특정 origin만 허용
            allowedOrigins.split(",").forEach { origin ->
                allowHost(origin.trim(), schemes = listOf("http", "https"))
            }
        }
        
        // 허용된 HTTP 메서드
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        
        // 허용된 헤더
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Requested-With")
        
        // Credentials 허용 (쿠키, 인증 헤더 등)
        // 주의: allowAnyOrigin = true일 때는 allowCredentials = true가 불가능
        if (allowedOrigins != "*") {
            allowCredentials = true
        }
        
        // Preflight 요청 캐시 시간
        maxAgeInSeconds = 3600
    }
    
    if (isProduction) {
        intercept(ApplicationCallPipeline.Plugins) {
            val scheme = call.request.origin.scheme
            if (scheme != "https") {
                // HTTP 요청을 HTTPS로 리다이렉트
                val httpsUrl = call.request.uri.replace("http://", "https://")
                call.respondRedirect(httpsUrl, permanent = true)
                return@intercept
            }
        }
    }
    
    application.log.info("Security configured: Production=${isProduction}, CORS=${allowedOrigins}")
}

