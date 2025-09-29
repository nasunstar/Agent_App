package com.example.agent_app.data.repo

import com.example.agent_app.ai.ClassificationResult
import com.example.agent_app.ai.OpenAIClassifier
import com.example.agent_app.data.dao.ContactDao
import com.example.agent_app.data.dao.EventDao
import com.example.agent_app.data.dao.EventTypeDao
import com.example.agent_app.data.dao.IngestItemDao
import com.example.agent_app.data.dao.NoteDao
import com.example.agent_app.data.entity.Contact
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.entity.EventType
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.data.entity.Note
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClassifiedDataRepository(
    private val openAIClassifier: OpenAIClassifier,
    private val contactDao: ContactDao,
    private val eventDao: EventDao,
    private val eventTypeDao: EventTypeDao,
    private val noteDao: NoteDao,
    private val ingestItemDao: IngestItemDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    
    suspend fun processAndStoreEmail(
        subject: String?,
        body: String?,
        source: String = "gmail",
        originalId: String,
        timestamp: Long? = null
    ): ClassificationResult = withContext(dispatcher) {
        
        // OpenAI로 분류
        val classification = openAIClassifier.classifyEmail(subject, body)
        
        // 분류 결과에 따라 적절한 테이블에 저장
        when (classification.type) {
            "contact" -> storeAsContact(classification, subject, body, originalId, timestamp)
            "event" -> storeAsEvent(classification, subject, body, originalId, timestamp)
            "note" -> storeAsNote(classification, subject, body, originalId, timestamp)
            else -> storeAsIngestItem(subject, body, source, originalId, timestamp)
        }
        
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
        when (classification.type) {
            "contact" -> storeAsContact(classification, title, body, originalId, timestamp)
            "event" -> storeAsEvent(classification, title, body, originalId, timestamp)
            "note" -> storeAsNote(classification, title, body, originalId, timestamp)
            else -> storeAsIngestItem(title, body, source, originalId, timestamp)
        }
        
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
            name = extractedData["name"] ?: subject ?: "Unknown",
            email = extractedData["email"] ?: null,
            phone = extractedData["phone"] ?: null,
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
        contactDao.insert(contact)
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
        val eventTypeName = extractedData["type"] ?: "일반"
        val eventType = getOrCreateEventType(eventTypeName)
        
        val event = Event(
            userId = 1L, // 기본 사용자 ID (실제로는 현재 사용자 ID 사용)
            typeId = eventType.id,
            title = extractedData["title"] ?: subject ?: "Unknown Event",
            startAt = extractedData["startAt"]?.toLongOrNull() ?: timestamp, // AI가 추출한 시간이 없으면 원본 timestamp 사용
            endAt = extractedData["endAt"]?.toLongOrNull(),
            location = extractedData["location"] ?: null,
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
            title = extractedData["title"] ?: subject ?: "Note",
            body = extractedData["body"] ?: body ?: "",
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
        timestamp: Long?
    ) {
        val ingestItem = IngestItem(
            id = originalId,
            source = source,
            type = "email",
            title = subject,
            body = body,
            timestamp = timestamp ?: System.currentTimeMillis(), // 원본 timestamp 사용
            dueDate = null,
            confidence = null,
            metaJson = buildString {
                append("{")
                append("\"originalId\":\"$originalId\",")
                append("\"rawData\":{\"subject\":\"$subject\",\"body\":\"$body\"}")
                append("}")
            }
        )
        ingestItemDao.upsert(ingestItem)
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
