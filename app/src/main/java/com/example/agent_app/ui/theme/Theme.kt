package com.example.agent_app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// === MOA 브랜딩: 다크 모드 색상 스킴 ===
private val MoaDarkColorScheme = darkColorScheme(
    primary = MoaPrimary, // #77BFA3
    primaryContainer = MoaPrimaryDark.copy(alpha = 0.3f), // 나뭇잎 테마 녹색 계열
    secondary = MoaPrimaryLight, // 나뭇잎 테마에 맞게 녹색 계열로 변경
    secondaryContainer = MoaPrimaryDark.copy(alpha = 0.2f), // 나뭇잎 테마 녹색 계열
    tertiary = MoaPrimaryLight,
    background = MoaBackgroundDark, // #1A1A1A
    surface = Color(0xFF2D2D2D),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onPrimaryContainer = MoaPrimaryLight,
    onSecondaryContainer = MoaPrimaryLight,
    onBackground = MoaTextDark, // #E2E8F0
    onSurface = MoaTextDark,
)

// === MOA 브랜딩: 라이트 모드 색상 스킴 ===
private val MoaLightColorScheme = lightColorScheme(
    primary = MoaPrimary, // #77BFA3
    primaryContainer = MoaPrimaryLight.copy(alpha = 0.3f), // 나뭇잎 테마 녹색 계열
    secondary = MoaPrimaryLight, // 나뭇잎 테마에 맞게 녹색 계열로 변경
    secondaryContainer = MoaPrimaryLight.copy(alpha = 0.2f), // 나뭇잎 테마 녹색 계열
    tertiary = MoaPrimaryDark,
    background = MoaBackgroundLight, // #F5F8F6
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = MoaTextLight,
    onPrimaryContainer = MoaPrimaryDark,
    onSecondaryContainer = MoaPrimaryDark,
    onBackground = MoaTextLight, // #2D3748
    onSurface = MoaTextLight,
)

// === 기존 색상 스킴 (하위 호환성) ===
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

/**
 * UI 리브랜딩: 출처별 색상 확장 함수
 * 다크/라이트 모드에 따라 적절한 색상 반환
 */
@Composable
fun getSourceColor(source: String, isDark: Boolean = false): Color {
    return when (source.lowercase()) {
        "gmail", "mail" -> if (isDark) SourceMailDark else SourceMailLight
        "ocr", "image" -> if (isDark) SourceImageDark else SourceImageLight
        "chat", "sms" -> if (isDark) SourceChatDark else SourceChatLight
        "push_notification", "notification" -> if (isDark) SourceSmsDark else SourceSmsLight
        else -> if (isDark) PurpleGrey80 else Purple40
    }
}

@Composable
fun getSourceContainerColor(source: String, isDark: Boolean = false): Color {
    return when (source.lowercase()) {
        "gmail", "mail" -> if (isDark) SourceMailContainerDark else SourceMailContainer
        "ocr", "image" -> if (isDark) SourceImageContainerDark else SourceImageContainer
        "chat", "sms" -> if (isDark) SourceChatContainerDark else SourceChatContainer
        "push_notification", "notification" -> if (isDark) SourceSmsContainerDark else SourceSmsContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
}

@Composable
fun AgentAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // MOA 브랜딩 색상 사용을 위해 false로 변경
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        // MOA 브랜딩 색상 스킴 사용
        darkTheme -> MoaDarkColorScheme
        else -> MoaLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
