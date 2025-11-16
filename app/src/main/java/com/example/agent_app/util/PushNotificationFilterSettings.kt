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

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getExcludedPackages(context: Context): Set<String> {
        val serialized = getPrefs(context).getString(KEY_EXCLUDED_PACKAGES, "") ?: ""
        if (serialized.isBlank()) return emptySet()
        return serialized.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun isPackageExcluded(context: Context, packageName: String): Boolean {
        return getExcludedPackages(context).contains(packageName)
    }

    fun addExcludedPackage(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        val current = getExcludedPackages(context).toMutableSet()
        if (current.add(packageName)) {
            save(context, current)
        }
    }

    fun removeExcludedPackage(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        val current = getExcludedPackages(context).toMutableSet()
        if (current.remove(packageName)) {
            save(context, current)
        }
    }

    private fun save(context: Context, packages: Set<String>) {
        val serialized = packages.joinToString(",")
        getPrefs(context).edit()
            .putString(KEY_EXCLUDED_PACKAGES, serialized)
            .apply()
    }
}


