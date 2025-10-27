package com.example.agent_app.ai

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Few-shot Learning 예시를 JSON 파일에서 로드하는 유틸리티
 */
class FewShotExampleLoader(private val context: Context) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    /**
     * OCR Few-shot 예시 로드
     */
    fun loadOcrExamples(): FewShotExamples {
        return try {
            android.util.Log.d("FewShotExampleLoader", "리소스 ID 조회 시작...")
            android.util.Log.d("FewShotExampleLoader", "패키지 이름: ${context.packageName}")
            
            val resourceId = context.resources.getIdentifier(
                "ocr_few_shot_examples",
                "raw",
                context.packageName
            )
            
            android.util.Log.d("FewShotExampleLoader", "리소스 ID: $resourceId")
            
            if (resourceId == 0) {
                android.util.Log.e("FewShotExampleLoader", "❌ OCR Few-shot 예시 파일을 찾을 수 없습니다!")
                android.util.Log.e("FewShotExampleLoader", "경로: res/raw/ocr_few_shot_examples.json")
                return FewShotExamples(emptyList(), emptyList())
            }
            
            android.util.Log.d("FewShotExampleLoader", "파일 읽기 시작...")
            val inputStream = context.resources.openRawResource(resourceId)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            
            android.util.Log.d("FewShotExampleLoader", "JSON 크기: ${jsonString.length}자")
            android.util.Log.d("FewShotExampleLoader", "JSON 미리보기: ${jsonString.take(100)}")
            
            android.util.Log.d("FewShotExampleLoader", "JSON 파싱 시작...")
            val result = json.decodeFromString<FewShotExamples>(jsonString)
            
            android.util.Log.d("FewShotExampleLoader", "✅ 파싱 성공! 예시 ${result.examples.size}개, 규칙 ${result.commonRules.size}개")
            result
        } catch (e: Exception) {
            android.util.Log.e("FewShotExampleLoader", "❌ Few-shot 예시 로드 실패!", e)
            android.util.Log.e("FewShotExampleLoader", "에러 타입: ${e.javaClass.simpleName}")
            android.util.Log.e("FewShotExampleLoader", "에러 메시지: ${e.message}")
            e.printStackTrace()
            FewShotExamples(emptyList(), emptyList())
        }
    }
    
    /**
     * Few-shot 예시를 프롬프트 형식으로 변환
     */
    fun formatExamplesForPrompt(examples: FewShotExamples): String {
        if (examples.examples.isEmpty()) {
            return ""
        }
        
        val formattedExamples = examples.examples.joinToString("\n\n") { example ->
            buildString {
                appendLine("**예시: ${example.name}**")
                appendLine("OCR: \"${example.ocrText}\"")
                appendLine()
                appendLine("**사고 과정:**")
                example.thoughtProcess.forEachIndexed { index, thought ->
                    appendLine("${index + 1}. $thought")
                }
                appendLine()
                appendLine("**결과:**")
                appendLine("- 기준 날짜: ${example.result.baseDate}")
                example.result.calculatedDate?.let { appendLine("- 계산된 날짜: $it") }
                example.result.startEpoch?.let { appendLine("- 시작: $it") }
                example.result.endEpoch?.let { appendLine("- 종료: $it") }
                appendLine("- 제목: ${example.result.title}")
                example.result.location?.let { appendLine("- 장소: $it") }
                
                if (example.wrongCalculations.isNotEmpty()) {
                    appendLine()
                    appendLine("⛔ **절대 금지:**")
                    example.wrongCalculations.forEach { appendLine(it) }
                }
                
                appendLine()
                appendLine(example.correctRule)
            }
        }
        
        val commonRules = if (examples.commonRules.isNotEmpty()) {
            "\n\n🔴 **공통 규칙:**\n" + examples.commonRules.joinToString("\n") { "- $it" }
        } else {
            ""
        }
        
        return """
            |🎯 **Few-shot Learning 예시:**
            |
            |$formattedExamples
            |$commonRules
        """.trimMargin()
    }
}

@Serializable
data class FewShotExamples(
    val examples: List<FewShotExample>,
    val commonRules: List<String> = emptyList()
)

@Serializable
data class FewShotExample(
    val name: String,
    val description: String,
    val ocrText: String,
    val thoughtProcess: List<String>,
    val result: ExampleResult,
    val wrongCalculations: List<String> = emptyList(),
    val correctRule: String
)

@Serializable
data class ExampleResult(
    val baseDate: String,
    val calculatedDate: String? = null,
    val startEpoch: Long? = null,
    val endEpoch: Long? = null,
    val title: String,
    val location: String? = null,
    val time: String? = null
)

