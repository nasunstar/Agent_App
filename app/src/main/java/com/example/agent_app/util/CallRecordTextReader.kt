package com.example.agent_app.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream

/**
 * 삼성 통화 녹음 STT 텍스트 파일을 찾고 읽는 유틸리티
 * 삼성 갤럭시는 통화 녹음을 자동으로 STT 변환하여 텍스트 파일로 저장합니다.
 */
object CallRecordTextReader {
    private const val TAG = "CallRecordTextReader"
    
    // 삼성 통화 녹음 파일이 저장되는 일반적인 경로
    private val samsungCallRecordPaths = listOf(
        "/storage/emulated/0/Sounds/",
        "/storage/emulated/0/CallRecord/",
        "/storage/emulated/0/Record/",
        "/storage/emulated/0/VoiceRecorder/",
        "/storage/emulated/0/Android/data/com.samsung.android.app.voicenote/files/",
        "/storage/emulated/0/Android/data/com.samsung.android.app.voicenote/cache/",
    )
    
    /**
     * 최근 통화 녹음 텍스트 파일 목록 조회
     * 
     * @param context Context
     * @param sinceTimestamp 이 시간 이후의 파일만 조회 (epoch milliseconds)
     * @param limit 최대 조회 개수
     * @return 통화 녹음 텍스트 파일 정보 리스트
     */
    fun findRecentCallRecordTexts(
        context: Context,
        sinceTimestamp: Long = 0L,
        limit: Int = 20
    ): List<CallRecordTextFile> {
        val files = mutableListOf<CallRecordTextFile>()
        
        try {
            // 방법 1: 직접 파일 시스템 탐색 (삼성 특화 경로)
            files.addAll(findCallRecordTextsViaFileSystem(sinceTimestamp, limit))
            
            // 방법 2: MediaStore를 통해 텍스트 파일 조회 (보조)
            if (files.size < limit) {
                files.addAll(findCallRecordTextsViaMediaStore(context, sinceTimestamp, limit - files.size))
            }
            
            // 최신순 정렬
            files.sortByDescending { it.modifiedTime }
            
            Log.d(TAG, "통화 녹음 텍스트 파일 ${files.size}개 발견")
            
        } catch (e: Exception) {
            Log.e(TAG, "통화 녹음 텍스트 파일 조회 실패", e)
        }
        
        return files.take(limit)
    }
    
    /**
     * 파일 시스템을 직접 탐색하여 통화 녹음 텍스트 파일 찾기
     * 삼성 통화 녹음은 오디오 파일(.mp3, .m4a 등)과 함께 STT 텍스트 파일(.txt)을 저장합니다.
     */
    private fun findCallRecordTextsViaFileSystem(
        sinceTimestamp: Long,
        limit: Int
    ): List<CallRecordTextFile> {
        val files = mutableListOf<CallRecordTextFile>()
        
        for (basePath in samsungCallRecordPaths) {
            try {
                val directory = File(basePath)
                if (!directory.exists() || !directory.isDirectory) {
                    continue
                }
                
                // 1단계: 오디오 파일 찾기
                val audioFiles = mutableListOf<File>()
                directory.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val fileName = file.name.lowercase()
                        // 통화 녹음 오디오 파일 확인
                        if (fileName.endsWith(".mp3", ignoreCase = true) ||
                            fileName.endsWith(".m4a", ignoreCase = true) ||
                            fileName.endsWith(".3gp", ignoreCase = true) ||
                            fileName.endsWith(".amr", ignoreCase = true)) {
                            
                            val modifiedTime = file.lastModified()
                            if (modifiedTime >= sinceTimestamp) {
                                audioFiles.add(file)
                            }
                        }
                    }
                }
                
                // 2단계: 각 오디오 파일에서 STT 텍스트 찾기
                for (audioFile in audioFiles.take(limit)) {
                    if (files.size >= limit) {
                        break
                    }
                    
                    val audioFileName = audioFile.nameWithoutExtension
                    val audioFileDir = audioFile.parentFile
                    
                    // 방법 1: 오디오 파일 메타데이터에서 STT 텍스트 읽기
                    val metadataText = extractSttFromAudioMetadata(audioFile)
                    if (!metadataText.isNullOrBlank()) {
                        // 메타데이터에서 찾은 텍스트를 임시 파일로 저장하거나 직접 사용
                        files.add(
                            CallRecordTextFile(
                                id = "${audioFile.name}_metadata",
                                name = "${audioFileName}_STT.txt",
                                path = audioFile.absolutePath, // 원본 오디오 파일 경로 (메타데이터에서 추출)
                                uri = Uri.fromFile(audioFile),
                                modifiedTime = audioFile.lastModified(),
                                size = metadataText.length.toLong(),
                                metadataText = metadataText // 메타데이터에서 추출한 텍스트
                            )
                        )
                        Log.d(TAG, "통화 녹음 STT 텍스트 발견 (메타데이터): ${audioFile.name}")
                        continue
                    }
                    
                    // 방법 2: 같은 이름의 .txt 파일 찾기
                    val txtFile = File(audioFileDir, "$audioFileName.txt")
                    if (txtFile.exists() && txtFile.canRead()) {
                        val textContent = try {
                            txtFile.readText()
                        } catch (e: Exception) {
                            null
                        }
                        
                        if (!textContent.isNullOrBlank()) {
                            files.add(
                                CallRecordTextFile(
                                    id = txtFile.name,
                                    name = txtFile.name,
                                    path = txtFile.absolutePath,
                                    uri = Uri.fromFile(txtFile),
                                    modifiedTime = txtFile.lastModified(),
                                    size = txtFile.length()
                                )
                            )
                            Log.d(TAG, "통화 녹음 STT 텍스트 발견: ${txtFile.name} (오디오: ${audioFile.name})")
                            continue
                        }
                    }
                    
                    // 방법 3: 같은 디렉토리에서 비슷한 이름의 텍스트 파일 찾기
                    audioFileDir?.listFiles()?.forEach { file ->
                        if (files.size >= limit) {
                            return@forEach
                        }
                        
                        if (file.isFile && file.name.lowercase().endsWith(".txt")) {
                            val fileName = file.nameWithoutExtension.lowercase()
                            // 오디오 파일 이름과 유사한 텍스트 파일 (예: "Call_20250104_123456.txt" vs "Call_20250104_123456.mp3")
                            if (fileName.contains(audioFileName.lowercase().take(10)) || 
                                audioFileName.lowercase().contains(fileName.take(10))) {
                                
                                val textContent = try {
                                    file.readText()
                                } catch (e: Exception) {
                                    null
                                }
                                
                                if (!textContent.isNullOrBlank() && textContent.length > 50) {
                                    files.add(
                                        CallRecordTextFile(
                                            id = file.name,
                                            name = file.name,
                                            path = file.absolutePath,
                                            uri = Uri.fromFile(file),
                                            modifiedTime = file.lastModified(),
                                            size = file.length()
                                        )
                                    )
                                    Log.d(TAG, "통화 녹음 STT 텍스트 발견 (유사 이름): ${file.name}")
                                }
                            }
                        }
                    }
                }
                
                // 3단계: 독립적인 텍스트 파일도 찾기 (오디오 파일과 연결되지 않은 경우)
                directory.walkTopDown().forEach { file ->
                    if (files.size >= limit) {
                        return@forEach
                    }
                    
                    if (file.isFile) {
                        val fileName = file.name.lowercase()
                        // 통화 녹음 관련 텍스트 파일
                        if ((fileName.endsWith(".txt") || fileName.endsWith(".text")) &&
                            (fileName.contains("call") || 
                             fileName.contains("통화") ||
                             fileName.contains("record") ||
                             fileName.contains("녹음"))) {
                            
                            val modifiedTime = file.lastModified()
                            if (modifiedTime >= sinceTimestamp) {
                                // 이미 추가된 파일인지 확인
                                if (files.any { it.path == file.absolutePath }) {
                                    return@forEach
                                }
                                
                                // 파일 내용 확인
                                val content = try {
                                    file.readText().take(200)
                                } catch (e: Exception) {
                                    ""
                                }
                                
                                // 충분한 길이의 텍스트인지 확인
                                if (content.isNotBlank() && content.length > 50) {
                                    files.add(
                                        CallRecordTextFile(
                                            id = file.name,
                                            name = file.name,
                                            path = file.absolutePath,
                                            uri = Uri.fromFile(file),
                                            modifiedTime = modifiedTime,
                                            size = file.length()
                                        )
                                    )
                                    Log.d(TAG, "통화 녹음 텍스트 발견 (독립 파일): ${file.name}")
                                }
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "파일 시스템 탐색 실패: $basePath", e)
            }
        }
        
        return files
    }
    
    /**
     * MediaStore를 통해 텍스트 파일 찾기
     */
    private fun findCallRecordTextsViaMediaStore(
        context: Context,
        sinceTimestamp: Long,
        limit: Int
    ): List<CallRecordTextFile> {
        val files = mutableListOf<CallRecordTextFile>()
        
        try {
            val contentResolver: ContentResolver = context.contentResolver
            
            // Android 10 이상에서는 MediaStore.Downloads 사용
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }
            
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.SIZE,
            )
            
            val selection = "${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ? AND " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? AND " +
                    "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
            
            val selectionArgs = arrayOf(
                (sinceTimestamp / 1000).toString(), // 초 단위로 변환
                "%Call%",
                "text/plain"
            )
            
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT $limit"
            
            val cursor: Cursor? = contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                
                while (it.moveToNext() && files.size < limit) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val path = it.getString(dataColumn)
                    val modifiedTime = it.getLong(dateColumn) * 1000 // 밀리초로 변환
                    val size = it.getLong(sizeColumn)
                    
                    files.add(
                        CallRecordTextFile(
                            id = id.toString(),
                            name = name,
                            path = path,
                            uri = Uri.withAppendedPath(uri, id.toString()),
                            modifiedTime = modifiedTime,
                            size = size
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore 조회 실패", e)
        }
        
        return files
    }
    
    /**
     * 오디오 파일의 메타데이터에서 STT 텍스트 추출
     * 삼성 통화 녹음은 오디오 파일의 메타데이터에 STT 텍스트를 저장할 수 있습니다.
     */
    private fun extractSttFromAudioMetadata(audioFile: File): String? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioFile.absolutePath)
            
            // 다양한 메타데이터 필드에서 STT 텍스트 찾기
            val sttText = buildString {
                // MediaMetadataRetriever는 커스텀 키를 직접 지원하지 않으므로
                // 표준 필드에서 찾기
                
                // 방법 1: TITLE 필드 (제목에 STT 일부 포함 가능)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                    if (it.isNotBlank() && it.length > 100) {
                        append(it)
                        append("\n")
                    }
                }
                
                // 방법 2: ARTIST 필드 (일부 경우)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                    if (it.isNotBlank() && it.length > 100) {
                        append(it)
                        append("\n")
                    }
                }
                
                // 방법 3: ALBUM 필드
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let {
                    if (it.isNotBlank() && it.length > 100) {
                        append(it)
                        append("\n")
                    }
                }
                
                // 방법 4: GENRE 필드 (일부 경우)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let {
                    if (it.isNotBlank() && it.length > 100) {
                        append(it)
                        append("\n")
                    }
                }
            }.trim()
            
            if (sttText.isNotBlank() && sttText.length > 50) {
                Log.d(TAG, "오디오 메타데이터에서 STT 텍스트 추출: ${audioFile.name} (${sttText.length}자)")
                sttText
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "오디오 메타데이터 읽기 실패: ${audioFile.name}", e)
            null
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                Log.e(TAG, "MediaMetadataRetriever 해제 실패", e)
            }
        }
    }
    
    /**
     * 텍스트 파일 내용 읽기
     * 메타데이터에서 추출한 텍스트가 있으면 그것을 우선 사용
     */
    fun readTextFile(file: CallRecordTextFile): String? {
        // 메타데이터에서 추출한 텍스트가 있으면 우선 사용
        if (file.metadataText != null) {
            return file.metadataText
        }
        
        // 일반 텍스트 파일 읽기
        return try {
            val fileObj = File(file.path)
            if (fileObj.exists() && fileObj.canRead()) {
                fileObj.readText()
            } else {
                Log.w(TAG, "파일 읽기 불가: ${file.path}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "텍스트 파일 읽기 실패: ${file.path}", e)
            null
        }
    }
}

/**
 * 통화 녹음 텍스트 파일 정보
 */
data class CallRecordTextFile(
    val id: String,
    val name: String,
    val path: String,
    val uri: Uri,
    val modifiedTime: Long,
    val size: Long,
    val metadataText: String? = null // 오디오 파일 메타데이터에서 추출한 STT 텍스트
)

