package com.example.agent_app.service

import android.content.Context
import android.util.Log
import com.example.agent_app.data.repo.OcrRepositoryWithAi
import com.example.agent_app.util.CallRecordTextReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 삼성 통화 녹음 텍스트 파일을 처리하여 일정을 추출하는 서비스
 * 삼성 폰은 통화 녹음을 자동으로 STT 변환하여 텍스트 파일로 저장합니다.
 */
class CallRecordTextProcessor(
    private val context: Context,
    private val ocrRepository: OcrRepositoryWithAi
) {
    private val TAG = "CallRecordTextProcessor"
    
    /**
     * 최근 통화 녹음 텍스트 파일들을 처리하여 일정 추출
     * 
     * @param sinceTimestamp 이 시간 이후의 파일만 처리
     * @param onProgress 진행 상황 콜백 (파일명, 처리 중 여부)
     * @return 처리 결과 (성공한 파일 수, 실패한 파일 수, 추출된 일정 수)
     */
    suspend fun processRecentCallRecordTexts(
        sinceTimestamp: Long = 0L,
        onProgress: ((String, Boolean) -> Unit)? = null
    ): CallRecordProcessingResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "통화 녹음 텍스트 파일 처리 시작 (since: $sinceTimestamp)")
            
            // 최근 통화 녹음 텍스트 파일 찾기
            val textFiles = CallRecordTextReader.findRecentCallRecordTexts(
                context = context,
                sinceTimestamp = sinceTimestamp,
                limit = 20
            )
            
            Log.d(TAG, "${textFiles.size}개의 통화 녹음 텍스트 파일 발견")
            
            var successCount = 0
            var failureCount = 0
            var eventCount = 0
            
            for (file in textFiles) {
                try {
                    onProgress?.invoke(file.name, true)
                    
                    Log.d(TAG, "통화 녹음 텍스트 처리 중: ${file.name}")
                    
                    // 1. 텍스트 파일 읽기
                    val textContent = CallRecordTextReader.readTextFile(file)
                    
                    if (textContent.isNullOrBlank()) {
                        Log.w(TAG, "텍스트 파일이 비어있거나 읽을 수 없음: ${file.name}")
                        onProgress?.invoke(file.name, false)
                        failureCount++
                        continue
                    }
                    
                    Log.d(TAG, "텍스트 파일 읽기 완료: ${textContent.length}자 - ${textContent.take(100)}...")
                    
                    // 2. 텍스트에서 일정 추출 및 저장 (경로 1: OcrRepositoryWithAi 사용)
                    val result = ocrRepository.processOcrText(
                        ocrText = textContent,
                        source = "call_record_text"
                    )
                    
                    if (result.success && result.totalEventCount > 0) {
                        Log.d(TAG, "일정 추출 및 저장 완료: ${result.totalEventCount}개의 일정 생성")
                        successCount++
                        eventCount += result.totalEventCount
                    } else {
                        Log.w(TAG, "일정 추출 실패 또는 일정 없음")
                        failureCount++
                    }
                    onProgress?.invoke(file.name, false)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "통화 녹음 텍스트 처리 실패: ${file.name}", e)
                    failureCount++
                    onProgress?.invoke(file.name, false)
                }
            }
            
            Log.d(TAG, "통화 녹음 텍스트 처리 완료: 성공 ${successCount}개, 실패 ${failureCount}개, 일정 ${eventCount}개")
            
            CallRecordProcessingResult(
                successCount = successCount,
                failureCount = failureCount,
                eventCount = eventCount
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "통화 녹음 텍스트 파일 처리 중 오류", e)
            throw e
        }
    }
    
    /**
     * 특정 통화 녹음 텍스트 파일 처리
     */
    suspend fun processCallRecordText(
        fileId: String,
        onProgress: ((String, Boolean) -> Unit)? = null
    ): com.example.agent_app.data.repo.OcrProcessingResult = withContext(Dispatchers.IO) {
        try {
            val files = CallRecordTextReader.findRecentCallRecordTexts(context, 0L, 100)
            val file = files.find { it.id == fileId }
                ?: throw IllegalArgumentException("통화 녹음 텍스트 파일을 찾을 수 없습니다: $fileId")
            
            onProgress?.invoke(file.name, true)
            
            // 텍스트 파일 읽기
            val textContent = CallRecordTextReader.readTextFile(file)
                ?: throw IllegalArgumentException("텍스트 파일을 읽을 수 없습니다")
            
            // 일정 추출 및 저장 (경로 1: OcrRepositoryWithAi 사용)
            val result = ocrRepository.processOcrText(
                ocrText = textContent,
                source = "call_record_text"
            )
            
            onProgress?.invoke(file.name, false)
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "통화 녹음 텍스트 파일 처리 실패: $fileId", e)
            onProgress?.invoke("", false)
            throw e
        }
    }
}

/**
 * 통화 녹음 텍스트 처리 결과
 */
data class CallRecordProcessingResult(
    val successCount: Int,
    val failureCount: Int,
    val eventCount: Int
)

