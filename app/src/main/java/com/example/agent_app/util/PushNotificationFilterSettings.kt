package com.example.agent_app.util

import android.content.Context

/**
 * 푸시 알림 저장 제외 앱 설정 관리
 *
 * - 사용자가 개발자 메뉴에서 특정 앱을 선택해
 *   "이 앱의 푸시알림은 저장하지 않기" 를 누르면
 *   해당 패키지명을 차단 목록에 추가한다.
 * - NotificationListenerService 에서는 이 설정을 읽어와
 *   목록에 포함된 패키지의 알림은 DB에 저장하지 않는다.
 */
object PushNotificationFilterSettings {

    private const val PREFS_NAME = "push_notification_filter_settings"
    private const val KEY_EXCLUDED_PACKAGES = "excluded_packages"
    private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
    private const val KEY_DEFAULTS_INITIALIZED = "defaults_initialized"

    private val DEFAULT_ALLOWED_PACKAGES = setOf(
        "com.google.android.gm",           // Gmail
        "com.nhn.android.mail",            // Naver Mail
        "com.nhn.android.search",          // Naver (일부 기기에서 메일 알림 제공)
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.kakao.talk",                  // 카카오톡
        "com.tencent.mm",                  // 위챗
        "com.whatsapp",                    // 왓츠앱
        "com.telegram.messenger"           // 텔레그램
    )
    
    // 스팸/광고 패턴 (제목이나 내용에 포함된 경우 차단)
    private val SPAM_PATTERNS = setOf(
        "광고", "홍보", "이벤트", "할인", "쿠폰", "적립", "포인트",
        "배달", "주문", "결제", "로그인", "인증", "보안", "알림",
        "업데이트", "설치", "다운로드", "앱", "게임", "무료",
        "당첨", "추천", "신청", "참여", "혜택", "특가", "세일"
    )
    
    // 시스템 알림 패턴
    private val SYSTEM_PATTERNS = setOf(
        "시스템", "system", "android", "google", "samsung",
        "battery", "배터리", "충전", "업데이트", "보안", "wifi",
        "bluetooth", "네트워크", "연결", "동기화", "백업"
    )

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun ensureDefaults(context: Context) {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_DEFAULTS_INITIALIZED, false)) {
            saveAllowedPackages(context, DEFAULT_ALLOWED_PACKAGES)
            prefs.edit()
                .putBoolean(KEY_DEFAULTS_INITIALIZED, true)
                .apply()
        }
    }

    fun getExcludedPackages(context: Context): Set<String> {
        val serialized = getPrefs(context).getString(KEY_EXCLUDED_PACKAGES, "") ?: ""
        if (serialized.isBlank()) return emptySet()
        return serialized.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun getAllowedPackages(context: Context): Set<String> {
        ensureDefaults(context)
        val serialized = getPrefs(context).getString(KEY_ALLOWED_PACKAGES, "") ?: ""
        if (serialized.isBlank()) return emptySet()
        return serialized.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun isPackageExcluded(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return true
        val allowed = getAllowedPackages(context)
        if (allowed.contains(packageName)) return false
        val excluded = getExcludedPackages(context)
        if (excluded.contains(packageName)) return true
        // 기본적으로 허용 목록에 없는 앱은 차단
        return true
    }
    
    /**
     * 알림 내용 기반 스팸 필터링
     * 일정 관련 키워드가 있거나 전화번호부에 있으면 스팸이 아님
     */
    fun isSpamNotification(
        title: String?, 
        text: String?, 
        packageName: String,
        senderPhoneNumber: String? = null,
        contactDao: com.example.agent_app.data.dao.ContactDao? = null
    ): Boolean {
        val content = "${title ?: ""} ${text ?: ""}".lowercase()
        
        // 전화번호부 확인 (전화번호가 있고 ContactDao가 제공된 경우)
        if (!senderPhoneNumber.isNullOrBlank() && contactDao != null) {
            try {
                val normalizedPhone = com.example.agent_app.util.PhoneNumberUtils.normalize(senderPhoneNumber)
                val contact = kotlinx.coroutines.runBlocking {
                    contactDao.findByPhoneNumber(senderPhoneNumber, normalizedPhone)
                }
                if (contact != null) {
                    android.util.Log.d("PushNotificationFilter", "전화번호부에 등록된 번호 - 스팸 아님: $senderPhoneNumber (${contact.name})")
                    return false
                }
            } catch (e: Exception) {
                android.util.Log.w("PushNotificationFilter", "전화번호부 확인 실패", e)
            }
        }
        
        // 일정 관련 키워드 체크 (일정 관련 메시지는 스팸이 아님)
        val eventKeywords = listOf(
            "다음주", "다음 주", "이번주", "이번 주", "내일", "모레", "오늘",
            "토요일", "일요일", "월요일", "화요일", "수요일", "목요일", "금요일",
            "일정", "약속", "회의", "만남", "만나", "보자", "가자", "갈까",
            "시", "분", "오전", "오후", "아침", "점심", "저녁", "밤"
        )
        
        // 일정 관련 키워드가 있으면 스팸이 아님
        if (eventKeywords.any { keyword -> content.contains(keyword) }) {
            android.util.Log.d("PushNotificationFilter", "일정 관련 키워드 발견 - 스팸 아님: $content")
            return false
        }
        
        // 스팸 패턴 체크
        if (SPAM_PATTERNS.any { pattern -> content.contains(pattern) }) {
            return true
        }
        
        // 시스템 알림 패턴 체크
        if (SYSTEM_PATTERNS.any { pattern -> content.contains(pattern) }) {
            return true
        }
        
        // 패키지명 기반 시스템 앱 체크 (단, 메시징 앱은 제외)
        val messagingApps = listOf(
            "com.samsung.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.kakao.talk"
        )
        if (packageName.startsWith("com.android.") || 
            packageName.startsWith("com.google.android.") ||
            (packageName.startsWith("com.samsung.android.") && !messagingApps.contains(packageName))) {
            return true
        }
        
        return false
    }

    fun addExcludedPackage(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        val current = getExcludedPackages(context).toMutableSet()
        if (current.add(packageName)) {
            saveExcludedPackages(context, current)
        }
    }

    fun removeExcludedPackage(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        val current = getExcludedPackages(context).toMutableSet()
        if (current.remove(packageName)) {
            saveExcludedPackages(context, current)
        }
    }

    fun addAllowedPackage(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        ensureDefaults(context)
        val current = getAllowedPackages(context).toMutableSet()
        if (current.add(packageName)) {
            saveAllowedPackages(context, current)
        }
    }

    fun removeAllowedPackage(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        ensureDefaults(context)
        val current = getAllowedPackages(context).toMutableSet()
        if (current.remove(packageName)) {
            saveAllowedPackages(context, current)
        }
    }

    private fun saveExcludedPackages(context: Context, packages: Set<String>) {
        val serialized = packages.joinToString(",")
        getPrefs(context).edit()
            .putString(KEY_EXCLUDED_PACKAGES, serialized)
            .apply()
    }

    private fun saveAllowedPackages(context: Context, packages: Set<String>) {
        val serialized = packages.joinToString(",")
        getPrefs(context).edit()
            .putString(KEY_ALLOWED_PACKAGES, serialized)
            .apply()
    }
}

