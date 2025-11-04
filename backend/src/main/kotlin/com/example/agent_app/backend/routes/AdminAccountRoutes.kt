package com.example.agent_app.backend.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.example.agent_app.backend.services.GoogleOAuthService
import com.example.agent_app.backend.services.AdminAccountService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.adminAccountRoutes() {
    val oauthService = GoogleOAuthService()
    val accountService = AdminAccountService()
    
    route("/admin/accounts") {
        /**
         * 1. Google OAuth 인증 시작
         * Android 앱에서 브라우저로 이 URL을 열면, Google 로그인 페이지로 리다이렉트됩니다.
         */
        get("/connect/google") {
            withContext(Dispatchers.IO) {
                try {
                    val authorizationUrl = oauthService.getAuthorizationUrl()
                    call.respondRedirect(authorizationUrl)
                } catch (e: Exception) {
                    call.application.log.error("Failed to start OAuth flow", e)
                    call.respondText(
                        "OAuth 인증 시작 실패: ${e.message}",
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
        }
        
        /**
         * 2. Google OAuth 콜백
         * Google이 인증 완료 후 이 엔드포인트로 리다이렉트합니다.
         */
        get("/connect/google/callback") {
            withContext(Dispatchers.IO) {
                try {
                    val code = call.parameters["code"]
                    val error = call.parameters["error"]
                    
                    if (error != null) {
                        call.respondText(
                            "OAuth 인증 실패: $error",
                            status = HttpStatusCode.BadRequest
                        )
                        return@withContext
                    }
                    
                    if (code == null) {
                        call.respondText(
                            "인증 코드가 없습니다.",
                            status = HttpStatusCode.BadRequest
                        )
                        return@withContext
                    }
                    
                    // 인증 코드로 토큰 교환
                    val tokenResponse = oauthService.exchangeCodeForTokens(code)
                    
                    // 사용자 정보 가져오기
                    val userInfo = oauthService.getUserInfo(tokenResponse.accessToken)
                    
                    // 데이터베이스에 저장
                    val account = accountService.upsertAccount(
                        email = userInfo.email,
                        accessToken = tokenResponse.accessToken,
                        refreshToken = tokenResponse.refreshToken,
                        idToken = tokenResponse.idToken,
                        scopes = tokenResponse.scope?.split(" ") ?: emptyList(),
                        expiresIn = tokenResponse.expiresIn
                    )
                    
                    call.application.log.info("Successfully connected account: ${account.email}")
                    
                    // 성공 페이지 표시
                    call.respondText(
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <title>계정 연결 성공</title>
                            <meta charset="UTF-8">
                            <style>
                                body {
                                    font-family: Arial, sans-serif;
                                    display: flex;
                                    justify-content: center;
                                    align-items: center;
                                    height: 100vh;
                                    margin: 0;
                                    background-color: #f5f5f5;
                                }
                                .container {
                                    text-align: center;
                                    background: white;
                                    padding: 40px;
                                    border-radius: 8px;
                                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                                }
                                h1 { color: #4CAF50; }
                                .email { color: #666; margin: 20px 0; }
                                .close-button {
                                    background-color: #4CAF50;
                                    color: white;
                                    border: none;
                                    padding: 10px 20px;
                                    border-radius: 4px;
                                    cursor: pointer;
                                    margin-top: 20px;
                                }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <h1>✓ 계정 연결 성공!</h1>
                                <p class="email">${account.email}</p>
                                <p>계정이 성공적으로 연결되었습니다.</p>
                                <button class="close-button" onclick="window.close()">창 닫기</button>
                            </div>
                        </body>
                        </html>
                        """.trimIndent(),
                        ContentType.Text.Html
                    )
                } catch (e: Exception) {
                    call.application.log.error("OAuth callback failed", e)
                    call.respondText(
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <title>계정 연결 실패</title>
                            <meta charset="UTF-8">
                        </head>
                        <body>
                            <h1>계정 연결 실패</h1>
                            <p>오류: ${e.message}</p>
                            <button onclick="window.close()">창 닫기</button>
                        </body>
                        </html>
                        """.trimIndent(),
                        ContentType.Text.Html,
                        HttpStatusCode.InternalServerError
                    )
                }
            }
        }
        
        /**
         * 3. 연결된 계정 목록 조회
         */
        get {
            withContext(Dispatchers.IO) {
                try {
                    val accounts = accountService.getAllAccounts()
                    call.respond(HttpStatusCode.OK, mapOf("accounts" to accounts))
                } catch (e: Exception) {
                    call.application.log.error("Failed to fetch accounts", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "계정 목록 조회 실패: ${e.message}")
                    )
                }
            }
        }
        
        /**
         * 4. 계정 삭제
         */
        delete("/{email}") {
            withContext(Dispatchers.IO) {
                try {
                    val email = call.parameters["email"] ?: run {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "이메일이 지정되지 않았습니다.")
                        )
                        return@withContext
                    }
                    
                    val deleted = accountService.deleteAccount(email)
                    if (deleted) {
                        call.respond(
                            HttpStatusCode.OK,
                            mapOf("message" to "계정이 삭제되었습니다.", "email" to email)
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "해당 계정을 찾을 수 없습니다.")
                        )
                    }
                } catch (e: Exception) {
                    call.application.log.error("Failed to delete account", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "계정 삭제 실패: ${e.message}")
                    )
                }
            }
        }
    }
}

