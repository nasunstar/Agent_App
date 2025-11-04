package com.example.agent_app.backend.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.agent_app.backend.models.TokenResponse
import com.example.agent_app.backend.models.UserInfo
import com.example.agent_app.backend.utils.CryptoUtils
import com.example.agent_app.backend.repositories.ManagedAccountRepository
import com.example.agent_app.backend.config.ConfigLoader
import com.example.agent_app.backend.plugins.rateLimit

/**
 * 관리자 인증 라우트
 * Google OAuth 2.0 플로우를 처리합니다.
 */
fun Route.adminAuthRoutes() {
    route("/admin/auth/google") {
        /**
         * 1단계: Google OAuth 인증 시작
         * 관리자가 developerMenuScreen.kt에서 [새 계정 연결] 버튼을 눌렀을 때 호출
         */
        get("/start") {
            // Rate Limiting 체크
            val isAllowed = kotlinx.coroutines.runBlocking {
                call.rateLimit()
            }
            if (!isAllowed) {
                call.respondText(
                    "Too many requests. Please try again later.",
                    status = HttpStatusCode.TooManyRequests
                )
                return@get
            }
            
            try {
                // application.conf에서 먼저 읽고, 없으면 환경 변수 또는 local.properties에서 읽기
                val config = call.application.environment.config
                val clientId = try {
                    config.property("ktor.google.oauth.client_id").getString()
                } catch (e: Exception) {
                    ConfigLoader.getProperty("GOOGLE_WEB_CLIENT_ID")
                        ?: throw IllegalStateException("GOOGLE_WEB_CLIENT_ID를 찾을 수 없습니다. local.properties 또는 환경 변수를 설정해주세요.")
                }
                
                // 요청의 호스트를 동적으로 감지
                val requestHost = call.request.host()
                val requestPort = call.request.port()
                
                // Google OAuth는 private IP (192.168.x.x)를 허용하지 않으므로 localhost 사용
                // redirect URI는 항상 localhost로 고정 (Google Cloud Console에 등록된 URI와 일치해야 함)
                val redirectUri = try {
                    config.property("ktor.google.oauth.redirect_uri").getString()
                } catch (e: Exception) {
                    val configuredUri = ConfigLoader.getProperty("OAUTH_REDIRECT_URI")
                    if (configuredUri != null) {
                        configuredUri
                    } else {
                        // 기본값: localhost 사용 (Google Cloud Console에 등록해야 함)
                        "http://localhost:8080/admin/auth/google/callback"
                    }
                }
                
                call.application.log.info("OAuth Redirect URI: $redirectUri (request from $requestHost:$requestPort)")
                call.application.log.info("Note: Redirect URI must be registered in Google Cloud Console as: $redirectUri")
                
                // 필수 파라미터: access_type=offline (refresh_token 받기)
                // prompt=select_account (항상 계정 선택창 띄우기)
                val scopes = listOf(
                    "https://www.googleapis.com/auth/gmail.readonly",
                    "https://www.googleapis.com/auth/userinfo.email"
                ).joinToString(" ")
                
                val params = mutableListOf(
                    "client_id" to clientId,
                    "redirect_uri" to redirectUri,
                    "response_type" to "code",
                    "scope" to scopes,
                    "access_type" to "offline",  // refresh_token 받기 위해 필수
                    "prompt" to "select_account"  // 항상 계정 선택창 띄우기
                )
                
                // 모든 파라미터를 URL 인코딩하여 쿼리 스트링 생성
                val queryString = params.joinToString("&") { (key, value) ->
                    "$key=${URLEncoder.encode(value, "UTF-8")}"
                }
                
                val authorizationUrl = "https://accounts.google.com/o/oauth2/v2/auth?$queryString"
                
                // 보안: 전체 URL을 로그에 남기지 않음 (client_id 포함)
                call.application.log.info("OAuth authorization URL generated (length: ${authorizationUrl.length})")
                call.respondRedirect(authorizationUrl)
            } catch (e: Exception) {
                call.application.log.error("Failed to start OAuth flow", e)
                call.respondText(
                    "OAuth 인증 시작 실패: ${e.message}",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }
        
        /**
         * 2단계: OAuth 콜백 및 토큰 교환
         * Google이 인증 후 code를 보내줄 콜백 엔드포인트
         */
        get("/callback") {
            withContext(Dispatchers.IO) {
                // Rate Limiting 체크
                if (!call.rateLimit()) {
                    call.respondText(
                        "Too many requests. Please try again later.",
                        status = HttpStatusCode.TooManyRequests
                    )
                    return@withContext
                }
                
                try {
                    // URL 쿼리 파라미터에서 code 값 받기
                    val code = call.request.queryParameters["code"]
                    val error = call.request.queryParameters["error"]
                    
                    // 에러 처리
                    if (error != null) {
                        call.application.log.error("OAuth callback error: $error")
                        call.respondText(
                            "OAuth 인증 실패: $error",
                            status = HttpStatusCode.BadRequest
                        )
                        return@withContext
                    }
                    
                    // code가 null이면 에러
                    if (code == null) {
                        call.application.log.error("OAuth callback: code parameter is missing")
                        call.respondText(
                            "인증 코드가 없습니다.",
                            status = HttpStatusCode.BadRequest
                        )
                        return@withContext
                    }
                    
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
                    
                    // redirect URI는 localhost로 고정 (start 엔드포인트와 동일하게)
                    val redirectUri = try {
                        config.property("ktor.google.oauth.redirect_uri").getString()
                    } catch (e: Exception) {
                        val configuredUri = ConfigLoader.getProperty("OAUTH_REDIRECT_URI")
                        if (configuredUri != null) {
                            configuredUri
                        } else {
                            // 기본값: localhost 사용
                            "http://localhost:8080/admin/auth/google/callback"
                        }
                    }
                    
                    // Ktor HTTP Client 생성
                    val client = HttpClient(CIO) {
                        install(ContentNegotiation) {
                            json(Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            })
                        }
                    }
                    
                    // https://oauth2.googleapis.com/token 엔드포인트에 POST 요청
                    val formBody = listOf(
                        "code" to code,
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "redirect_uri" to redirectUri,
                        "grant_type" to "authorization_code"
                    ).formUrlEncode()
                    
                    val tokenResponse: TokenResponse = client.post("https://oauth2.googleapis.com/token") {
                        contentType(ContentType.Application.FormUrlEncoded)
                        setBody(formBody)
                    }.body()
                    
                    // 응답에 에러가 있는지 확인
                    if (tokenResponse.error != null) {
                        call.application.log.error(
                            "Token exchange failed: ${tokenResponse.error} - ${tokenResponse.errorDescription}"
                        )
                        call.respondText(
                            "토큰 교환 실패: ${tokenResponse.error} - ${tokenResponse.errorDescription}",
                            status = HttpStatusCode.BadRequest
                        )
                        client.close()
                        return@withContext
                    }
                    
                    // 성공하면 access_token과 refresh_token을 콘솔에 로그로 찍기 (보안: 일부만 로그)
                    call.application.log.info("✅ OAuth 토큰 교환 성공!")
                    // 보안: 토큰 전체를 로그에 남기지 않음
                    call.application.log.info("Token expires in: ${tokenResponse.expiresIn} seconds")
                    call.application.log.info("Refresh token received: ${if (tokenResponse.refreshToken != null) "Yes" else "No"}")
                    
                    // refresh_token이 없으면 에러
                    val refreshToken = tokenResponse.refreshToken
                    if (refreshToken == null) {
                        call.application.log.warn("⚠️ Refresh token이 없습니다. access_type=offline과 prompt=consent을 확인하세요.")
                        call.respondText(
                            "Refresh token을 받지 못했습니다. 다시 시도해주세요.",
                            status = HttpStatusCode.BadRequest
                        )
                        client.close()
                        return@withContext
                    }
                    
                    // 3단계: access_token으로 사용자 정보 가져오기
                    call.application.log.info("사용자 정보 조회 중...")
                    val userInfo: UserInfo = client.get("https://www.googleapis.com/oauth2/v1/userinfo") {
                        headers {
                            append(HttpHeaders.Authorization, "Bearer ${tokenResponse.accessToken}")
                        }
                    }.body()
                    
                    call.application.log.info("✅ 사용자 정보 조회 성공: ${userInfo.email}")
                    
                    // refresh_token 암호화
                    val encryptedRefreshToken = try {
                        CryptoUtils.encrypt(refreshToken)
                    } catch (e: Exception) {
                        call.application.log.error("Refresh token 암호화 실패", e)
                        call.respondText(
                            "토큰 암호화 실패: ${e.message}",
                            status = HttpStatusCode.InternalServerError
                        )
                        client.close()
                        return@withContext
                    }
                    
                    // DB에 저장
                    try {
                        val repository = ManagedAccountRepository()
                        repository.save(userInfo.email, encryptedRefreshToken)
                        call.application.log.info("✅ 계정 정보 DB 저장 완료: ${userInfo.email}")
                    } catch (e: Exception) {
                        call.application.log.error("DB 저장 실패", e)
                        call.respondText(
                            "계정 저장 실패: ${e.message}",
                            status = HttpStatusCode.InternalServerError
                        )
                        client.close()
                        return@withContext
                    }
                    
                    client.close()
                    
                    // 성공 HTML 페이지 표시 (developerMenuScreen.kt가 닫을 수 있도록)
                    call.respondText(
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <title>계정 연결 성공</title>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body {
                                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                                    display: flex;
                                    justify-content: center;
                                    align-items: center;
                                    height: 100vh;
                                    margin: 0;
                                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                                }
                                .container {
                                    text-align: center;
                                    background: white;
                                    padding: 40px;
                                    border-radius: 16px;
                                    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                                    max-width: 400px;
                                    width: 90%;
                                }
                                .success-icon {
                                    font-size: 64px;
                                    margin-bottom: 20px;
                                }
                                h1 {
                                    color: #4CAF50;
                                    margin: 0 0 10px 0;
                                    font-size: 24px;
                                }
                                .email {
                                    color: #666;
                                    margin: 20px 0;
                                    font-size: 16px;
                                    font-weight: 500;
                                }
                                .message {
                                    color: #888;
                                    margin: 20px 0;
                                    font-size: 14px;
                                    line-height: 1.5;
                                }
                                .close-button {
                                    background-color: #4CAF50;
                                    color: white;
                                    border: none;
                                    padding: 12px 32px;
                                    border-radius: 8px;
                                    cursor: pointer;
                                    margin-top: 20px;
                                    font-size: 16px;
                                    font-weight: 600;
                                    transition: background-color 0.3s;
                                }
                                .close-button:hover {
                                    background-color: #45a049;
                                }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="success-icon">✅</div>
                                <h1>계정 연결 성공!</h1>
                                <p class="email">${userInfo.email}</p>
                                <p class="message">Google 계정이 성공적으로 연결되었습니다.<br>이 창을 닫고 앱으로 돌아가세요.</p>
                                <button class="close-button" onclick="window.close()">창 닫기</button>
                            </div>
                            <script>
                                // 3초 후 자동으로 창 닫기 시도 (일부 브라우저에서는 작동하지 않을 수 있음)
                                setTimeout(function() {
                                    try {
                                        window.close();
                                    } catch(e) {
                                        // window.close()가 작동하지 않으면 무시
                                    }
                                }, 3000);
                            </script>
                        </body>
                        </html>
                        """.trimIndent(),
                        ContentType.Text.Html
                    )
                } catch (e: Exception) {
                    call.application.log.error("OAuth callback 처리 실패", e)
                    call.respondText(
                        "콜백 처리 중 오류 발생: ${e.message}",
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
        }
    }
}

