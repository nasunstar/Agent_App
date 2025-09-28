package com.example.agent_app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ingest_items",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["due_date"]),
    ]
)
data class IngestItem(
    @PrimaryKey
    val id: String,
    val source: String,
    val type: String?,
    val title: String?,
    val body: String?,
    val timestamp: Long,
    @ColumnInfo(name = "due_date")
    val dueDate: Long?,
    val confidence: Double?,
    @ColumnInfo(name = "meta_json")
    val metaJson: String?,
)
