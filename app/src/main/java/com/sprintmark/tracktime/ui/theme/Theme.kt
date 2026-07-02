package com.sprintmark.tracktime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SprintLightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF144C59),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = androidx.compose.ui.graphics.Color(0xFF8C5A2B),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFFF5F7F8),
    onBackground = androidx.compose.ui.graphics.Color(0xFF101416),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF101416),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE3E8EA)
)

@Composable
fun TrackTimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SprintLightColors,
        typography = Typography(),
        content = content
    )
}
