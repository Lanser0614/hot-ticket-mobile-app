package com.example.hotticketapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF4FAAF1), onPrimary = Color(0xFF081624),
    secondary = Color(0xFFFF8A3D), background = Color(0xFF101922),
    surface = Color(0xFF1C2733), surfaceVariant = Color(0xFF293847),
    onBackground = Color(0xFFF2F4F6), onSurface = Color(0xFFF2F4F6),
    onSurfaceVariant = Color(0xFF9AAABA), error = Color(0xFFFF8A8A)
)
private val Light = lightColorScheme(
    primary = Color(0xFF1677B8), onPrimary = Color.White,
    secondary = Color(0xFFBF560D), background = Color(0xFFF6F8FC),
    surface = Color.White, surfaceVariant = Color(0xFFE9EFF5),
    onBackground = Color(0xFF132137), onSurface = Color(0xFF132137)
)
@Composable
fun HotTicketApplicationTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography, content = content)
}
