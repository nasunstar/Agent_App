package com.example.agent_app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperMenuDrawer(
    uiState: AssistantUiState,
    mainViewModel: MainViewModel,
    onAccessTokenChange: (String) -> Unit,
    onRefreshTokenChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onScopeChange: (String) -> Unit,
    onExpiresAtChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
    onSync: () -> Unit,
    onResetDatabase: () -> Unit,
    onClearEvents: () -> Unit,
    onCloseDrawer: () -> Unit,
    googleSignInLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "개발자 기능",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            
            HorizontalDivider()
        }
        
        // 요약(Overview) 컨텐츠를 사이드바에 포함
        // AssistantContent가 자체적으로 스크롤을 처리하므로 외부 스크롤 제거
        LaunchedEffect(Unit) {
            uiState.smsScanState.message?.let {
                mainViewModel.consumeStatusMessage()
            }
        }
        
        DeveloperContent(
            uiState = uiState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            mainViewModel = mainViewModel,
            onAccessTokenChange = onAccessTokenChange,
            onRefreshTokenChange = onRefreshTokenChange,
            onEmailChange = onEmailChange,
            onScopeChange = onScopeChange,
            onExpiresAtChange = onExpiresAtChange,
            onSaveToken = onSaveToken,
            onClearToken = onClearToken,
            onSync = onSync,
            onResetDatabase = onResetDatabase,
            onClearEvents = onClearEvents,
            googleSignInLauncher = googleSignInLauncher,
        )
    }
}

