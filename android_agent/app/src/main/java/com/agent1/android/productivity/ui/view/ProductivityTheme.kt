package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 生产力对话配色对齐墨览（molan）默认纸本与墨夜：
 * 暖纸底、墨色正文、琥珀强调色。见 fengshihao/molan `packages/molan-core/src/molan.css`。
 */
private val Ink = Color(0xFF1C1914)
private val InkSoft = Color(0xFF4A453C)
private val InkMuted = Color(0xFF8A8276)
private val Paper = Color(0xFFF4EFE6)
private val PaperDeep = Color(0xFFEBE4D6)
private val PaperLift = Color(0xFFFAF7F1)
private val Accent = Color(0xFFD4773B)
private val AccentDeep = Color(0xFFB85F28)
private val OnAccent = Color(0xFFFFF8F1)
private val AccentWash = Color(0xFFF6E6D8)
private val Moss = Color(0xFF6B8F71)
private val Danger = Color(0xFFC45C4A)
private val Hairline = Color(0x141C1914)
private val HairlineStrong = Color(0x1F1C1914)

private val NightInk = Color(0xFFE8E2D6)
private val NightInkSoft = Color(0xFFC4BDB0)
private val NightMuted = Color(0xFF8D867A)
private val NightPaper = Color(0xFF161410)
private val NightPaperDeep = Color(0xFF1E1A15)
private val NightPaperLift = Color(0xFF221E18)
private val NightAccent = Color(0xFFE0A054)
private val NightOnAccent = Color(0xFF1A140C)
private val NightDanger = Color(0xFFE07A6A)

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
        assistantBackground = cs.surfaceContainerLowest,
        assistantBorder = cs.outlineVariant,
        systemBackground = cs.surfaceContainerLow,
        systemBorder = cs.outlineVariant,
    )
}

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentWash,
    onPrimaryContainer = AccentDeep,
    secondary = Moss,
    onSecondary = OnAccent,
    secondaryContainer = PaperDeep,
    onSecondaryContainer = Ink,
    tertiary = Moss,
    onTertiary = OnAccent,
    tertiaryContainer = PaperDeep,
    onTertiaryContainer = InkSoft,
    background = Paper,
    onBackground = Ink,
    surface = PaperLift,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = PaperDeep,
    surfaceContainer = Paper,
    surfaceContainerHigh = PaperLift,
    outline = HairlineStrong,
    outlineVariant = Hairline,
    error = Danger,
    onError = OnAccent,
    errorContainer = Color(0xFFF8E4DF),
    onErrorContainer = Color(0xFF6B2E24),
)

private val DarkColors = darkColorScheme(
    primary = NightAccent,
    onPrimary = NightOnAccent,
    primaryContainer = Color(0xFF3A2A18),
    onPrimaryContainer = Color(0xFFF0C48A),
    secondary = Color(0xFF7A9E80),
    onSecondary = NightOnAccent,
    secondaryContainer = NightPaperDeep,
    onSecondaryContainer = NightInk,
    tertiary = Color(0xFF7A9E80),
    onTertiary = NightOnAccent,
    tertiaryContainer = NightPaperDeep,
    onTertiaryContainer = NightInkSoft,
    background = NightPaper,
    onBackground = NightInk,
    surface = NightPaperDeep,
    onSurface = NightInk,
    surfaceVariant = NightPaperLift,
    onSurfaceVariant = NightMuted,
    surfaceContainerLowest = NightPaperLift,
    surfaceContainerLow = NightPaperDeep,
    surfaceContainer = NightPaper,
    surfaceContainerHigh = NightPaperLift,
    outline = Color(0x29E8E2D6),
    outlineVariant = Color(0x1AE8E2D6),
    error = NightDanger,
    onError = NightOnAccent,
    errorContainer = Color(0xFF3A221C),
    onErrorContainer = Color(0xFFF0C4BC),
)

private val MolanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

private val ProductivityTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
    ),
)

@Composable
fun ProductivityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = ProductivityTypography,
        shapes = MolanShapes,
        content = content,
    )
}
