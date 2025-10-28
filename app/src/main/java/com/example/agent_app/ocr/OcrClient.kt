package com.example.agent_app.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrClient(context: Context) {

    // 한글 인식을 위해 KoreanTextRecognizerOptions 사용
    private val textRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val appContext = context.applicationContext

    suspend fun recognizeTextFromUri(uri: Uri): String = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromFilePath(appContext, uri)
            textRecognizer
                .process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        // 텍스트 블록을 Y 좌표(위에서 아래) 기준으로 정렬
                        val sortedText = result.textBlocks
                            .sortedBy { block -> 
                                // boundingBox의 top (Y 좌표)를 기준으로 정렬
                                block.boundingBox?.top ?: 0
                            }
                            .flatMap { block ->
                                // 각 블록 내의 줄도 Y 좌표로 정렬
                                block.lines.sortedBy { line ->
                                    line.boundingBox?.top ?: 0
                                }
                            }
                            .joinToString("\n") { line -> line.text }
                        
                        continuation.resume(sortedText)
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
        } catch (error: Exception) {
            if (continuation.isActive) {
                continuation.resumeWithException(error)
            }
        }
    }

    fun close() {
        textRecognizer.close()
    }
}

