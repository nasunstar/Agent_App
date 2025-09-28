package com.example.agent_app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.agent_app.data.entity.AuthToken
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthTokenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(token: AuthToken)

    @Update
    suspend fun update(token: AuthToken)

    @Query("SELECT * FROM auth_tokens WHERE provider = :provider")
    suspend fun getByProvider(provider: String): AuthToken?

    @Query(
        "SELECT * FROM auth_tokens WHERE provider = :provider AND account_email = :accountEmail"
    )
    suspend fun getByProviderAndEmail(provider: String, accountEmail: String): AuthToken?

    @Query("DELETE FROM auth_tokens WHERE provider = :provider AND account_email = :accountEmail")
    suspend fun delete(provider: String, accountEmail: String)


    @Query("SELECT * FROM auth_tokens")
    fun observeAll(): Flow<List<AuthToken>>
}
