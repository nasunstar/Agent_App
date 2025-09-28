package com.example.agent_app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        IngestItemEntity::class,
        IngestItemFtsEntity::class,
        AuthTokenEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ingestItemDao(): IngestItemDao

    abstract fun authTokenDao(): AuthTokenDao

    companion object {
        const val DATABASE_NAME: String = "agent_app.db"

        fun build(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME
        ).build()
    }
}
