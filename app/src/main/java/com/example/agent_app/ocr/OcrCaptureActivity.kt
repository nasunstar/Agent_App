package com.example.agent_app.ocr

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                    }
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
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(OcrCaptureViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return OcrCaptureViewModel(ocrClient, ocrRepository) as T
    }
}

private class OcrCaptureViewModel(
    private val ocrClient: OcrClient,
    private val ocrRepository: OcrRepositoryWithAi,
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
    ) : OcrCaptureUiState

    data class Error(val message: String) : OcrCaptureUiState
}

@Composable
private fun OcrCaptureScreen(
    uiState: OcrCaptureUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            OcrCaptureUiState.Loading -> OcrLoading(onClose)
            is OcrCaptureUiState.Success -> OcrSuccess(uiState, onClose)
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
private fun OcrSuccess(state: OcrCaptureUiState.Success, onClose: () -> Unit) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "새 일정이 저장되었습니다",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "AI Confidence: ${"%.2f".format(state.confidence)} (${state.classificationType})",
            style = MaterialTheme.typography.bodyMedium,
        )
        OcrDetailRow("일정 제목", state.eventTitle)
        OcrDetailRow("시작 시간", state.startAt?.let { TimeFormatter.format(it) } ?: "미정")
        OcrDetailRow("종료 시간", state.endAt?.let { TimeFormatter.format(it) } ?: "미정")
        OcrDetailRow("장소", state.location ?: "미정")
        OcrDetailRow("Event ID", state.eventId.toString())

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
