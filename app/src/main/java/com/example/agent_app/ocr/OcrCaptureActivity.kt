package com.example.agent_app.ocr

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.agent_app.data.entity.Event
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.agent_app.data.repo.OcrRepositoryWithAi
import com.example.agent_app.di.AppContainer
import com.example.agent_app.ui.theme.AgentAppTheme
import com.example.agent_app.util.TimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OcrCaptureActivity : ComponentActivity() {

    private val appContainer: AppContainer by lazy { AppContainer(applicationContext) }
    private val ocrClient: OcrClient by lazy { OcrClient(applicationContext) }

    private val viewModel: OcrCaptureViewModel by viewModels {
        OcrCaptureViewModelFactory(
            ocrClient = ocrClient,
            ocrRepository = appContainer.ocrRepository,
            eventDao = appContainer.eventDao,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUri = extractSharedImageUri(intent)
        if (imageUri != null) {
            viewModel.startProcessing(imageUri)
        } else {
            viewModel.notifyMissingImage()
        }

        setContent {
            AgentAppTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                OcrCaptureScreen(
                    uiState = uiState,
                    onClose = { finish() },
                    onRetry = {
                        imageUri?.let { viewModel.retryProcessing(it) } ?: viewModel.notifyMissingImage()
                    },
                    onUpdateEvent = { event -> viewModel.updateEvent(event) }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ocrClient.close()
        appContainer.close()
    }

    private fun extractSharedImageUri(intent: Intent?): Uri? {
        if (intent == null) return null
        val directUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        return directUri ?: intent.clipData?.getItemAt(0)?.uri
    }
}

private class OcrCaptureViewModelFactory(
    private val ocrClient: OcrClient,
    private val ocrRepository: OcrRepositoryWithAi,
    private val eventDao: com.example.agent_app.data.dao.EventDao,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(OcrCaptureViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return OcrCaptureViewModel(ocrClient, ocrRepository, eventDao) as T
    }
}

private class OcrCaptureViewModel(
    private val ocrClient: OcrClient,
    private val ocrRepository: OcrRepositoryWithAi,
    private val eventDao: com.example.agent_app.data.dao.EventDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow<OcrCaptureUiState>(OcrCaptureUiState.Loading)
    val uiState: StateFlow<OcrCaptureUiState> = _uiState.asStateFlow()

    private var hasProcessed = false

    fun startProcessing(uri: Uri) {
        if (hasProcessed) return
        hasProcessed = true
        processImage(uri)
    }

    fun retryProcessing(uri: Uri) {
        hasProcessed = true
        processImage(uri)
    }

    fun notifyMissingImage() {
        _uiState.value = OcrCaptureUiState.Error("공유된 이미지에서 읽을 수 있는 텍스트가 없습니다.")
    }

    fun updateEvent(event: Event) {
        viewModelScope.launch {
            try {
                eventDao.update(event)
                // UI 상태 업데이트
                val currentState = _uiState.value
                if (currentState is OcrCaptureUiState.Success) {
                    val updatedEvents = currentState.allEvents.map {
                        if (it.id == event.id) event else it
                    }
                    _uiState.value = currentState.copy(allEvents = updatedEvents)
                }
            } catch (t: Throwable) {
                android.util.Log.e("OcrCaptureViewModel", "이벤트 업데이트 실패", t)
            }
        }
    }

    private fun processImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = OcrCaptureUiState.Loading
            try {
                val recognizedText = ocrClient.recognizeTextFromUri(uri).trim()
                if (recognizedText.isBlank()) {
                    _uiState.value = OcrCaptureUiState.Error("이미지에서 텍스트를 인식하지 못했습니다.")
                    return@launch
                }
                val result = ocrRepository.processOcrText(recognizedText)
                _uiState.value = OcrCaptureUiState.Success(
                    recognizedText = recognizedText,
                    eventTitle = result.eventTitle ?: "제목 없음",
                    startAt = result.startAt,
                    endAt = result.endAt,
                    location = result.location,
                    confidence = result.confidence,
                    classificationType = result.eventType,
                    eventId = result.eventId ?: 0L,
                    totalEventCount = result.totalEventCount,
                    allEvents = result.allEvents,  // 모든 이벤트 추가
                )
            } catch (t: Throwable) {
                _uiState.value = OcrCaptureUiState.Error(
                    t.message ?: "OCR 처리 중 오류가 발생했습니다."
                )
            }
        }
    }
}

private sealed interface OcrCaptureUiState {
    data object Loading : OcrCaptureUiState
    data class Success(
        val recognizedText: String,
        val eventTitle: String,
        val startAt: Long?,
        val endAt: Long?,
        val location: String?,
        val confidence: Double,
        val classificationType: String,
        val eventId: Long,
        val totalEventCount: Int = 1,  // 생성된 이벤트 개수
        val allEvents: List<Event> = emptyList(),  // 모든 이벤트
    ) : OcrCaptureUiState

    data class Error(val message: String) : OcrCaptureUiState
}

@Composable
private fun OcrCaptureScreen(
    uiState: OcrCaptureUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onUpdateEvent: (Event) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            OcrCaptureUiState.Loading -> OcrLoading(onClose)
            is OcrCaptureUiState.Success -> OcrSuccess(uiState, onClose, onUpdateEvent)
            is OcrCaptureUiState.Error -> OcrError(uiState.message, onClose, onRetry)
        }
    }
}

@Composable
private fun OcrLoading(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "이미지에서 텍스트를 추출하고 일정을 생성하는 중입니다...")
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onClose) {
            Text(text = "닫기")
        }
    }
}

@Composable
private fun OcrSuccess(
    state: OcrCaptureUiState.Success,
    onClose: () -> Unit,
    onUpdateEvent: (Event) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val titleText = if (state.totalEventCount > 1) {
            "${state.totalEventCount}개의 일정이 저장되었습니다"
        } else {
            "새 일정이 저장되었습니다"
        }
        Text(
            text = titleText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        
        Text(
            text = "AI Confidence: ${"%.2f".format(state.confidence)} (${state.classificationType})",
            style = MaterialTheme.typography.bodyMedium,
        )
        
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        
        // 모든 이벤트 표시
        Text(
            text = "추출된 일정 목록",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        
        if (state.allEvents.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.allEvents.forEachIndexed { index, event ->
                    EventCard(
                        event = event,
                        index = index + 1,
                        totalCount = state.allEvents.size,
                        onUpdateEvent = onUpdateEvent
                    )
                }
            }
        } else {
            Text(
                text = "추출된 일정이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        
        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "인식된 원본 텍스트",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = state.recognizedText,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "완료")
        }
    }
}

@Composable
private fun OcrError(message: String, onClose: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "이미지를 처리할 수 없습니다",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = onClose,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "닫기")
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "다시 시도")
            }
        }
    }
}

@Composable
private fun EventCard(
    event: Event,
    index: Int,
    totalCount: Int,
    onUpdateEvent: (Event) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (totalCount > 1) "일정 $index/$totalCount" else "일정",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { showEditDialog = true }) {
                    Text("수정")
                }
            }
            
            Text(
                text = "제목: ${event.title}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            
            if (event.startAt != null) {
                Text(
                    text = "시작: ${TimeFormatter.format(event.startAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            
            if (event.endAt != null) {
                Text(
                    text = "종료: ${TimeFormatter.format(event.endAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            
            if (event.location != null) {
                Text(
                    text = "장소: ${event.location}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    
    if (showEditDialog) {
        EventEditDialog(
            event = event,
            onDismiss = { showEditDialog = false },
            onSave = { updatedEvent ->
                onUpdateEvent(updatedEvent)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun EventEditDialog(
    event: Event,
    onDismiss: () -> Unit,
    onSave: (Event) -> Unit
) {
    var title by remember { mutableStateOf(event.title) }
    var startAtText by remember {
        mutableStateOf(event.startAt?.let { TimeFormatter.format(it) } ?: "")
    }
    var endAtText by remember {
        mutableStateOf(event.endAt?.let { TimeFormatter.format(it) } ?: "")
    }
    var location by remember { mutableStateOf(event.location ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일정 수정") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = startAtText,
                    onValueChange = { startAtText = it },
                    label = { Text("시작 시간 (읽기 전용)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = endAtText,
                    onValueChange = { endAtText = it },
                    label = { Text("종료 시간 (읽기 전용)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("장소") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Text(
                    text = "※ 현재 날짜/시간 수정은 지원되지 않습니다. 제목과 장소만 수정할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val updatedEvent = event.copy(
                    title = title,
                    location = location.takeIf { it.isNotBlank() }
                )
                onSave(updatedEvent)
            }) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun OcrDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
