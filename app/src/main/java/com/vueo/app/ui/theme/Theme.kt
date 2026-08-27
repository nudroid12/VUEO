package com.vueo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VueoDark = darkColorScheme(
    primary = Color(0xFF8CE66A),
    onPrimary = Color(0xFF0A1307),
    secondary = Color(0xFFA9D49A),
    background = Color(0xFF080C0E),
    onBackground = Color(0xFFF4F7F2),
    surface = Color(0xFF10171A),
    onSurface = Color(0xFFE7ECE5),
    surfaceVariant = Color(0xFF172024),
    outline = Color(0xFF344146),
)

@Composable
fun VueoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VueoDark,
        typography = MaterialTheme.typography,
        content = content,
    )
}
