package com.example.agent_app.backend.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import com.example.agent_app.backend.repositories.ManagedAccountRepository
import com.example.agent_app.backend.utils.GoogleAuthUtils
import com.example.agent_app.backend.models.EmailCheckResult
import com.example.agent_app.backend.models.EmailCheckResponse
import com.example.agent_app.backend.models.EmailCheckData
import com.example.agent_app.backend.models.EmailMessage
import com.example.agent_app.backend.config.ConfigLoader

/**
 * 관리자 API 라우트
 * 모든 계정의 메일을 일괄 확인하는 API를 제공합니다.
 */
fun Route.adminApiRoutes() {
    route("/api/admin") {
        /**
         * 5단계: 모든 계정 메일 일괄 확인 API
         * developerMenuScreen.kt에서 [모든 계정 메일 확인] 버튼을 눌렀을 때 호출
         */
        get("/check-all-emails") {
            withContext(Dispatchers.IO) {
                try {
                    // application.conf에서 먼저 읽고, 없으면 환경 변수 또는 local.properties에서 읽기
                    val config = call.application.environment.config
                    val clientId = try {
                        config.property("ktor.google.oauth.client_id").getString()
                    } catch (e: Exception) {
                        ConfigLoader.getProperty("GOOGLE_WEB_CLIENT_ID")
                            ?: throw IllegalStateException("GOOGLE_WEB_CLIENT_ID를 찾을 수 없습니다.")
                    }
                    
                    val clientSecret = try {
                        config.property("ktor.google.oauth.client_secret").getString()
                    } catch (e: Exception) {
                        ConfigLoader.getProperty("GOOGLE_CLIENT_SECRET")
                            ?: throw IllegalStateException("GOOGLE_CLIENT_SECRET를 찾을 수 없습니다. local.properties 또는 환경 변수를 설정해주세요.")
                    }
                    
                    // DB에서 저장된 모든 ManagedGoogleAccount 목록 가져오기
                    val repository = ManagedAccountRepository()
                    val accounts = repository.findAll()
                    
                    if (accounts.isEmpty()) {
                        call.respond(
                            HttpStatusCode.OK,
                            EmailCheckResponse(results = emptyList())
                        )
                        return@withContext
                    }
                    
                    call.application.log.info("총 ${accounts.size}개의 계정 메일 확인 시작...")
                    
                    // Kotlin 코루틴의 coroutineScope와 async를 사용해 각 계정별로 병렬 처리
                    val results: List<EmailCheckResult> = coroutineScope {
                        accounts.map { account ->
                            async {
                                try {
                                    // a. GoogleAuthUtils.getNewAccessToken 함수를 호출해 새 access_token을 받아
                                    val accessToken = GoogleAuthUtils.getNewAccessToken(
                                        encryptedRefreshToken = account.encryptedRefreshToken,
                                        clientId = clientId,
                                        clientSecret = clientSecret
                                    )
                                    
                                    // b. access_token이 null이면 EmailCheckResult(email = account.googleEmail, status = "ERROR", data = null)을 반환해
                                    if (accessToken == null) {
                                        call.application.log.warn("계정 ${account.googleEmail}의 access token 갱신 실패")
                                        EmailCheckResult(
                                            email = account.googleEmail,
                                            status = "ERROR",
                                            errorMessage = "Access token 갱신 실패",
                                            data = null
                                        )
                                    } else {
                                        // c. access_token이 있으면, Ktor Client로 Gmail API 호출
                                        val client = HttpClient(CIO) {
                                            install(ContentNegotiation) {
                                                json(Json {
                                                    ignoreUnknownKeys = true
                                                    isLenient = true
                                                })
                                            }
                                        }
                                        
                                        try {
                                            // https://gmail.googleapis.com/gmail/v1/users/me/messages?q=is:unread&maxResults=10
                                            val gmailResponse: GmailMessagesResponse = client.get(
                                                "https://gmail.googleapis.com/gmail/v1/users/me/messages"
                                            ) {
                                                headers {
                                                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                                                }
                                                parameter("q", "is:unread")
                                                parameter("maxResults", "10")
                                            }.body()
                                            
                                            client.close()
                                            
                                            // d. 성공하면 EmailCheckResult(email = account.googleEmail, status = "SUCCESS", data = ...)를 반환해
                                            call.application.log.info("계정 ${account.googleEmail} 메일 확인 성공: ${gmailResponse.messages.size}개의 읽지 않은 메일")
                                            EmailCheckResult(
                                                email = account.googleEmail,
                                                status = "SUCCESS",
                                                errorMessage = null,
                                                data = EmailCheckData(
                                                    unreadCount = gmailResponse.messages.size,
                                                    messages = gmailResponse.messages.map { msg ->
                                                        EmailMessage(
                                                            id = msg.id,
                                                            threadId = msg.threadId
                                                        )
                                                    }
                                                )
                                            )
                                        } catch (e: Exception) {
                                            client.close()
                                            call.application.log.error(
                                                "계정 ${account.googleEmail}의 Gmail API 호출 실패",
                                                e
                                            )
                                            EmailCheckResult(
                                                email = account.googleEmail,
                                                status = "ERROR",
                                                errorMessage = "Gmail API 호출 실패: ${e.message}",
                                                data = null
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    call.application.log.error(
                                        "계정 ${account.googleEmail} 처리 중 예외 발생",
                                        e
                                    )
                                    EmailCheckResult(
                                        email = account.googleEmail,
                                        status = "ERROR",
                                        errorMessage = "처리 중 오류: ${e.message}",
                                        data = null
                                    )
                                }
                            }
                        }.awaitAll() // awaitAll()을 사용해 모든 EmailCheckResult 리스트가 준비되면
                    }
                    
                    // 이 리스트를 JSON으로 클라이언트(developerMenuScreen.kt)에게 응답해 줘
                    call.application.log.info("모든 계정 메일 확인 완료")
                    call.respond(
                        HttpStatusCode.OK,
                        EmailCheckResponse(results = results)
                    )
                } catch (e: Exception) {
                    call.application.log.error("모든 계정 메일 확인 API 실패", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        EmailCheckResponse(
                            results = emptyList(),
                            error = "서버 오류: ${e.message}"
                        )
                    )
                }
            }
        }
    }
}

/**
 * Gmail Messages API 응답 DTO
 */
@Serializable
private data class GmailMessagesResponse(
    val messages: List<GmailMessage> = emptyList(),
    val nextPageToken: String? = null,
    val resultSizeEstimate: Long = 0
)

@Serializable
private data class GmailMessage(
    val id: String,
    val threadId: String
)

