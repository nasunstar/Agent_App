package com.example.agent_app.backend.services

import com.example.agent_app.backend.data.AdminAccountsTable
import com.example.agent_app.backend.models.AdminAccount
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * 관리자 계정 관리 서비스
 */
class AdminAccountService {
    
    /**
     * 계정 추가 또는 업데이트
     */
    fun upsertAccount(
        email: String,
        accessToken: String,
        refreshToken: String?,
        idToken: String?,
        scopes: List<String>,
        expiresIn: Int
    ): AdminAccount = transaction {
        val existingAccount = AdminAccountsTable.select { AdminAccountsTable.email eq email }
            .singleOrNull()
        
        val expiresAt = Instant.now().plusSeconds(expiresIn.toLong())
        val now = Instant.now()
        val scopesJson = Json.encodeToString(scopes)
        
        if (existingAccount != null) {
            // 업데이트
            AdminAccountsTable.update({ AdminAccountsTable.email eq email }) {
                it[AdminAccountsTable.accessToken] = accessToken
                it[AdminAccountsTable.refreshToken] = refreshToken
                it[AdminAccountsTable.idToken] = idToken
                it[AdminAccountsTable.scopes] = scopesJson
                it[AdminAccountsTable.expiresAt] = expiresAt
                it[AdminAccountsTable.updatedAt] = now
            }
        } else {
            // 신규 추가
            AdminAccountsTable.insert {
                it[AdminAccountsTable.email] = email
                it[AdminAccountsTable.accessToken] = accessToken
                it[AdminAccountsTable.refreshToken] = refreshToken
                it[AdminAccountsTable.idToken] = idToken
                it[AdminAccountsTable.scopes] = scopesJson
                it[AdminAccountsTable.expiresAt] = expiresAt
                it[AdminAccountsTable.createdAt] = now
                it[AdminAccountsTable.updatedAt] = now
            }
        }
        
        // 업데이트된 계정 반환
        AdminAccountsTable.select { AdminAccountsTable.email eq email }
            .single()
            .toAdminAccount()
    }
    
    /**
     * 모든 계정 조회
     */
    fun getAllAccounts(): List<AdminAccount> = transaction {
        AdminAccountsTable.selectAll()
            .orderBy(AdminAccountsTable.createdAt, SortOrder.DESC)
            .map { it.toAdminAccount() }
    }
    
    /**
     * 특정 계정 조회
     */
    fun getAccount(email: String): AdminAccount? = transaction {
        AdminAccountsTable.select { AdminAccountsTable.email eq email }
            .singleOrNull()
            ?.toAdminAccount()
    }
    
    /**
     * 계정 삭제
     */
    fun deleteAccount(email: String): Boolean = transaction {
        val deletedCount = AdminAccountsTable.deleteWhere { AdminAccountsTable.email eq email }
        deletedCount > 0
    }
    
    /**
     * ResultRow를 AdminAccount로 변환
     */
    private fun ResultRow.toAdminAccount(): AdminAccount {
        val scopesJson = this[AdminAccountsTable.scopes]
        val scopes = try {
            Json.decodeFromString<List<String>>(scopesJson)
        } catch (e: Exception) {
            emptyList()
        }
        
        return AdminAccount(
            id = this[AdminAccountsTable.id],
            email = this[AdminAccountsTable.email],
            scopes = scopes,
            expiresAt = this[AdminAccountsTable.expiresAt],
            createdAt = this[AdminAccountsTable.createdAt],
            updatedAt = this[AdminAccountsTable.updatedAt]
        )
    }
}

