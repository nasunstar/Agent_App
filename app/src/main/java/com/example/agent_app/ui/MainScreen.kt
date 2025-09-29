package com.example.agent_app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent_app.R
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.util.TimeFormatter

@Composable
fun AssistantApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.loginState.statusMessage, uiState.syncMessage) {
        val messages = listOfNotNull(uiState.loginState.statusMessage, uiState.syncMessage)
        if (messages.isNotEmpty()) {
            messages.forEach { snackbarHostState.showSnackbar(it) }
            viewModel.consumeStatusMessage()
        }
    }

    AssistantScaffold(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onAccessTokenChange = viewModel::updateAccessToken,
        onRefreshTokenChange = viewModel::updateRefreshToken,
        onScopeChange = viewModel::updateScope,
        onExpiresAtChange = viewModel::updateExpiresAt,
        onSaveToken = viewModel::saveToken,
        onClearToken = viewModel::clearToken,
        onSync = viewModel::syncGmail,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantScaffold(
    uiState: AssistantUiState,
    snackbarHostState: SnackbarHostState,
    onAccessTokenChange: (String) -> Unit,
    onRefreshTokenChange: (String) -> Unit,
    onScopeChange: (String) -> Unit,
    onExpiresAtChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
    onSync: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        AssistantContent(
            uiState = uiState,
            contentPadding = paddingValues,
            onAccessTokenChange = onAccessTokenChange,
            onRefreshTokenChange = onRefreshTokenChange,
            onScopeChange = onScopeChange,
            onExpiresAtChange = onExpiresAtChange,
            onSaveToken = onSaveToken,
            onClearToken = onClearToken,
            onSync = onSync,
        )
    }
}

@Composable
private fun AssistantContent(
    uiState: AssistantUiState,
    contentPadding: PaddingValues,
    onAccessTokenChange: (String) -> Unit,
    onRefreshTokenChange: (String) -> Unit,
    onScopeChange: (String) -> Unit,
    onExpiresAtChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
    onSync: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        LoginCard(
            loginState = uiState.loginState,
            onAccessTokenChange = onAccessTokenChange,
            onRefreshTokenChange = onRefreshTokenChange,
            onScopeChange = onScopeChange,
            onExpiresAtChange = onExpiresAtChange,
            onSaveToken = onSaveToken,
            onClearToken = onClearToken,
        )
        GmailCard(
            items = uiState.gmailItems,
            isSyncing = uiState.isSyncing,
            onSync = onSync,
        )
    }
}

@Composable
private fun LoginCard(
    loginState: LoginUiState,
    onAccessTokenChange: (String) -> Unit,
    onRefreshTokenChange: (String) -> Unit,
    onScopeChange: (String) -> Unit,
    onExpiresAtChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Google 로그인 설정",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "OAuth 플로우를 완료한 뒤 발급받은 액세스 토큰을 입력하면 최근 Gmail을 동기화할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = loginState.accessTokenInput,
                onValueChange = onAccessTokenChange,
                label = { Text("Access Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = loginState.refreshTokenInput,
                onValueChange = onRefreshTokenChange,
                label = { Text("Refresh Token (선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = loginState.scopeInput,
                onValueChange = onScopeChange,
                label = { Text("Scope") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = loginState.expiresAtInput,
                onValueChange = onExpiresAtChange,
                label = { Text("만료 시각 (epoch ms, 선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (loginState.hasStoredToken) {
                Divider(modifier = Modifier.padding(top = 8.dp))
                val scope = loginState.storedScope ?: "미지"
                val expiry = loginState.storedExpiresAt?.let { TimeFormatter.format(it) } ?: "만료 시간 미설정"
                Text(
                    text = "저장된 Scope: $scope",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "만료 예정: $expiry",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSaveToken) {
                    Text(text = "토큰 저장")
                }
                TextButton(onClick = onClearToken) {
                    Text(text = "토큰 삭제")
                }
            }
        }
    }
}

@Composable
private fun GmailCard(
    items: List<IngestItem>,
    isSyncing: Boolean,
    onSync: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Gmail 수집함",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = onSync, enabled = !isSyncing) {
                    Text(text = "최근 20개 동기화")
                }
            }
            if (isSyncing) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
            if (items.isEmpty()) {
                Text(
                    text = "저장된 메시지가 없습니다. 동기화를 실행해 보세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items.forEachIndexed { index, item ->
                        GmailMessageRow(item)
                        if (index < items.lastIndex) {
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GmailMessageRow(item: IngestItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.title ?: "(제목 없음)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.body.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "수신: ${TimeFormatter.format(item.timestamp)}",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
