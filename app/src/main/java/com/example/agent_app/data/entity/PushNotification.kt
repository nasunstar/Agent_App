package com.example.agent_app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "push_notifications",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["package_name"]),
        Index(value = ["app_name"]),
    ]
)
data class PushNotification(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "app_name")
    val appName: String?,
    val title: String?,
    val text: String?,
    @ColumnInfo(name = "sub_text")
    val subText: String?,
    val timestamp: Long,
    @ColumnInfo(name = "meta_json")
    val metaJson: String?, // 추가 정보 (카테고리, 채널 등)
)

