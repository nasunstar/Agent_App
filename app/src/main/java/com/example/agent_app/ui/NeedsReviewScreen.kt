package com.example.agent_app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.ui.theme.Dimens
import com.example.agent_app.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MOA-Needs-Review: 검토 필요한 일정 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeedsReviewScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val needsReviewEvents = uiState.needsReviewEvents
    val isLoading = uiState.isSyncing
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("검토 필요한 일정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (needsReviewEvents.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(Dimens.spacingMD),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "검토할 일정 없음",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "검토할 일정이 없습니다",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "모든 일정이 승인되었습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(Dimens.spacingMD),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
            ) {
                item {
                    Text(
                        text = "⚠️ 검토 필요한 일정 ${needsReviewEvents.size}건",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                items(needsReviewEvents.size) { index ->
                    val event = needsReviewEvents[index]
                    NeedsReviewItemCard(
                        event = event,
                        viewModel = viewModel,
                        onEventUpdated = {
                            viewModel.loadNeedsReviewEvents()
                        }
                    )
                }
            }
        }
    }
}

/**
 * MOA-Needs-Review: 검토 필요한 일정 카드
 */
@Composable
private fun NeedsReviewItemCard(
    event: Event,
    viewModel: MainViewModel,
    onEventUpdated: () -> Unit
) {
    var showDetailDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    // 원본 IngestItem 조회 (비동기)
    var originalItem by remember { mutableStateOf<IngestItem?>(null) }
    var isLoadingOriginal by remember { mutableStateOf(false) }
    
    LaunchedEffect(event.sourceId) {
        if (event.sourceId != null) {
            isLoadingOriginal = true
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    originalItem = viewModel.getIngestItemById(event.sourceId)
                } catch (e: Exception) {
                    android.util.Log.e("NeedsReviewItemCard", "원본 데이터 조회 실패", e)
                } finally {
                    isLoadingOriginal = false
                }
            }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMD),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSM)
        ) {
            // 제목
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // 시간 정보
            if (event.startAt != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSM),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "시작 시간",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = TimeFormatter.format(event.startAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 장소
            if (event.location != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSM),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "장소",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = event.location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 출처 정보
            if (event.sourceType != null) {
                Text(
                    text = "출처: ${event.sourceType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.spacingXS))
            
            // 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSM)
            ) {
                Button(
                    onClick = { showDetailDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Filled.Info, contentDescription = "상세보기", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("상세보기")
                }
                
                Button(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "수정", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("수정")
                }
                
                Button(
                    onClick = {
                        viewModel.approveEvent(event)
                        onEventUpdated()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "승인", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("승인")
                }
            }
        }
    }
    
    // 상세 정보 다이얼로그
    if (showDetailDialog) {
        NeedsReviewDetailDialog(
            event = event,
            originalItem = originalItem,
            onDismiss = { showDetailDialog = false }
        )
    }
    
    // 수정 다이얼로그 - EventEditDialog 재사용
    if (showEditDialog) {
        EventEditDialog(
            event = event,
            onDismiss = { showEditDialog = false },
            onSave = { updatedEvent ->
                viewModel.updateEvent(updatedEvent)
                onEventUpdated()
                showEditDialog = false
            }
        )
    }
}

/**
 * MOA-Needs-Review: 검토 일정 상세 정보 다이얼로그
 */
@Composable
private fun NeedsReviewDetailDialog(
    event: Event,
    originalItem: IngestItem?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일정 상세 정보") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
            ) {
                // AI가 계산한 일정 정보
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Dimens.spacingMD),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSM)
                    ) {
                        Text(
                            text = "🤖 AI가 추출한 일정",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text("제목: ${event.title}")
                        if (event.startAt != null) {
                            Text("시작: ${TimeFormatter.format(event.startAt)}")
                        }
                        if (event.endAt != null) {
                            Text("종료: ${TimeFormatter.format(event.endAt)}")
                        }
                        if (event.location != null) {
                            Text("장소: ${event.location}")
                        }
                        if (event.body != null) {
                            Text("본문: ${event.body}")
                        }
                    }
                }
                
                // 원본 텍스트
                if (originalItem != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(Dimens.spacingMD),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSM)
                        ) {
                            Text(
                                text = "📄 원본 텍스트",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = originalItem.body ?: "(본문 없음)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                // Mismatch 정보 (body에 JSON이 포함된 경우)
                if (event.body != null && event.body.contains("validationMismatch")) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(Dimens.spacingMD),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSM)
                        ) {
                            Text(
                                text = "⚠️ 검증 불일치 정보",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = event.body,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

