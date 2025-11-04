package com.example.agent_app.backend

import android.content.Context
import com.example.agent_app.util.BackendConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object AdminBackendServiceFactory {
    /**
     * 백엔드 API 서비스 생성
     * 
     * @param context Android 컨텍스트 (설정 읽기용)
     * @param enableLogging 로깅 활성화 여부
     * @return AdminBackendApi 인스턴스
     */
    fun create(context: Context, enableLogging: Boolean = true): AdminBackendApi {
        // BackendConfig를 사용하여 URL 가져오기 (자동으로 에뮬레이터 감지)
        val baseUrl = BackendConfig.getBackendUrl(context, BackendConfig.isEmulator())
        
        return createWithUrl(baseUrl, enableLogging)
    }
    
    /**
     * 특정 URL로 백엔드 API 서비스 생성
     * 
     * @param baseUrl 백엔드 서버 URL
     * @param enableLogging 로깅 활성화 여부
     * @return AdminBackendApi 인스턴스
     */
    fun createWithUrl(baseUrl: String, enableLogging: Boolean = true): AdminBackendApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (enableLogging) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
        
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = true
        }
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        
        return retrofit.create(AdminBackendApi::class.java)
    }
}

