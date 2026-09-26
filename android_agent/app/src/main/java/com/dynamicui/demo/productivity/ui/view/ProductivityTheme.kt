package com.dynamicui.demo.productivity.ui.view

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 生产力对话 UI 配色：浅灰底、蓝色主色，深浅色跟随 Material 语义色。 */
private val AgentText = Color(0xFF1F2328)
private val AgentMuted = Color(0xFF8B949E)
private val AgentBg = Color(0xFFF5F6F8)
private val AgentPanel = Color(0xFFFFFFFF)
private val AgentBorder = Color(0xFFE5E5E7)
private val AgentAccent = Color(0xFF2563EB)
private val AgentSystemFill = Color(0xFFF0FDF4)
private val AgentCodeBg = Color(0xFFF0F0F2)

/** 聊天气泡色：随 MaterialTheme / 深浅色切换，避免硬编码白底。 */
data class ChatBubbleColors(
    val userBackground: Color,
    val userContent: Color,
    val assistantBackground: Color,
    val assistantBorder: Color,
    val systemBackground: Color,
    val systemBorder: Color,
)

@Composable
fun chatBubbleColors(): ChatBubbleColors {
    val cs = MaterialTheme.colorScheme
    return ChatBubbleColors(
        userBackground = cs.primary,
        userContent = cs.onPrimary,
        assistantBackground = cs.surfaceContainerHigh,
        assistantBorder = cs.outline,
        systemBackground = cs.tertiaryContainer,
        systemBorder = cs.outlineVariant,
    )
}

private val LightColors = lightColorScheme(
    primary = AgentAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EFFC),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    secondaryContainer = AgentCodeBg,
    onSecondaryContainer = AgentText,
    tertiary = Color(0xFF0E7490),
    onTertiary = Color.White,
    tertiaryContainer = AgentSystemFill,
    onTertiaryContainer = Color(0xFF14532D),
    background = AgentBg,
    onBackground = AgentText,
    surface = AgentPanel,
    onSurface = AgentText,
    surfaceVariant = AgentCodeBg,
    onSurfaceVariant = AgentMuted,
    surfaceContainerLowest = AgentPanel,
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainerHigh = AgentPanel,
    surfaceContainer = AgentBg,
    outline = AgentBorder,
    outlineVariant = Color(0xFFEDEFF2),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF1E3A8A),
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF94A3B8),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceContainerLowest = Color(0xFF161B22),
    surfaceContainerLow = Color(0xFF1C2128),
    surfaceContainerHigh = Color(0xFF21262D),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF042F2E),
    tertiaryContainer = Color(0xFF1A2E22),
    onTertiaryContainer = Color(0xFF86EFAC),
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF3D444D),
    error = Color(0xFFF87171),
)

private val ProductivityTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
)

@Composable
fun ProductivityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = ProductivityTypography,
        content = content,
    )
}
