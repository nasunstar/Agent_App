package com.example.agent_app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent_app.R
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.data.entity.Contact
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.entity.Note
import com.example.agent_app.util.TimeFormatter
import com.example.agent_app.ui.chat.ChatScreen
import com.example.agent_app.ui.chat.ChatViewModel

@Composable
fun AssistantApp(
    mainViewModel: MainViewModel,
    chatViewModel: ChatViewModel,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableStateOf(AssistantTab.Overview) }

    LaunchedEffect(uiState.loginState.statusMessage, uiState.syncMessage) {
        val messages = listOfNotNull(uiState.loginState.statusMessage, uiState.syncMessage)
        if (messages.isNotEmpty()) {
            messages.forEach { snackbarHostState.showSnackbar(it) }
            mainViewModel.consumeStatusMessage()
        }
    }

    AssistantScaffold(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        chatViewModel = chatViewModel,
        onAccessTokenChange = mainViewModel::updateAccessToken,
        onRefreshTokenChange = mainViewModel::updateRefreshToken,
        onScopeChange = mainViewModel::updateScope,
        onExpiresAtChange = mainViewModel::updateExpiresAt,
        onSaveToken = mainViewModel::saveToken,
        onClearToken = mainViewModel::clearToken,
        onSync = mainViewModel::syncGmail,
        onResetDatabase = mainViewModel::resetDatabase,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantScaffold(
    uiState: AssistantUiState,
    snackbarHostState: SnackbarHostState,
    selectedTab: AssistantTab,
    onTabSelected: (AssistantTab) -> Unit,
    chatViewModel: ChatViewModel,
    onAccessTokenChange: (String) -> Unit,
    onRefreshTokenChange: (String) -> Unit,
    onScopeChange: (String) -> Unit,
    onExpiresAtChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
    onSync: () -> Unit,
    onResetDatabase: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                AssistantTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        label = { Text(tab.label) },
                        icon = { Spacer(modifier = Modifier.size(0.dp)) },
                    )
                }
            }
        },
    ) { paddingValues ->
        when (selectedTab) {
            AssistantTab.Overview -> AssistantContent(
                uiState = uiState,
                contentPadding = paddingValues,
                onAccessTokenChange = onAccessTokenChange,
                onRefreshTokenChange = onRefreshTokenChange,
                onScopeChange = onScopeChange,
                onExpiresAtChange = onExpiresAtChange,
                onSaveToken = onSaveToken,
                onClearToken = onClearToken,
                onSync = onSync,
                onResetDatabase = onResetDatabase,
            )

            AssistantTab.Chat -> ChatScreen(
                viewModel = chatViewModel,
                modifier = Modifier.padding(paddingValues),
            )

            AssistantTab.DbCheck -> DbCheckContent(
                ocrItems = uiState.ocrItems,
                ocrEvents = uiState.ocrEvents,
                contentPadding = paddingValues,
            )
        }
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
    onResetDatabase: () -> Unit,
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
                   onResetDatabase = onResetDatabase,
               )
               ClassifiedDataCard(
                   contacts = uiState.contacts,
                   events = uiState.events,
                   notes = uiState.notes,
               )
    }
}

private enum class AssistantTab(val label: String) {
    Overview("요약"),
    Chat("챗봇"),
    DbCheck("DB확인"),
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
                text = "Google 계정 로그인을 통해 gmail.readonly 권한을 부여하면 토큰이 자동으로 저장됩니다. 필요 시 아래 필드를 사용해 수동으로 토큰을 입력할 수도 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            // Google 로그인 버튼은 현재 비활성화
            // Button(
            //     onClick = onGoogleLogin,
            //     enabled = !loginState.isGoogleLoginInProgress,
            // ) {
            //     if (loginState.isGoogleLoginInProgress) {
            //         CircularProgressIndicator(
            //             modifier = Modifier.size(18.dp),
            //             strokeWidth = 2.dp,
            //             color = MaterialTheme.colorScheme.onPrimary,
            //         )
            //         Spacer(modifier = Modifier.width(8.dp))
            //         Text(text = "로그인 진행 중...")
            //     } else {
            //         Text(text = "Google 계정으로 로그인")
            //     }
            // }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
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
    onResetDatabase: () -> Unit,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSync, enabled = !isSyncing) {
                        Text(text = "최근 20개 동기화")
                    }
                    TextButton(
                        onClick = onResetDatabase,
                        enabled = !isSyncing
                    ) {
                        Text(text = "DB 초기화")
                    }
                }
            }
            if (isSyncing) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
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
        if (item.dueDate != null || item.confidence != null) {
            Spacer(modifier = Modifier.height(4.dp))
            val dueText = item.dueDate?.let { "예상 일정: ${TimeFormatter.format(it)}" }
            val confidenceText = item.confidence?.let { "신뢰도 ${(it * 100).coerceIn(0.0, 100.0).toInt()}%" }
            val summary = listOfNotNull(dueText, confidenceText).joinToString(separator = " · ")
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ClassifiedDataCard(
    contacts: List<Contact>,
    events: List<Event>,
    notes: List<Note>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "분류된 데이터",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            
            // 연락처 섹션
            if (contacts.isNotEmpty()) {
                Text(
                    text = "연락처 (${contacts.size}개)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                contacts.take(3).forEach { contact ->
                    Text(
                        text = "${contact.name} - ${contact.email ?: contact.phone ?: "정보 없음"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (contacts.size > 3) {
                    Text(
                        text = "... 외 ${contacts.size - 3}개",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Divider()
            }
            
            // 이벤트 섹션
            if (events.isNotEmpty()) {
                Text(
                    text = "일정 (${events.size}개)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                events.take(3).forEach { event ->
                    Text(
                        text = "${event.title} - ${event.location ?: "장소 미정"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (events.size > 3) {
                    Text(
                        text = "... 외 ${events.size - 3}개",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Divider()
            }
            
            // 노트 섹션
            if (notes.isNotEmpty()) {
                Text(
                    text = "메모 (${notes.size}개)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                notes.take(3).forEach { note ->
                    Text(
                        text = "${note.title} - ${note.body.take(50)}${if (note.body.length > 50) "..." else ""}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (notes.size > 3) {
                    Text(
                        text = "... 외 ${notes.size - 3}개",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            
            if (contacts.isEmpty() && events.isEmpty() && notes.isEmpty()) {
                Text(
                    text = "Gmail을 동기화하면 AI가 자동으로 분류한 데이터가 여기에 표시됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DbCheckContent(
    ocrItems: List<IngestItem>,
    ocrEvents: Map<String, List<Event>>,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "OCR DB 확인",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "이미지에서 추출된 텍스트와 일정 내역",
                    style = MaterialTheme.typography.bodyMedium,
                )
                
                if (ocrItems.isEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "저장된 OCR 데이터가 없습니다. 이미지를 공유하여 일정을 추출해 보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = "총 ${ocrItems.size}개의 OCR 데이터",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ocrItems.forEachIndexed { index, item ->
                            OcrItemCard(
                                item = item,
                                events = ocrEvents[item.id] ?: emptyList(),
                            )
                            if (index < ocrItems.lastIndex) {
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrItemCard(
    item: IngestItem,
    events: List<Event>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // OCR 텍스트 정보
            Text(
                text = "📷 OCR 텍스트",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            
            Text(
                text = "제목: ${item.title ?: "(제목 없음)"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            
            Text(
                text = item.body.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            
            Text(
                text = "추출 시간: ${TimeFormatter.format(item.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
            )
            
            if (item.confidence != null) {
                Text(
                    text = "신뢰도: ${(item.confidence * 100).coerceIn(0.0, 100.0).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            
            // 연결된 이벤트 표시
            if (events.isNotEmpty()) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = "📅 추출된 일정 (${events.size}개)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    events.forEach { event ->
                        EventDetailRow(event)
                    }
                }
            } else {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "추출된 일정 없음",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun EventDetailRow(event: Event) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "• ${event.title}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        
        if (event.startAt != null) {
            Text(
                text = "시작: ${TimeFormatter.format(event.startAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        
        if (event.endAt != null) {
            Text(
                text = "종료: ${TimeFormatter.format(event.endAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        
        if (event.location != null) {
            Text(
                text = "장소: ${event.location}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
