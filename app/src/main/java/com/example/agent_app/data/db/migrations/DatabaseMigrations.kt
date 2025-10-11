package com.example.agent_app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `ingest_items_fts` USING FTS4(`title`, `body`, content=`ingest_items`)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `ingest_item_embeddings` (" +
                    "`item_id` TEXT NOT NULL, " +
                    "`vector` BLOB NOT NULL, " +
                    "`dimension` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`item_id`)" +
                    ")"
            )
            database.execSQL("INSERT INTO ingest_items_fts(ingest_items_fts) VALUES('rebuild')")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
