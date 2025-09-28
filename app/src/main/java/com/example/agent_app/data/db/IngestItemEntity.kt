package com.example.agent_app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "ingest_items",
    indices = [Index(value = ["source", "external_id"], unique = true)]
)
data class IngestItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val source: String,
    @ColumnInfo(name = "external_id")
    val externalId: String? = null,
    val subject: String,
    val body: String,
    val summary: String? = null,
    @ColumnInfo(name = "labels")
    val labels: List<String> = emptyList(),
    @ColumnInfo(name = "due_at")
    val dueAt: Instant? = null,
    @ColumnInfo(name = "received_at")
    val receivedAt: Instant? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now()
)
