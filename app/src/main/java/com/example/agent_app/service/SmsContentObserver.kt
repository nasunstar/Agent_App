package com.example.agent_app.service

import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.provider.Telephony
import android.util.Log
import com.example.agent_app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SMS 데이터베이스 변경을 모니터링하는 ContentObserver
 */
class SmsContentObserver(
    private val context: android.content.Context,
    handler: Handler
) : ContentObserver(handler) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastCheckTime: Long = System.currentTimeMillis()
    
    companion object {
        private const val TAG = "SmsContentObserver"
        private const val DEBOUNCE_MS = 2000L // 2초 디바운싱
    }
    
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        Log.d(TAG, "🔔 SMS 데이터베이스 변경 감지 - uri: $uri, selfChange: $selfChange")
        
        // 디바운싱
        val now = System.currentTimeMillis()
        if (now - lastCheckTime < DEBOUNCE_MS) {
            Log.d(TAG, "⏸️ 디바운싱: ${now - lastCheckTime}ms 경과, 건너뜀")
            return
        }
        lastCheckTime = now
        
        scope.launch {
            try {
                // SMS 저장 완료 대기
                delay(1500)
                
                // SMS 권한 확인 (먼저 확인)
                val hasSmsPermission = android.content.pm.PackageManager.PERMISSION_GRANTED == 
                    context.checkSelfPermission(android.Manifest.permission.READ_SMS)
                Log.d(TAG, "🔐 SMS 읽기 권한 확인: ${if (hasSmsPermission) "✅ 있음" else "❌ 없음"}")
                
                if (!hasSmsPermission) {
                    Log.w(TAG, "⚠️ SMS 읽기 권한이 없습니다. 설정에서 권한을 허용해주세요.")
                    return@launch
                }
                
                // 자동 처리 활성화 여부 확인
                val isAutoProcessEnabled = com.example.agent_app.util.AutoProcessSettings.isSmsAutoProcessEnabled(context)
                if (!isAutoProcessEnabled) {
                    Log.d(TAG, "⚠️ SMS 자동 처리 비활성화 - 건너뜀")
                    return@launch
                }
                
                // 최근 2일(오늘 + 어제) 기준으로 SMS 읽기
                val today = java.time.LocalDate.now()
                val yesterday = today.minusDays(1)
                val yesterdayStart = yesterday.atStartOfDay(java.time.ZoneId.systemDefault())
                val sinceTimestamp = yesterdayStart.toInstant().toEpochMilli()
                val now = System.currentTimeMillis()
                
                Log.d(TAG, "📅 최근 2일(어제+오늘) 기준 SMS 읽기 - 시작: $sinceTimestamp (${yesterdayStart.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}), 현재: $now")
                
                // SMS 읽기
                val readResult = readTodaySms(sinceTimestamp, now)
                
                when (readResult) {
                    is com.example.agent_app.util.SmsReader.SmsReadResult.Success -> {
                        val messages = readResult.messages
                        Log.d(TAG, "✅ 오늘 SMS 읽기 성공 - ${messages.size}개")
                        
                        if (messages.isNotEmpty()) {
                            processSmsMessages(messages)
                        } else {
                            Log.d(TAG, "ℹ️ 오늘 SMS 없음")
                        }
                    }
                    is com.example.agent_app.util.SmsReader.SmsReadResult.Error -> {
                        Log.e(TAG, "❌ SMS 읽기 실패: ${readResult.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ SMS 변경 처리 실패", e)
            }
        }
    }
    
    /**
     * 오늘 SMS 읽기 (간단한 버전)
     */
    private suspend fun readTodaySms(sinceTimestamp: Long, windowEnd: Long): com.example.agent_app.util.SmsReader.SmsReadResult {
        // SMS 권한 확인 (이중 확인)
        val permissionStatus = context.checkSelfPermission(android.Manifest.permission.READ_SMS)
        val hasPermission = permissionStatus == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "🔐 SMS 읽기 권한 재확인: ${if (hasPermission) "✅ 허용됨" else "❌ 거부됨 (상태: $permissionStatus)"}")
        
        if (!hasPermission) {
            Log.e(TAG, "❌ SMS 읽기 권한이 없습니다. 설정 > 앱 > 권한에서 SMS 읽기 권한을 허용해주세요.")
            return com.example.agent_app.util.SmsReader.SmsReadResult.Error(
                errorType = com.example.agent_app.util.SmsReader.SmsReadError.PERMISSION_DENIED,
                message = "SMS 읽기 권한이 없습니다. 설정에서 권한을 허용해주세요."
            )
        }
        
        val contentResolver = context.contentResolver
        val messages = mutableListOf<com.example.agent_app.util.SmsMessage>()
        
        try {
            // URI 시도 (Inbox, Sent, 전체 순서로)
            val urisToTry = listOf(
                Telephony.Sms.Inbox.CONTENT_URI,  // 받은 메시지
                Telephony.Sms.Sent.CONTENT_URI,   // 보낸 메시지
                Telephony.Sms.CONTENT_URI         // 전체 SMS
            )
            
            var cursor: Cursor? = null
            var successfulUri: Uri? = null
            
            for (uri in urisToTry) {
                try {
                    Log.d(TAG, "📋 SMS URI 시도: $uri (sinceTimestamp: $sinceTimestamp, windowEnd: $windowEnd)")
                    cursor = contentResolver.query(
                        uri,
                        arrayOf(
                            Telephony.Sms._ID,
                            Telephony.Sms.ADDRESS,
                            Telephony.Sms.BODY,
                            Telephony.Sms.DATE,
                            Telephony.Sms.DATE_SENT
                        ),
                        "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
                        arrayOf(sinceTimestamp.toString(), windowEnd.toString()),
                        "${Telephony.Sms.DATE} DESC"
                    )
                    
                    if (cursor != null) {
                        val count = cursor.count
                        Log.d(TAG, "✅ URI $uri 성공 - Cursor 행 수: $count")
                        successfulUri = uri
                        // count가 0이어도 성공 (오늘 SMS가 없을 수 있음)
                        break
                    } else {
                        Log.w(TAG, "⚠️ URI $uri - cursor가 null")
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "⚠️ URI $uri 권한 오류: ${e.message}")
                    cursor?.close()
                    cursor = null
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "⚠️ URI $uri ContentProvider 오류: ${e.message}")
                    cursor?.close()
                    cursor = null
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ URI $uri 오류: ${e.message}", e)
                    cursor?.close()
                    cursor = null
                }
            }
            
            if (cursor == null) {
                Log.e(TAG, "❌ 모든 URI 시도 실패 - SMS 데이터베이스 접근 불가")
                return com.example.agent_app.util.SmsReader.SmsReadResult.Error(
                    errorType = com.example.agent_app.util.SmsReader.SmsReadError.CONTENT_PROVIDER_ERROR,
                    message = "SMS 데이터베이스 접근 실패"
                )
            }
            
            // Cursor 읽기
            cursor.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val dateSentIndex = it.getColumnIndex(Telephony.Sms.DATE_SENT)
                
                if (idIndex < 0 || bodyIndex < 0 || dateIndex < 0) {
                    return com.example.agent_app.util.SmsReader.SmsReadResult.Error(
                        errorType = com.example.agent_app.util.SmsReader.SmsReadError.DATA_ERROR,
                        message = "필수 컬럼을 찾을 수 없습니다"
                    )
                }
                
                while (it.moveToNext()) {
                    try {
                        val id = it.getString(idIndex)
                        val address = if (addressIndex >= 0) it.getString(addressIndex) else null
                        val body = it.getString(bodyIndex)
                        val date = it.getLong(dateIndex)
                        // 받은 메시지는 DATE(수신 시간)를 사용, DATE_SENT는 발신 시간이므로 받은 메시지에서는 부정확할 수 있음
                        val timestamp = date
                        
                        // 시간 범위 확인 (DATE 필드로 필터링)
                        if (timestamp >= sinceTimestamp && timestamp <= windowEnd && body != null && body.isNotBlank()) {
                            val timeStr = try {
                                java.time.Instant.ofEpochMilli(timestamp)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            } catch (e: Exception) {
                                timestamp.toString()
                            }
                            Log.d(TAG, "📨 SMS 메시지 발견 - ID: $id, 발신자: ${address ?: "Unknown"}, 타임스탬프: $timestamp ($timeStr)")
                            messages.add(
                                com.example.agent_app.util.SmsMessage(
                                    id = id,
                                    address = address ?: "Unknown",
                                    body = body,
                                    timestamp = timestamp,
                                    category = com.example.agent_app.util.SmsCategory.UNKNOWN // 기본값 사용
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ SMS 메시지 읽기 실패", e)
                    }
                }
            }
            
            Log.d(TAG, "✅ SMS 읽기 완료 - ${messages.size}개")
            return com.example.agent_app.util.SmsReader.SmsReadResult.Success(messages)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ SMS 읽기 실패", e)
            return com.example.agent_app.util.SmsReader.SmsReadResult.Error(
                errorType = com.example.agent_app.util.SmsReader.SmsReadError.UNKNOWN_ERROR,
                message = "SMS 읽기 중 오류: ${e.message}",
                exception = e
            )
        }
    }
    
    /**
     * SMS 메시지 처리
     */
    private suspend fun processSmsMessages(messages: List<com.example.agent_app.util.SmsMessage>) {
        val appContainer = AppContainer(context)
        val aiAgent = appContainer.huenDongMinAiAgent
        val ingestRepository = appContainer.ingestRepository
        val contactDao = appContainer.contactDao
        
        for (sms in messages) {
            val originalSmsId = "sms-auto-${sms.id}"
            
            // 중복 체크
            val existingItem = ingestRepository.getById(originalSmsId)
            if (existingItem != null) {
                Log.d(TAG, "⏭️ 이미 처리된 SMS: $originalSmsId")
                continue
            }
            
            // 기간 확인
            val isWithinPeriod = com.example.agent_app.util.AutoProcessSettings.isWithinSmsAutoProcessPeriod(context, sms.timestamp)
            if (!isWithinPeriod) {
                Log.d(TAG, "⏭️ 기간 밖 SMS: ${sms.timestamp}")
                continue
            }
            
            // 전화번호부 확인
            val normalizedPhone = com.example.agent_app.util.PhoneNumberUtils.normalize(sms.address)
            val contact = contactDao.findByPhoneNumber(sms.address, normalizedPhone)
            if (contact != null) {
                Log.d(TAG, "✅ 연락처 존재: ${sms.address} (${contact.name})")
            }
            
            // AI 처리
            val result = aiAgent.processSMSForEvent(
                smsBody = sms.body,
                smsAddress = sms.address,
                receivedTimestamp = sms.timestamp,
                originalSmsId = originalSmsId
            )
            
            Log.d(TAG, "✅ SMS 처리 완료 - Type: ${result.type}, Events: ${result.events.size}")
        }
    }
}
