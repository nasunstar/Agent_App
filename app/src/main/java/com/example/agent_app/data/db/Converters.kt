package com.example.agent_app.data.db

import androidx.room.TypeConverter
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromEpochMillis(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun toEpochMillis(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun fromDelimitedString(value: String?): List<String> =
        value?.split(DELIMITER)?.filter { it.isNotBlank() } ?: emptyList()

    @TypeConverter
    fun toDelimitedString(values: List<String>?): String? =
        values?.takeIf { it.isNotEmpty() }?.joinToString(DELIMITER)

    private companion object {
        const val DELIMITER = "\u001F"
    }
}
