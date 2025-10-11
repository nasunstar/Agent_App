package com.example.agent_app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ingest_item_embeddings")
data class IngestItemEmbedding(
    @PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: String,
    val vector: ByteArray,
    val dimension: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
