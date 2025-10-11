package com.example.agent_app.data.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = IngestItem::class)
@Entity(tableName = "ingest_items_fts")
data class IngestItemFts(
    val title: String?,
    val body: String?,
)
