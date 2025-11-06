package com.example.agent_app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.agent_app.data.dao.AuthTokenDao
import com.example.agent_app.data.dao.ContactDao
import com.example.agent_app.data.dao.EmbeddingDao
import com.example.agent_app.data.dao.EventDao
import com.example.agent_app.data.dao.EventTypeDao
import com.example.agent_app.data.dao.IngestItemDao
import com.example.agent_app.data.dao.NoteDao
import com.example.agent_app.data.dao.PushNotificationDao
import com.example.agent_app.data.dao.UserDao
import com.example.agent_app.data.db.migrations.DatabaseMigrations
import com.example.agent_app.data.entity.AuthToken
import com.example.agent_app.data.entity.Contact
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.entity.EventDetail
import com.example.agent_app.data.entity.EventNotification
import com.example.agent_app.data.entity.EventType
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.data.entity.IngestItemEmbedding
import com.example.agent_app.data.entity.IngestItemFts
import com.example.agent_app.data.entity.Note
import com.example.agent_app.data.entity.PushNotification
import com.example.agent_app.data.entity.User

@Database(
    entities = [
        Contact::class,
        User::class,
        Note::class,
        EventType::class,
        Event::class,
        EventDetail::class,
        EventNotification::class,
        AuthToken::class,
        IngestItem::class,
        IngestItemFts::class,
        IngestItemEmbedding::class,
        PushNotification::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun userDao(): UserDao
    abstract fun noteDao(): NoteDao
    abstract fun eventTypeDao(): EventTypeDao
    abstract fun eventDao(): EventDao
    abstract fun authTokenDao(): AuthTokenDao
    abstract fun ingestItemDao(): IngestItemDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun pushNotificationDao(): PushNotificationDao

    companion object {
        private const val DATABASE_NAME = "assistant.db"

        fun build(context: Context): AppDatabase = Room
            .databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(*DatabaseMigrations.ALL)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
}
