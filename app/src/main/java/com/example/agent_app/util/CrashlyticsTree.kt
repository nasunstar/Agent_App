package com.example.agent_app.util

import timber.log.Timber

/**
 * MOA-Logging: Crashlytics로 로그를 전송하는 Timber Tree
 * Release 빌드에서 사용
 * 
 * Firebase가 없어도 안전하게 작동 (try-catch로 보호)
 */
class CrashlyticsTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // ERROR 레벨 이상만 Crashlytics로 전송
        if (priority >= android.util.Log.ERROR) {
            try {
                // Firebase가 초기화되지 않았을 수 있으므로 안전하게 처리
                val crashlytics = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                
                // 메시지 전송
                crashlytics.log("[$tag] $message")
                
                // 예외가 있으면 예외 정보도 전송
                if (t != null) {
                    crashlytics.recordException(t)
                } else {
                    // 예외가 없어도 에러 로그는 기록
                    crashlytics.recordException(Exception(message))
                }
            } catch (e: Exception) {
                // Firebase가 없어도 앱은 정상 작동 (조용히 무시)
                // Debug 빌드에서는 로그 출력
                if (android.util.Log.isLoggable("CrashlyticsTree", android.util.Log.DEBUG)) {
                    android.util.Log.d("CrashlyticsTree", "Firebase Crashlytics 사용 불가 (무시됨): ${e.message}")
                }
            }
        }
    }
}

