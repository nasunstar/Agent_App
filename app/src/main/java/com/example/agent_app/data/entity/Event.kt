package com.example.agent_app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EventType::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["type_id"]),
        Index(value = ["start_at"]),
        Index(value = ["end_at"]),
        Index(value = ["source_id"]),
    ]
)
data class Event(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: Long,
    @ColumnInfo(name = "type_id")
    val typeId: Long?,
    val title: String,
    val body: String?,
    @ColumnInfo(name = "start_at")
    val startAt: Long?,
    @ColumnInfo(name = "end_at")
    val endAt: Long?,
    val location: String?,
    val status: String?,
    
    // 일정의 출처 정보
    @ColumnInfo(name = "source_type")
    val sourceType: String? = null,  // "gmail", "ocr", "manual" 등
    
    @ColumnInfo(name = "source_id")
    val sourceId: String? = null,  // ingest_items의 id (원본 데이터 참조용)
    
    // MOA-Event-Confidence: 일정 신뢰도 (IngestItem의 confidence에서 매핑)
    val confidence: Double? = null,  // AI가 추출한 일정의 신뢰도 (0.0~1.0)
)
