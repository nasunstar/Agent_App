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
        "com.android.mms"
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

