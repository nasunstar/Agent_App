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

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Event 테이블에 body 컬럼 추가
            database.execSQL("ALTER TABLE events ADD COLUMN body TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
