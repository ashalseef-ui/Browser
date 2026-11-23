package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.AppThemeMode

private val CyberObsidianColorScheme =
  darkColorScheme(
    primary = VoltCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = Color(0xFF004F5C),
    onPrimaryContainer = Color(0xFFB3F4FF),
    secondary = VoltAmber,
    onSecondary = Color(0xFF281800),
    secondaryContainer = Color(0xFF4A3000),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = VoltEmerald,
    onTertiary = Color(0xFF002914),
    background = ObsidianBg,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = ObsidianTextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1E293B),
    error = VoltCrimson,
  )

private val ZenLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = ZenPaperBg,
    onBackground = ZenPrimary,
    surface = ZenPaperSurface,
    onSurface = ZenPrimary,
    surfaceVariant = ZenPaperSurfaceHigh,
    onSurfaceVariant = ZenSecondary,
    surfaceContainerLowest = ZenPaperSurface,
    surfaceContainerLow = ZenPaperBg,
    surfaceContainer = ZenPaperSurfaceHigh,
    surfaceContainerHigh = ZenPaperSurfaceHighest,
    outline = ZenBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
  )

private val RoundedVoltShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
  )

@Composable
fun VoltBrowserTheme(
  themeMode: AppThemeMode = AppThemeMode.CYBER_OBSIDIAN,
  content: @Composable () -> Unit,
) {
  val (colorScheme, extendedColors) =
    when (themeMode) {
      AppThemeMode.CYBER_OBSIDIAN -> CyberObsidianColorScheme to DarkVoltExtendedColors
      AppThemeMode.ZEN_LIGHT -> ZenLightColorScheme to ZenLightExtendedColors
    }

  CompositionLocalProvider(LocalVoltColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = VoltTypography,
      shapes = RoundedVoltShapes,
      content = content,
    )
  }
}
