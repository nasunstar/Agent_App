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

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Event 테이블에 출처 정보 필드 추가 (Gmail, OCR, 수동 입력 구분)
            database.execSQL("ALTER TABLE events ADD COLUMN source_type TEXT")
            database.execSQL("ALTER TABLE events ADD COLUMN source_id TEXT")
            
            // source_id 인덱스 생성 (빠른 원본 데이터 조회용)
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_events_source_id` ON `events` (`source_id`)")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // EventNotification 테이블에 sent_at 컬럼 추가 (알림 발송 시간 추적)
            database.execSQL("ALTER TABLE event_notifications ADD COLUMN sent_at INTEGER")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // AuthToken 테이블에 email 컬럼 추가 및 복합 키로 변경 (여러 Google 계정 지원)
            // 기존 테이블에는 email 컬럼이 없으므로 빈 문자열('')을 기본값으로 사용
            
            // 1. 임시 테이블 생성
            database.execSQL("CREATE TABLE IF NOT EXISTS auth_tokens_new (provider TEXT NOT NULL, email TEXT NOT NULL DEFAULT '', access_token TEXT NOT NULL, refresh_token TEXT, scope TEXT, expires_at INTEGER, PRIMARY KEY(provider, email))")
            
            // 2. 기존 데이터 마이그레이션 (email 컬럼이 없으므로 빈 문자열('')을 기본값으로 사용)
            database.execSQL("INSERT INTO auth_tokens_new (provider, email, access_token, refresh_token, scope, expires_at) SELECT provider, '' as email, access_token, refresh_token, scope, expires_at FROM auth_tokens")
            
            // 3. 기존 테이블 삭제
            database.execSQL("DROP TABLE auth_tokens")
            
            // 4. 새 테이블 이름 변경
            database.execSQL("ALTER TABLE auth_tokens_new RENAME TO auth_tokens")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 푸시 알림 테이블 생성
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `push_notifications` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`package_name` TEXT NOT NULL, " +
                    "`app_name` TEXT, " +
                    "`title` TEXT, " +
                    "`text` TEXT, " +
                    "`sub_text` TEXT, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`meta_json` TEXT" +
                    ")"
            )
            
            // 인덱스 생성
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_push_notifications_timestamp` ON `push_notifications` (`timestamp`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_push_notifications_package_name` ON `push_notifications` (`package_name`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_push_notifications_app_name` ON `push_notifications` (`app_name`)")
        }
    }

    // MOA-Event-Confidence: Event 테이블에 confidence 필드 추가
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Event 테이블에 confidence 컬럼 추가 (nullable Double)
            database.execSQL("ALTER TABLE events ADD COLUMN confidence REAL")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
}
