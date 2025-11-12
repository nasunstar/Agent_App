package com.example.agent_app.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import android.util.Log

/**
 * SMS 메시지를 읽는 유틸리티 클래스
 */
object SmsReader {
    
    /**
     * SMS 읽기 결과
     */
    sealed class SmsReadResult {
        data class Success(val messages: List<SmsMessage>) : SmsReadResult()
        data class Error(val errorType: SmsReadError, val message: String, val exception: Throwable? = null) : SmsReadResult()
    }
    
    /**
     * SMS 읽기 오류 타입
     */
    enum class SmsReadError {
        PERMISSION_DENIED,      // 권한 없음
        CONTENT_PROVIDER_ERROR,  // ContentProvider 접근 실패
        DATA_ERROR,             // 데이터 읽기 실패
        UNKNOWN_ERROR           // 알 수 없는 오류
    }
    
    /**
     * 지정된 날짜 이후의 SMS 메시지를 읽어옵니다.
     * 
     * @param context Context
     * @param sinceTimestamp 이 시간(epoch milliseconds) 이후의 메시지만 읽어옵니다
     * @return SMS 읽기 결과 (성공 또는 오류)
     */
    fun readSmsMessages(context: Context, sinceTimestamp: Long): SmsReadResult {
        val messages = mutableListOf<SmsMessage>()
        
        try {
            // 권한 확인
            if (android.content.pm.PackageManager.PERMISSION_GRANTED != 
                context.checkSelfPermission(android.Manifest.permission.READ_SMS)) {
                return SmsReadResult.Error(
                    errorType = SmsReadError.PERMISSION_DENIED,
                    message = "SMS 읽기 권한이 없습니다. 설정에서 권한을 허용해주세요."
                )
            }
            
            val contentResolver: ContentResolver = context.contentResolver
            val uri = Telephony.Sms.CONTENT_URI
            
            // sinceTimestamp가 0L이면 모든 SMS를 읽음 (첫 스캔 또는 전체 스캔)
            val selection = if (sinceTimestamp > 0L) {
                "${Telephony.Sms.DATE} >= ?"
            } else {
                null
            }
            val selectionArgs = if (sinceTimestamp > 0L) {
                arrayOf(sinceTimestamp.toString())
            } else {
                null
            }
            
            val cursor: Cursor? = try {
                contentResolver.query(
                    uri,
                    arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.DATE_SENT,
                    ),
                    selection,
                    selectionArgs,
                    "${Telephony.Sms.DATE} DESC"
                )
            } catch (e: SecurityException) {
                Log.e("SmsReader", "SMS 읽기 권한 오류", e)
                return SmsReadResult.Error(
                    errorType = SmsReadError.PERMISSION_DENIED,
                    message = "SMS 읽기 권한이 거부되었습니다: ${e.message}",
                    exception = e
                )
            } catch (e: IllegalStateException) {
                Log.e("SmsReader", "ContentProvider 접근 오류", e)
                return SmsReadResult.Error(
                    errorType = SmsReadError.CONTENT_PROVIDER_ERROR,
                    message = "SMS 데이터베이스에 접근할 수 없습니다: ${e.message}",
                    exception = e
                )
            }
            
            cursor?.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val dateSentIndex = it.getColumnIndex(Telephony.Sms.DATE_SENT)
                
                // Cursor의 총 행 수 확인
                val totalCount = it.count
                Log.d("SmsReader", "SMS Cursor 총 행 수: $totalCount")
                Log.d("SmsReader", "컬럼 인덱스: id=$idIndex, address=$addressIndex, body=$bodyIndex, date=$dateIndex, dateSent=$dateSentIndex")
                
                // 컬럼 인덱스 검증 (필수 컬럼만 체크)
                if (idIndex < 0 || bodyIndex < 0 || dateIndex < 0) {
                    val missingColumns = mutableListOf<String>()
                    if (idIndex < 0) missingColumns.add("_ID")
                    if (bodyIndex < 0) missingColumns.add("BODY")
                    if (dateIndex < 0) missingColumns.add("DATE")
                    
                    Log.e("SmsReader", "필수 컬럼을 찾을 수 없습니다: ${missingColumns.joinToString(", ")}")
                    return SmsReadResult.Error(
                        errorType = SmsReadError.DATA_ERROR,
                        message = "SMS 데이터 형식이 올바르지 않습니다. 필수 컬럼 누락: ${missingColumns.joinToString(", ")}"
                    )
                }
                
                // DATE_SENT는 선택적 컬럼 (없어도 DATE 사용)
                val hasDateSent = dateSentIndex >= 0
                Log.d("SmsReader", "DATE_SENT 컬럼 사용 가능: $hasDateSent")
                
                var successCount = 0
                var errorCount = 0
                var readCount = 0
                
                // 모든 행 읽기
                while (it.moveToNext()) {
                    readCount++
                    try {
                        val id = it.getString(idIndex)
                        val address = if (addressIndex >= 0) {
                            try {
                                it.getString(addressIndex)
                            } catch (e: Exception) {
                                Log.w("SmsReader", "주소 읽기 실패", e)
                                null
                            }
                        } else null
                        
                        val body = it.getString(bodyIndex)
                        val date = it.getLong(dateIndex)
                        
                        // DATE_SENT가 있으면 사용, 없으면 DATE 사용
                        val dateSent = if (hasDateSent && !it.isNull(dateSentIndex)) {
                            try {
                                it.getLong(dateSentIndex)
                            } catch (e: Exception) {
                                Log.w("SmsReader", "DATE_SENT 읽기 실패, DATE 사용", e)
                                date
                            }
                        } else {
                            date
                        }
                        
                        if (id != null && body != null && body.isNotBlank()) {
                            val smsAddress = address ?: "Unknown"
                            val category = classifySmsCategory(smsAddress, body)
                            messages.add(
                                SmsMessage(
                                    id = id,
                                    address = smsAddress,
                                    body = body,
                                    timestamp = dateSent,
                                    category = category,
                                )
                            )
                            successCount++
                        } else {
                            Log.w("SmsReader", "SMS 메시지 데이터가 불완전합니다 (id=$id, body=${body?.take(20)}...)")
                            errorCount++
                        }
                    } catch (e: Exception) {
                        Log.w("SmsReader", "개별 SMS 메시지 읽기 실패 (계속 진행)", e)
                        errorCount++
                        // 개별 메시지 오류는 무시하고 계속 진행
                    }
                }
                
                Log.d("SmsReader", "SMS 읽기 완료: Cursor 총 행 수=$totalCount, 읽은 행 수=$readCount, 성공=$successCount, 실패=$errorCount, 메시지 리스트 크기=${messages.size}")
                
                // 읽은 행 수와 Cursor 총 행 수가 다르면 경고
                if (readCount != totalCount) {
                    Log.w("SmsReader", "⚠️ 경고: Cursor 총 행 수($totalCount)와 실제 읽은 행 수($readCount)가 다릅니다!")
                }
                
                if (successCount == 0 && errorCount > 0) {
                    Log.w("SmsReader", "모든 SMS 메시지 읽기 실패")
                    return SmsReadResult.Error(
                        errorType = SmsReadError.DATA_ERROR,
                        message = "SMS 메시지를 읽을 수 없습니다. 데이터 형식 문제일 수 있습니다."
                    )
                }
            } ?: run {
                Log.e("SmsReader", "SMS Cursor가 null입니다. ContentProvider 접근 실패")
                return SmsReadResult.Error(
                    errorType = SmsReadError.CONTENT_PROVIDER_ERROR,
                    message = "SMS 데이터를 가져올 수 없습니다. 권한을 확인하거나 앱을 재시작해보세요."
                )
            }
            
            Log.d("SmsReader", "✅ 최종 읽은 SMS 메시지 개수: ${messages.size}")
            if (messages.isEmpty() && sinceTimestamp == 0L) {
                Log.w("SmsReader", "⚠️ 전체 스캔인데 SMS 메시지가 0개입니다. 권한이나 데이터 문제일 수 있습니다.")
            }
            return SmsReadResult.Success(messages)
            
        } catch (e: SecurityException) {
            Log.e("SmsReader", "SMS 읽기 권한 오류", e)
            return SmsReadResult.Error(
                errorType = SmsReadError.PERMISSION_DENIED,
                message = "SMS 읽기 권한이 필요합니다: ${e.message}",
                exception = e
            )
        } catch (e: IllegalStateException) {
            Log.e("SmsReader", "ContentProvider 상태 오류", e)
            return SmsReadResult.Error(
                errorType = SmsReadError.CONTENT_PROVIDER_ERROR,
                message = "SMS 데이터베이스 접근 실패: ${e.message}",
                exception = e
            )
        } catch (e: Exception) {
            Log.e("SmsReader", "SMS 읽기 실패", e)
            return SmsReadResult.Error(
                errorType = SmsReadError.UNKNOWN_ERROR,
                message = "SMS 읽기 중 오류가 발생했습니다: ${e.message ?: e.javaClass.simpleName}",
                exception = e
            )
        }
    }
    
    /**
     * 기존 호환성을 위한 래퍼 함수 (빈 리스트 반환)
     * @deprecated readSmsMessages()를 사용하고 SmsReadResult를 확인하세요
     */
    @Deprecated("SmsReadResult를 반환하는 readSmsMessages()를 사용하세요")
    fun readSmsMessagesLegacy(context: Context, sinceTimestamp: Long): List<SmsMessage> {
        return when (val result = readSmsMessages(context, sinceTimestamp)) {
            is SmsReadResult.Success -> result.messages
            is SmsReadResult.Error -> {
                Log.e("SmsReader", "SMS 읽기 실패: ${result.message}")
                emptyList()
            }
        }
    }
    
    /**
     * SMS 카테고리 분류 (프로모션/개인)
     * 
     * 분류 기준:
     * 1. 발신자 번호 패턴: 짧은 번호(4-5자리)는 프로모션 가능성 높음
     * 2. 메시지 내용: 프로모션 키워드 포함 여부
     */
    private fun classifySmsCategory(address: String, body: String): SmsCategory {
        // 발신자 번호 패턴 분석
        val cleanAddress = address.replace("-", "").replace(" ", "").replace("+82", "0")
        
        // 짧은 번호 (4-5자리)는 프로모션 가능성 높음
        val isShortNumber = cleanAddress.length in 4..5 && cleanAddress.all { it.isDigit() }
        
        // 프로모션 키워드 패턴
        val promotionKeywords = listOf(
            "할인", "특가", "이벤트", "프로모션", "쿠폰", "적립", "포인트",
            "무료", "증정", "선착순", "마감", "광고", "알림톡",
            "신청", "가입", "구독", "해지", "문의", "상담",
            "www.", "http://", "https://", ".com", ".kr",
            "안내", "공지", "서비스", "혜택", "추천"
        )
        
        val bodyLower = body.lowercase()
        val hasPromotionKeyword = promotionKeywords.any { keyword ->
            bodyLower.contains(keyword.lowercase())
        }
        
        // 개인 메시지 특징
        val personalKeywords = listOf(
            "안녕", "감사", "고맙", "미안", "죄송", "만나", "약속", "회의",
            "오늘", "내일", "모레", "다음주", "언제", "어디", "뭐"
        )
        val hasPersonalKeyword = personalKeywords.any { keyword ->
            bodyLower.contains(keyword.lowercase())
        }
        
        // 분류 로직
        return when {
            // 짧은 번호 + 프로모션 키워드 → 프로모션
            isShortNumber && hasPromotionKeyword -> SmsCategory.PROMOTION
            // 개인 키워드가 있고 프로모션 키워드가 없으면 → 개인
            hasPersonalKeyword && !hasPromotionKeyword -> SmsCategory.PERSONAL
            // 짧은 번호만 있으면 → 프로모션 가능성 높음
            isShortNumber -> SmsCategory.PROMOTION
            // 프로모션 키워드만 있으면 → 프로모션
            hasPromotionKeyword -> SmsCategory.PROMOTION
            // 일반 전화번호 형식이면 → 개인 가능성 높음
            cleanAddress.matches(Regex("^01[0-9]{8,9}$")) -> SmsCategory.PERSONAL
            // 기본값
            else -> SmsCategory.UNKNOWN
        }
    }
}

data class SmsMessage(
    val id: String,
    val address: String,
    val body: String,
    val timestamp: Long,
    val category: SmsCategory = SmsCategory.UNKNOWN,
)

/**
 * SMS 카테고리 (프로모션/개인 분류)
 */
enum class SmsCategory {
    PERSONAL,      // 개인 메시지
    PROMOTION,     // 프로모션/광고
    UNKNOWN,       // 분류 불가
}

