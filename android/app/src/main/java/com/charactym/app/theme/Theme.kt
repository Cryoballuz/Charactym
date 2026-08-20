package com.charactym.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Charactym 主题：固定浅色（不跟随系统深色模式，不使用动态取色），
 * 配色遵循设计规范：淡蓝 - 白 - 灰（docs/03-design-spec.md）。
 */
private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF5B8BC0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E4F5),
        onPrimaryContainer = Color(0xFF1D3A5F),
        secondary = Color(0xFF7FA8D9),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE3EDF8),
        onSecondaryContainer = Color(0xFF27476E),
        background = Color(0xFFF7F9FB),
        onBackground = Color(0xFF333333),
        surface = Color.White,
        onSurface = Color(0xFF333333),
        surfaceVariant = Color(0xFFEFF3F8),
        onSurfaceVariant = Color(0xFF8A94A3),
        outline = Color(0xFFD9DEE6),
        outlineVariant = Color(0xFFE3E7EC),
        error = Color(0xFFC96A6A),
        onError = Color.White,
    )

@Composable
fun CharactymTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColorScheme, typography = Typography, content = content)
}
