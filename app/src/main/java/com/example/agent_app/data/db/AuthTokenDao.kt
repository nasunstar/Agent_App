package com.example.agent_app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface AuthTokenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(token: AuthTokenEntity): Long

    @Update
    suspend fun update(token: AuthTokenEntity)

    @Delete
    suspend fun delete(token: AuthTokenEntity)

    @Query(
        "DELETE FROM auth_tokens WHERE provider = :provider AND account_email = :accountEmail"
    )
    suspend fun delete(provider: String, accountEmail: String): Int

    @Query(
        "SELECT * FROM auth_tokens WHERE provider = :provider AND account_email = :accountEmail LIMIT 1"
    )
    suspend fun find(provider: String, accountEmail: String): AuthTokenEntity?

    @Query("SELECT * FROM auth_tokens WHERE provider = :provider ORDER BY updated_at DESC")
    suspend fun forProvider(provider: String): List<AuthTokenEntity>
}
