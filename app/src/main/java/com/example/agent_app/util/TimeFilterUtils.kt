package com.example.agent_app.util

import android.util.Log

/**
 * MOA-LLM-Optimization: Rule-based Pre-filter + Time Keyword Gating
 * 
 * LLM 호출을 최적화하기 위한 필터링 유틸리티
 * - 일정 생성 가능성이 0%에 가까운 알림만 필터링 (보수적 설계)
 * - 시간 관련 키워드가 있으면 LLM 호출 허용
 */
object TimeFilterUtils {
    
    private const val TAG = "TimeFilterUtils"
    
    // ==========================================
    // 필터링 조건 A: 일정 가능성이 0%에 가까운 도메인
    // ==========================================
    
    // OTP/인증 관련 키워드
    private val OTP_KEYWORDS = setOf(
        "인증번호", "OTP", "보안코드", "로그인", "2단계 인증", "인증 코드",
        "인증코드", "인증 번호", "인증", "코드", "번호", "비밀번호"
    )
    
    // 금융 단순 알림 키워드
    private val FINANCE_KEYWORDS = setOf(
        "입금", "출금", "승인", "결제완료", "사용내역", "가맹점",
        "거래", "이체", "송금", "잔액", "계좌", "카드 승인",
        "결제", "승인 완료", "거래 완료", "이체 완료"
    )
    
    // 광고/마케팅 키워드
    private val ADVERTISING_KEYWORDS = setOf(
        "세일", "쿠폰", "특가", "이벤트", "광고", "혜택", "딜",
        "할인", "프로모션", "추천", "신규", "오픈", "기념일",
        "% 할인", "원 할인", "무료", "증정", "선물"
    )
    
    // 배송/택배 키워드
    private val DELIVERY_KEYWORDS = setOf(
        "배송중", "배달완료", "도착했습니다", "출고완료",
        "배송", "배달", "출고", "도착", "배송 시작", "배송 완료",
        "택배", "배송지", "수령", "수령 완료"
    )
    
    // 단순 공지 키워드
    private val NOTICE_KEYWORDS = setOf(
        "업데이트 안내", "공지사항", "이용안내", "약관 변경",
        "업데이트", "공지", "안내", "변경", "점검", "서비스",
        "알림", "설정", "권한", "정책"
    )
    
    // 알림성 메시지 키워드
    private val NOTIFICATION_KEYWORDS = setOf(
        "메시지가 도착했습니다", "알림 켜짐", "백업 완료",
        "도착", "완료", "켜짐", "꺼짐", "설정", "변경됨"
    )
    
    // 모든 필터링 대상 키워드 통합
    private val FILTER_KEYWORDS = OTP_KEYWORDS + FINANCE_KEYWORDS + 
        ADVERTISING_KEYWORDS + DELIVERY_KEYWORDS + 
        NOTICE_KEYWORDS + NOTIFICATION_KEYWORDS
    
    // ==========================================
    // 필터링 조건 B: 시간 관련 표현 체크
    // ==========================================
    
    // 그룹 1: 강한 시간 키워드
    private val STRONG_TIME_KEYWORDS = setOf(
        "오전", "오후", "새벽", "저녁", "밤", "점심",
        "내일", "모레", "주말", "다음주",
        "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일",
        "월", "화", "수", "목", "금", "토", "일"
    )
    
    // 그룹 2: 약한 시간 키워드 (구어체·오타·비표준)
    private val WEAK_TIME_KEYWORDS = setOf(
        "낼", "내", "담주", "담쥬", "담달", "수욜", "금욜",
        "지금 뒤", "조금 뒤", "퇴근후", "퇴근하고", "퇴근 후",
        "나중에", "곧", "조금 있다", "이따가", "좀따"
    )
    
    // 그룹 3: 일정 가능성이 높은 동사/명사
    private val EVENT_VERBS_NOUNS = setOf(
        "회의", "미팅", "약속", "방문", "참석", "면담", "레슨", "수업",
        "보자", "만나자", "모여", "가자", "진료", "예약",
        "일정", "스케줄", "약속", "모임", "모임", "만남"
    )
    
    // 시간 패턴 (숫자+시, HH:mm, AM/PM)
    private val TIME_PATTERNS = listOf(
        Regex("""\d{1,2}시"""),  // "3시", "14시"
        Regex("""\d{1,2}:\d{2}"""),  // "14:30"
        Regex("""\d{1,2}:\d{2}:\d{2}"""),  // "14:30:00"
        Regex("""\d{1,2}am""", RegexOption.IGNORE_CASE),  // "3am"
        Regex("""\d{1,2}pm""", RegexOption.IGNORE_CASE),  // "3pm"
        Regex("""오전\s*\d{1,2}"""),  // "오전 3"
        Regex("""오후\s*\d{1,2}"""),  // "오후 3"
    )
    
    // 날짜 패턴
    private val DATE_PATTERNS = listOf(
        Regex("""\d{4}년\s*\d{1,2}월\s*\d{1,2}일"""),  // "2025년 10월 12일"
        Regex("""\d{1,2}월\s*\d{1,2}일"""),  // "10월 12일"
        Regex("""\d{1,2}/\d{1,2}"""),  // "10/12"
        Regex("""\d{1,2}\.\d{1,2}"""),  // "10.12"
        Regex("""\d{4}-\d{2}-\d{2}"""),  // "2025-10-12"
    )
    
    /**
     * 텍스트가 LLM 호출을 스킵해야 하는지 판단
     * 
     * @param text 분석할 텍스트
     * @param sourceType 데이터 소스 타입 ("gmail", "sms", "ocr", "push_notification")
     * @return true면 LLM 호출 스킵, false면 LLM 호출 필요
     */
    fun shouldSkipLLM(text: String?, sourceType: String): Boolean {
        if (text.isNullOrBlank()) {
            Log.d(TAG, "[$sourceType] 텍스트가 비어있어 LLM 호출 스킵")
            return true
        }
        
        val normalizedText = text.lowercase().trim()
        
        // ==========================================
        // 1단계: Rule-based Pre-filter 체크
        // ==========================================
        
        // 조건 A: 일정 가능성이 0%에 가까운 도메인 키워드 포함 여부
        val hasFilterKeyword = FILTER_KEYWORDS.any { keyword ->
            normalizedText.contains(keyword.lowercase())
        }
        
        if (!hasFilterKeyword) {
            // 필터 키워드가 없으면 시간 키워드 체크로 진행
            Log.d(TAG, "[$sourceType] 필터 키워드 없음 → 시간 키워드 체크 진행")
            return checkTimeKeywords(normalizedText, sourceType)
        }
        
        // 조건 B: 시간 관련 표현이 전혀 없는지 체크
        val hasTimeExpression = hasAnyTimeExpression(normalizedText)
        
        if (hasTimeExpression) {
            // 필터 키워드가 있어도 시간 표현이 있으면 LLM 호출 허용 (보수적 설계)
            Log.d(TAG, "[$sourceType] 필터 키워드 있으나 시간 표현도 있음 → LLM 호출 허용")
            return false
        }
        
        // A + B 모두 만족 → LLM 호출 스킵
        Log.d(TAG, "[$sourceType] 필터링됨: 필터 키워드 있음 + 시간 표현 없음")
        Log.d(TAG, "[$sourceType] 필터링된 텍스트: ${text.take(100)}...")
        return true
    }
    
    /**
     * 시간 키워드가 있는지 체크 (Time Keyword Gating)
     * 
     * @param normalizedText 정규화된 텍스트 (소문자)
     * @param sourceType 데이터 소스 타입
     * @return true면 LLM 호출 스킵, false면 LLM 호출 필요
     */
    private fun checkTimeKeywords(normalizedText: String, sourceType: String): Boolean {
        // 그룹 1: 강한 시간 키워드 체크
        val hasStrongTimeKeyword = STRONG_TIME_KEYWORDS.any { keyword ->
            normalizedText.contains(keyword.lowercase())
        }
        
        if (hasStrongTimeKeyword) {
            Log.d(TAG, "[$sourceType] 강한 시간 키워드 발견 → LLM 호출 허용")
            return false
        }
        
        // 그룹 2: 약한 시간 키워드 체크
        val hasWeakTimeKeyword = WEAK_TIME_KEYWORDS.any { keyword ->
            normalizedText.contains(keyword.lowercase())
        }
        
        if (hasWeakTimeKeyword) {
            Log.d(TAG, "[$sourceType] 약한 시간 키워드 발견 → LLM 호출 허용")
            return false
        }
        
        // 그룹 3: 일정 가능성이 높은 동사/명사 체크
        val hasEventKeyword = EVENT_VERBS_NOUNS.any { keyword ->
            normalizedText.contains(keyword.lowercase())
        }
        
        if (hasEventKeyword) {
            Log.d(TAG, "[$sourceType] 일정 관련 키워드 발견 → LLM 호출 허용")
            return false
        }
        
        // 시간 패턴 체크
        val hasTimePattern = TIME_PATTERNS.any { pattern ->
            pattern.containsMatchIn(normalizedText)
        }
        
        if (hasTimePattern) {
            Log.d(TAG, "[$sourceType] 시간 패턴 발견 → LLM 호출 허용")
            return false
        }
        
        // 날짜 패턴 체크
        val hasDatePattern = DATE_PATTERNS.any { pattern ->
            pattern.containsMatchIn(normalizedText)
        }
        
        if (hasDatePattern) {
            Log.d(TAG, "[$sourceType] 날짜 패턴 발견 → LLM 호출 허용")
            return false
        }
        
        // 모든 시간 관련 표현이 없음 → LLM 호출 스킵
        Log.d(TAG, "[$sourceType] 시간 관련 표현 없음 → LLM 호출 스킵")
        return true
    }
    
    /**
     * 시간 관련 표현이 있는지 체크
     * 
     * @param normalizedText 정규화된 텍스트 (소문자)
     * @return true면 시간 표현 있음, false면 없음
     */
    private fun hasAnyTimeExpression(normalizedText: String): Boolean {
        // 강한 시간 키워드
        if (STRONG_TIME_KEYWORDS.any { normalizedText.contains(it.lowercase()) }) {
            return true
        }
        
        // 약한 시간 키워드
        if (WEAK_TIME_KEYWORDS.any { normalizedText.contains(it.lowercase()) }) {
            return true
        }
        
        // 일정 관련 키워드
        if (EVENT_VERBS_NOUNS.any { normalizedText.contains(it.lowercase()) }) {
            return true
        }
        
        // 시간 패턴
        if (TIME_PATTERNS.any { it.containsMatchIn(normalizedText) }) {
            return true
        }
        
        // 날짜 패턴
        if (DATE_PATTERNS.any { it.containsMatchIn(normalizedText) }) {
            return true
        }
        
        return false
    }
}

