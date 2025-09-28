package com.example.agent_app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.agent_app.data.dao.AuthTokenDao
import com.example.agent_app.data.dao.ContactDao
import com.example.agent_app.data.dao.EventDao
import com.example.agent_app.data.dao.EventTypeDao
import com.example.agent_app.data.dao.IngestItemDao
import com.example.agent_app.data.dao.NoteDao
import com.example.agent_app.data.dao.UserDao
import com.example.agent_app.data.entity.AuthToken
import com.example.agent_app.data.entity.Contact
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.entity.EventDetail
import com.example.agent_app.data.entity.EventNotification
import com.example.agent_app.data.entity.EventType
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.data.entity.Note
import com.example.agent_app.data.entity.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var contactDao: ContactDao
    private lateinit var userDao: UserDao
    private lateinit var noteDao: NoteDao
    private lateinit var eventTypeDao: EventTypeDao
    private lateinit var eventDao: EventDao
    private lateinit var authTokenDao: AuthTokenDao
    private lateinit var ingestItemDao: IngestItemDao

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        contactDao = database.contactDao()
        userDao = database.userDao()
        noteDao = database.noteDao()
        eventTypeDao = database.eventTypeDao()
        eventDao = database.eventDao()
        authTokenDao = database.authTokenDao()
        ingestItemDao = database.ingestItemDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun contactCrudOperationsWork() = runTest {
        val contactId = contactDao.upsert(
            Contact(
                name = "Ada Lovelace",
                email = "ada@example.com",
                phone = "010-1234-5678",
                metaJson = "{\"company\":\"Analytical Engines\"}",
            )
        )

        var stored = contactDao.getById(contactId)
        assertNotNull(stored)
        assertEquals("Ada Lovelace", stored!!.name)

        val updated = stored.copy(phone = "010-8765-4321")
        contactDao.update(updated)
        stored = contactDao.getById(contactId)
        assertEquals("010-8765-4321", stored!!.phone)

        contactDao.delete(updated)
        assertNull(contactDao.getById(contactId))
    }

    @Test
    fun userAndNoteRelationshipIsMaintained() = runTest {
        val userId = userDao.upsert(
            User(
                name = "Test User",
                email = "user@example.com",
                createdAt = 1727443200000L,
            )
        )

        val noteId = noteDao.upsert(
            Note(
                userId = userId,
                title = "Meeting notes",
                body = "Discuss quarterly goals",
                createdAt = 1727443300000L,
                updatedAt = 1727443300000L,
            )
        )

        val fetched = noteDao.getById(noteId)
        assertNotNull(fetched)
        assertEquals(userId, fetched!!.userId)

        val updated = fetched.copy(updatedAt = 1727444300000L)
        noteDao.update(updated)

        val notes = noteDao.observeByUser(userId).first()
        assertEquals(1, notes.size)
        assertEquals(1727444300000L, notes.first().updatedAt)
    }

    @Test
    fun eventEntitiesSupportDetailsAndNotifications() = runTest {
        val userId = userDao.upsert(
            User(
                name = "Calendar Owner",
                email = "owner@example.com",
                createdAt = 1727443200000L,
            )
        )
        val typeId = eventTypeDao.upsert(EventType(typeName = "Meeting"))

        val eventId = eventDao.upsert(
            Event(
                userId = userId,
                typeId = typeId,
                title = "Strategy Sync",
                startAt = 1727450000000L,
                endAt = 1727453600000L,
                location = "Conference Room",
                status = "confirmed",
            )
        )

        eventDao.upsertDetail(
            EventDetail(
                eventId = eventId,
                description = "Align on roadmap priorities",
            )
        )

        val initialNotifications = listOf(
            EventNotification(eventId = eventId, notifyAt = 1727446400000L, channel = "push"),
            EventNotification(eventId = eventId, notifyAt = 1727448200000L, channel = "email"),
        )
        eventDao.replaceNotifications(eventId, initialNotifications)

        val detail = eventDao.getDetail(eventId)
        assertNotNull(detail)
        assertEquals("Align on roadmap priorities", detail!!.description)

        val notifications = eventDao.observeNotifications(eventId).first()
        assertEquals(2, notifications.size)
        assertEquals("push", notifications.first().channel)

        val updatedNotifications = listOf(
            EventNotification(eventId = eventId, notifyAt = 1727449200000L, channel = "push"),
        )
        eventDao.replaceNotifications(eventId, updatedNotifications)

        val refreshed = eventDao.observeNotifications(eventId).first()
        assertEquals(1, refreshed.size)
        assertEquals(1727449200000L, refreshed.first().notifyAt)
    }

    @Test
    fun authTokensPersistAndUpdate() = runTest {
        val provider = "google"
        val token = AuthToken(
            provider = provider,
            accessToken = "access",
            refreshToken = "refresh",
            scope = "scope",
            expiresAt = 1727450000L,
        )

        authTokenDao.upsert(token)
        var stored = authTokenDao.getByProvider(provider)
        assertNotNull(stored)
        assertEquals("access", stored!!.accessToken)

        val updated = token.copy(accessToken = "updated_access")
        authTokenDao.upsert(updated)
        stored = authTokenDao.getByProvider(provider)
        assertEquals("updated_access", stored!!.accessToken)
    }

    @Test
    fun ingestItemsSupportQueryingByTimeAndDueDate() = runTest {
        val baseTimestamp = 1727443200000L
        val items = listOf(
            IngestItem(
                id = "1",
                source = "gmail",
                type = "email",
                title = "Welcome",
                body = "Welcome to the service",
                timestamp = baseTimestamp,
                dueDate = null,
                confidence = null,
                metaJson = null,
            ),
            IngestItem(
                id = "2",
                source = "sms",
                type = "message",
                title = "Code",
                body = "Your OTP is 1234",
                timestamp = baseTimestamp + 1_000,
                dueDate = baseTimestamp + 10_000,
                confidence = 0.9,
                metaJson = null,
            ),
            IngestItem(
                id = "3",
                source = "gmail",
                type = "email",
                title = "Invoice",
                body = "Invoice attached",
                timestamp = baseTimestamp + 2_000,
                dueDate = baseTimestamp + 20_000,
                confidence = 0.5,
                metaJson = null,
            ),
        )

        items.forEach { ingestItemDao.upsert(it) }

        val page = ingestItemDao.getPaged(limit = 2, offset = 0)
        assertEquals(2, page.size)
        assertEquals("3", page.first().id)

        val gmailItems = ingestItemDao.observeBySource("gmail").first()
        assertEquals(2, gmailItems.size)

        val rangeItems = ingestItemDao.getByTimestampRange(
            start = baseTimestamp + 500,
            end = baseTimestamp + 1_500,
        )
        assertEquals(1, rangeItems.size)
        assertEquals("2", rangeItems.first().id)

        val dueItems = ingestItemDao.observeDueBetween(
            after = baseTimestamp + 5_000,
            before = baseTimestamp + 25_000,
        ).first()
        assertEquals(listOf("2", "3"), dueItems.map { it.id })

        ingestItemDao.delete(items.first())
        assertNull(ingestItemDao.getById("1"))
    }
}
