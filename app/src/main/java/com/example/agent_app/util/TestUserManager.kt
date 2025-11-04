package com.example.agent_app.util

import android.content.Context
import android.content.SharedPreferences

object TestUserManager {
    private const val PREFS_NAME = "test_users"
    private const val KEY_TEST_USERS = "test_user_emails"
    
    // 기본 테스트 사용자 목록
    private val DEFAULT_TEST_USERS = setOf(
        "wjswod8@gmail.com",
        // 추가 테스트 사용자 이메일을 여기에 추가
    )
    
    /**
     * 테스트 사용자 목록 가져오기
     */
    fun getTestUsers(context: Context): Set<String> {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_TEST_USERS, null)
        
        return if (saved != null) {
            saved
        } else {
            // 기본값 저장
            prefs.edit().putStringSet(KEY_TEST_USERS, DEFAULT_TEST_USERS).apply()
            DEFAULT_TEST_USERS
        }
    }
    
    /**
     * 테스트 사용자 추가
     */
    fun addTestUser(context: Context, email: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_TEST_USERS, DEFAULT_TEST_USERS)?.toMutableSet() ?: mutableSetOf()
        current.add(email.lowercase().trim())
        prefs.edit().putStringSet(KEY_TEST_USERS, current).apply()
    }
    
    /**
     * 테스트 사용자 제거
     */
    fun removeTestUser(context: Context, email: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_TEST_USERS, DEFAULT_TEST_USERS)?.toMutableSet() ?: mutableSetOf()
        current.remove(email.lowercase().trim())
        prefs.edit().putStringSet(KEY_TEST_USERS, current).apply()
    }
    
    /**
     * 테스트 사용자인지 확인
     */
    fun isTestUser(context: Context, email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val testUsers = getTestUsers(context)
        return testUsers.contains(email.lowercase().trim())
    }
}

