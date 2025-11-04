package com.example.agent_app.backend.config

import java.io.File
import java.util.Properties

/**
 * local.properties 파일에서 설정을 읽어오는 유틸리티
 */
object ConfigLoader {
    /**
     * 프로젝트 루트의 local.properties 파일을 찾습니다.
     * 여러 가능한 경로를 시도합니다.
     */
    private fun findLocalPropertiesFile(): File? {
        val currentDir = File(System.getProperty("user.dir"))
        
        // 시도할 경로 목록
        val possiblePaths = listOf(
            // 1. 현재 작업 디렉토리
            File(currentDir, "local.properties"),
            // 2. 프로젝트 루트 (backend/에서 실행 시)
            File(currentDir.parent, "local.properties"),
            // 3. 프로젝트 루트 (backend/build/classes 등에서 실행 시)
            File(currentDir.parentFile?.parentFile, "local.properties"),
            // 4. 절대 경로로 찾기 (프로젝트가 AndroidStudioProjects 하위에 있다고 가정)
            File(System.getProperty("user.home"), "AndroidStudioProjects/Agent_App/local.properties")
        )
        
        return possiblePaths.firstOrNull { it.exists() && it.isFile }
    }
    
    /**
     * local.properties 파일을 읽어서 Properties 객체로 반환
     */
    fun loadLocalProperties(): Properties {
        val properties = Properties()
        val localPropertiesFile = findLocalPropertiesFile()
        
        if (localPropertiesFile != null && localPropertiesFile.exists()) {
            try {
                localPropertiesFile.inputStream().use { properties.load(it) }
            } catch (e: Exception) {
                System.err.println("Warning: Failed to load local.properties from ${localPropertiesFile.absolutePath}: ${e.message}")
            }
        } else {
            System.err.println("Warning: local.properties file not found. Tried paths:")
            val currentDir = File(System.getProperty("user.dir"))
            System.err.println("  - Current dir: ${currentDir.absolutePath}")
            System.err.println("  - ${File(currentDir, "local.properties").absolutePath}")
            System.err.println("  - ${File(currentDir.parent, "local.properties").absolutePath}")
        }
        
        return properties
    }
    
    /**
     * 환경 변수 또는 local.properties에서 값을 가져옵니다.
     * 환경 변수가 우선이고, 없으면 local.properties에서 읽습니다.
     */
    fun getProperty(key: String, defaultValue: String? = null): String? {
        // 1. 환경 변수에서 먼저 확인
        System.getenv(key)?.let { return it }
        
        // 2. local.properties에서 확인
        val localProps = loadLocalProperties()
        localProps.getProperty(key)?.let { return it }
        
        // 3. 기본값 반환
        return defaultValue
    }
}

