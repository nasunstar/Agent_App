package com.example.agent_app.data.repo

import com.example.agent_app.ai.ClassificationResult
import com.example.agent_app.ai.OpenAIClassifier
import com.example.agent_app.data.dao.ContactDao
import com.example.agent_app.data.dao.EventDao
import com.example.agent_app.data.dao.EventTypeDao
import com.example.agent_app.data.dao.NoteDao
import com.example.agent_app.data.entity.Contact
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.entity.EventType
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.data.entity.Note
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// JsonElement 확장 함수
private fun JsonElement?.asString(): String? = this?.jsonPrimitive?.content
private fun JsonElement?.asLong(): Long? = this?.jsonPrimitive?.longOrNull

class ClassifiedDataRepository(
    private val openAIClassifier: OpenAIClassifier,
    private val contactDao: ContactDao,
    private val eventDao: EventDao,
    private val eventTypeDao: EventTypeDao,
    private val noteDao: NoteDao,
    private val ingestRepository: IngestRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    
    suspend fun processAndStoreEmail(
        subject: String?,
        body: String?,
        source: String = "gmail",
        originalId: String,
        timestamp: Long? = null
    ): ClassificationResult = withContext(dispatcher) {
        
        android.util.Log.d("ClassifiedDataRepository", "이메일 분류 시작 - ID: $originalId")
        
        // OpenAI로 분류
        val classification = openAIClassifier.classifyEmail(subject, body)
        
        android.util.Log.d("ClassifiedDataRepository", "AI 분류 결과 - Type: ${classification.type}, Confidence: ${classification.confidence}")
        
        // 분류 결과에 따라 적절한 테이블에 저장
        when (classification.type.lowercase()) {
            "contact" -> {
                android.util.Log.d("ClassifiedDataRepository", "Contact 테이블에 저장")
                storeAsContact(classification, subject, body, originalId, timestamp)
            }
            "event" -> {
                android.util.Log.d("ClassifiedDataRepository", "Event 테이블에 저장")
                storeAsEvent(classification, subject, body, originalId, timestamp)
            }
            "note", "ingest" -> {
                android.util.Log.d("ClassifiedDataRepository", "Note 테이블에 저장 (type: ${classification.type})")
                storeAsNote(classification, subject, body, originalId, timestamp)
            }
            else -> {
                android.util.Log.d("ClassifiedDataRepository", "Note 테이블에 저장 (기본값)")
                storeAsNote(classification, subject, body, originalId, timestamp)
            }
        }

        storeAsIngestItem(subject, body, source, originalId, timestamp, classification)

        android.util.Log.d("ClassifiedDataRepository", "이메일 저장 완료 - ID: $originalId")
        classification
    }
    
    // 푸시 알림 처리 메서드 추가
    suspend fun processAndStorePushNotification(
        title: String?,
        body: String?,
        source: String = "push_notification",
        originalId: String,
        timestamp: Long? = null
    ): ClassificationResult = withContext(dispatcher) {
        
        // OpenAI로 푸시 알림 분류
        val classification = openAIClassifier.classifyPushNotification(title, body)
        
        // 분류 결과에 따라 적절한 테이블에 저장
        when (classification.type.lowercase()) {
            "contact" -> storeAsContact(classification, title, body, originalId, timestamp)
            "event" -> storeAsEvent(classification, title, body, originalId, timestamp)
            "note" -> storeAsNote(classification, title, body, originalId, timestamp)
            else -> Unit
        }

        storeAsIngestItem(title, body, source, originalId, timestamp, classification)

        classification
    }
    
    private suspend fun storeAsContact(
        classification: ClassificationResult,
        subject: String?,
        body: String?,
        originalId: String,
        timestamp: Long?
    ) {
        val extractedData = classification.extractedData
        val contact = Contact(
            name = extractedData["name"].asString() ?: subject ?: "Unknown",
            email = extractedData["email"].asString(),
            phone = extractedData["phone"].asString(),
            metaJson = buildString {
                append("{")
                append("\"originalId\":\"$originalId\",")
                append("\"source\":\"gmail\",")
                append("\"confidence\":\"${classification.confidence}\",")
                append("\"timestamp\":${timestamp ?: "null"},")
                append("\"rawData\":{\"subject\":\"$subject\",\"body\":\"$body\"}")
                append("}")
            }
        )
        val contactId = contactDao.insert(contact)
        android.util.Log.d("ClassifiedDataRepository", "Contact 저장 완료 - ID: $contactId, Name: ${contact.name}")
    }
    
    private suspend fun storeAsEvent(
        classification: ClassificationResult,
        subject: String?,
        body: String?,
        originalId: String,
        timestamp: Long?
    ) {
        val extractedData = classification.extractedData
        
        // 이벤트 타입 생성 또는 가져오기
        val eventTypeName = extractedData["type"].asString() ?: "일반"
        val eventType = getOrCreateEventType(eventTypeName)
        
        val aiExtractedStartAt = extractedData["startAt"].asLong()
        
        // AI가 시간을 추출했더라도 TimeResolver로 재검증
        val timeText = "${subject ?: ""} ${body ?: ""}"
        val timeResolution = com.example.agent_app.util.TimeResolver.resolve(timeText)
        val timeResolverStartAt = timeResolution?.timestampMillis
        
        // AI 추출 시간과 TimeResolver 결과를 비교하여 더 적절한 시간 선택
        val finalStartAt = when {
            // TimeResolver가 시간을 해석했고, AI 시간과 다르면 TimeResolver 우선
            timeResolverStartAt != null && aiExtractedStartAt != null && 
            timeResolverStartAt != aiExtractedStartAt -> {
                android.util.Log.d("ClassifiedDataRepository", "시간 불일치 - AI: $aiExtractedStartAt, TimeResolver: $timeResolverStartAt, TimeResolver 우선")
                timeResolverStartAt
            }
            // TimeResolver가 시간을 해석했으면 사용
            timeResolverStartAt != null -> timeResolverStartAt
            // AI가 시간을 추출했으면 사용
            aiExtractedStartAt != null -> aiExtractedStartAt
            // 둘 다 없으면 원본 timestamp
            else -> timestamp
        }
        
        // 디버깅 로그 추가
        android.util.Log.d("ClassifiedDataRepository", "Event 저장 - 제목: ${subject}")
        android.util.Log.d("ClassifiedDataRepository", "AI 추출 시간: $aiExtractedStartAt")
        android.util.Log.d("ClassifiedDataRepository", "TimeResolver 시간: $timeResolverStartAt")
        android.util.Log.d("ClassifiedDataRepository", "원본 timestamp: $timestamp")
        android.util.Log.d("ClassifiedDataRepository", "최종 startAt: $finalStartAt")
        android.util.Log.d("ClassifiedDataRepository", "AI 추출 데이터: $extractedData")
        android.util.Log.d("ClassifiedDataRepository", "TimeResolver 시도 - 텍스트: $timeText")
        android.util.Log.d("ClassifiedDataRepository", "TimeResolver 결과: $timeResolution")
        
        val event = Event(
            userId = 1L, // 기본 사용자 ID (실제로는 현재 사용자 ID 사용)
            typeId = eventType.id,
            title = extractedData["title"].asString() ?: subject ?: "Unknown Event",
            body = body,
            startAt = finalStartAt,
            endAt = extractedData["endAt"].asLong(),
            location = extractedData["location"].asString(),
            status = "pending"
        )
        eventDao.insert(event)
    }
    
    private suspend fun getOrCreateEventType(typeName: String): EventType {
        // 기존 타입 찾기
        val existingType = eventTypeDao.getByName(typeName)
        if (existingType != null) {
            return existingType
        }
        
        // 새 타입 생성
        val eventType = EventType(typeName = typeName)
        val typeId = eventTypeDao.upsert(eventType)
        return eventType.copy(id = typeId)
    }
    
    private suspend fun storeAsNote(
        classification: ClassificationResult,
        subject: String?,
        body: String?,
        originalId: String,
        timestamp: Long?
    ) {
        val extractedData = classification.extractedData
        val note = Note(
            userId = 1L, // 기본 사용자 ID
            title = extractedData["title"].asString() ?: subject ?: "Note",
            body = extractedData["body"].asString() ?: body ?: "",
            createdAt = timestamp ?: System.currentTimeMillis(), // 원본 timestamp 사용
            updatedAt = System.currentTimeMillis()
        )
        noteDao.insert(note)
    }
    
    private suspend fun storeAsIngestItem(
        subject: String?,
        body: String?,
        source: String,
        originalId: String,
        timestamp: Long?,
        classification: ClassificationResult,
    ) {
        val ingestItem = IngestItem(
            id = originalId,
            source = source,
            type = classification.type,
            title = subject,
            body = body,
            timestamp = timestamp ?: System.currentTimeMillis(),
            dueDate = classification.extractedData["startAt"].asLong(),
            confidence = classification.confidence,
            metaJson = buildString {
                append("{")
                append("\"originalId\":\"$originalId\",")
                append("\"classification\":\"${classification.type}\",")
                append("\"rawData\":{\"subject\":\"$subject\",\"body\":\"$body\"}")
                append("}")
            }
        )
        ingestRepository.upsert(ingestItem)
        android.util.Log.d(
            "ClassifiedDataRepository",
            "IngestItem 저장 완료 - ID: $originalId, Title: $subject, Type: ${classification.type}"
        )
    }
    
    // 분류된 데이터 조회 메서드들
    suspend fun getAllContacts(): List<Contact> = withContext(dispatcher) {
        contactDao.getAll()
    }
    
    suspend fun getAllEvents(): List<Event> = withContext(dispatcher) {
        eventDao.getAll()
    }
    
    suspend fun getAllNotes(): List<Note> = withContext(dispatcher) {
        noteDao.getAll()
    }
}
