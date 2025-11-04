package com.example.agent_app.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.agent_app.backend.AdminBackendServiceFactory
import com.example.agent_app.util.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 관리자 계정 관리 카드
 * 백엔드 서버와 통신하여 Google 계정을 추가하고 관리합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAccountCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<AdminAccountInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val backendApi = remember { AdminBackendServiceFactory.create(context) }
    
    // 계정 목록 로드
    val loadAccounts = {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = withContext(Dispatchers.IO) {
                    backendApi.getAdminAccounts()
                }
                accounts = response.accounts.map { dto ->
                    AdminAccountInfo(
                        id = dto.id,
                        email = dto.email,
                        createdAt = formatInstant(dto.createdAt),
                        scopes = dto.scopes
                    )
                }
            } catch (e: Exception) {
                errorMessage = "계정 목록 로드 실패: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadAccounts()
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "관리자 계정 관리",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                
                // 계정 추가 버튼
                Button(
                    onClick = {
                        scope.launch {
                            openGoogleAuthInBrowser(context)
                        }
                    },
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "계정 추가",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("계정 추가")
                }
            }
            
            Divider()
            
            // 설명
            Text(
                text = "시스템이 사용할 Google 계정을 추가하세요. Gmail API를 통해 메일 데이터를 수집합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            
            // 오류 메시지
            errorMessage?.let { error ->
                Text(
                    text = "⚠️ $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            
            // 계정 목록
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "등록된 계정이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    accounts.forEach { account ->
                        AccountItem(
                            account = account,
                            onDelete = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            backendApi.deleteAdminAccount(account.email)
                                        }
                                        loadAccounts() // 삭제 후 목록 새로고침
                                    } catch (e: Exception) {
                                        errorMessage = "계정 삭제 실패: ${e.message}"
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 개별 계정 아이템
 */
@Composable
private fun AccountItem(
    account: AdminAccountInfo,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.email,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "등록일: ${account.createdAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Custom Tabs로 Google OAuth 브라우저 열기
 */
private fun openGoogleAuthInBrowser(context: Context) {
    // BackendConfig를 사용하여 URL 가져오기 (자동으로 에뮬레이터 감지)
    val baseUrl = BackendConfig.getBackendUrl(context, BackendConfig.isEmulator())
    val backendUrl = "$baseUrl/admin/auth/google/start"
    
    val customTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    
    customTabsIntent.launchUrl(context, Uri.parse(backendUrl))
}

/**
 * ISO 8601 형식의 시간 문자열을 한국 시간대로 포맷팅
 */
private fun formatInstant(instantString: String): String {
    return try {
        val instant = Instant.parse(instantString)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Seoul"))
        formatter.format(instant)
    } catch (e: Exception) {
        instantString
    }
}

/**
 * 계정 정보 데이터 클래스
 */
data class AdminAccountInfo(
    val id: Long,
    val email: String,
    val createdAt: String,
    val scopes: List<String>,
)

