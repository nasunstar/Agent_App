package com.example.agent_app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE auth_tokens ADD COLUMN account_email TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE auth_tokens ADD COLUMN server_auth_code TEXT")
            database.execSQL("ALTER TABLE auth_tokens ADD COLUMN id_token TEXT")
            database.execSQL("ALTER TABLE auth_tokens ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
