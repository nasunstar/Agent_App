package com.example.agent_app.data.db

import androidx.room.Fts5
import androidx.room.FtsOptions
import androidx.room.Entity
import androidx.room.ColumnInfo

@Fts5(
    contentEntity = IngestItemEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    prefix = [2, 3]
)
@Entity(tableName = "ingest_items_fts")
internal data class IngestItemFtsEntity(
    val subject: String,
    val body: String,
    @ColumnInfo(name = "summary")
    val summary: String?
)
