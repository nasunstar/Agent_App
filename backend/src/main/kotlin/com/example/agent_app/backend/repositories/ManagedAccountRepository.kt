package com.example.agent_app.backend.repositories

import com.example.agent_app.backend.data.ManagedGoogleAccountTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * ManagedGoogleAccount 레포지토리
 * DB에 계정 정보를 저장하고 조회합니다.
 */
class ManagedAccountRepository {
    
    /**
     * 계정 저장 또는 업데이트
     * @param googleEmail Google 계정 이메일
     * @param encryptedRefreshToken 암호화된 refresh_token
     */
    suspend fun save(googleEmail: String, encryptedRefreshToken: String): Unit = withContext(Dispatchers.IO) {
        transaction {
            val existingAccount = ManagedGoogleAccountTable.select {
                ManagedGoogleAccountTable.googleEmail eq googleEmail
            }.singleOrNull()
            
            val now = Instant.now()
            
            if (existingAccount != null) {
                // 업데이트
                ManagedGoogleAccountTable.update({ ManagedGoogleAccountTable.googleEmail eq googleEmail }) {
                    it[ManagedGoogleAccountTable.encryptedRefreshToken] = encryptedRefreshToken
                    it[ManagedGoogleAccountTable.updatedAt] = now
                }
            } else {
                // 신규 저장
                ManagedGoogleAccountTable.insert {
                    it[ManagedGoogleAccountTable.googleEmail] = googleEmail
                    it[ManagedGoogleAccountTable.encryptedRefreshToken] = encryptedRefreshToken
                    it[ManagedGoogleAccountTable.createdAt] = now
                    it[ManagedGoogleAccountTable.updatedAt] = now
                }
            }
        }
    }
    
    /**
     * 모든 계정 조회
     */
    suspend fun findAll(): List<ManagedAccount> = withContext(Dispatchers.IO) {
        transaction {
            ManagedGoogleAccountTable.selectAll()
                .orderBy(ManagedGoogleAccountTable.createdAt, SortOrder.DESC)
                .map { row ->
                    ManagedAccount(
                        id = row[ManagedGoogleAccountTable.id],
                        googleEmail = row[ManagedGoogleAccountTable.googleEmail],
                        encryptedRefreshToken = row[ManagedGoogleAccountTable.encryptedRefreshToken],
                        createdAt = row[ManagedGoogleAccountTable.createdAt],
                        updatedAt = row[ManagedGoogleAccountTable.updatedAt]
                    )
                }
        }
    }
    
    /**
     * 특정 이메일로 계정 조회
     */
    suspend fun findByEmail(email: String): ManagedAccount? = withContext(Dispatchers.IO) {
        transaction {
            ManagedGoogleAccountTable.select {
                ManagedGoogleAccountTable.googleEmail eq email
            }.singleOrNull()?.let { row ->
                ManagedAccount(
                    id = row[ManagedGoogleAccountTable.id],
                    googleEmail = row[ManagedGoogleAccountTable.googleEmail],
                    encryptedRefreshToken = row[ManagedGoogleAccountTable.encryptedRefreshToken],
                    createdAt = row[ManagedGoogleAccountTable.createdAt],
                    updatedAt = row[ManagedGoogleAccountTable.updatedAt]
                )
            }
        }
    }
    
    /**
     * 계정 삭제
     */
    suspend fun delete(email: String): Boolean = withContext(Dispatchers.IO) {
        transaction {
            val deletedCount = ManagedGoogleAccountTable.deleteWhere {
                ManagedGoogleAccountTable.googleEmail eq email
            }
            deletedCount > 0
        }
    }
}

/**
 * 관리된 계정 데이터 클래스
 */
data class ManagedAccount(
    val id: Long,
    val googleEmail: String,
    val encryptedRefreshToken: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

