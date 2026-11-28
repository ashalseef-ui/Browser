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

private val GraphiteEmberColorScheme =
  darkColorScheme(
    primary = EmberCoral,
    onPrimary = EmberOnCoral,
    primaryContainer = Color(0xFF4F1B12),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = EmberAmber,
    onSecondary = Color(0xFF281900),
    secondaryContainer = Color(0xFF473000),
    onSecondaryContainer = Color(0xFFFFE09E),
    tertiary = EmberMint,
    onTertiary = Color(0xFF002918),
    background = GraphiteCanvas,
    onBackground = GraphiteTextPrimary,
    surface = GraphiteSurface,
    onSurface = GraphiteTextPrimary,
    surfaceVariant = GraphiteSurfaceHigh,
    onSurfaceVariant = GraphiteTextSecondary,
    surfaceContainerLowest = GraphiteCanvas,
    surfaceContainerLow = GraphiteSurface,
    surfaceContainer = GraphiteSurfaceHigh,
    surfaceContainerHigh = GraphiteSurfaceHighest,
    outline = GraphiteBorder,
    outlineVariant = Color(0xFF23232C),
    error = EmberCrimson,
    onError = Color(0xFF2C0606),
  )

private val AlabasterPaperColorScheme =
  lightColorScheme(
    primary = AlabasterCoral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5DF),
    onPrimaryContainer = Color(0xFF7D1B07),
    secondary = AlabasterAmber,
    onSecondary = Color.White,
    tertiary = AlabasterMint,
    onTertiary = Color.White,
    background = AlabasterCanvas,
    onBackground = AlabasterTextPrimary,
    surface = AlabasterSurface,
    onSurface = AlabasterTextPrimary,
    surfaceVariant = AlabasterSurfaceHigh,
    onSurfaceVariant = AlabasterTextSecondary,
    surfaceContainerLowest = AlabasterSurface,
    surfaceContainerLow = AlabasterCanvas,
    surfaceContainer = AlabasterSurfaceHigh,
    surfaceContainerHigh = AlabasterSurfaceHighest,
    outline = AlabasterBorder,
    outlineVariant = Color(0xFFE5E0D5),
    error = AlabasterCrimson,
    onError = Color.White,
  )

private val ArchitecturalVoltShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
  )

@Composable
fun VoltBrowserTheme(
  themeMode: AppThemeMode = AppThemeMode.CYBER_OBSIDIAN,
  content: @Composable () -> Unit,
) {
  val (colorScheme, extendedColors) =
    when (themeMode) {
      AppThemeMode.CYBER_OBSIDIAN -> GraphiteEmberColorScheme to GraphiteExtendedColors
      AppThemeMode.ZEN_LIGHT -> AlabasterPaperColorScheme to AlabasterExtendedColors
    }

  CompositionLocalProvider(LocalVoltColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = VoltTypography,
      shapes = ArchitecturalVoltShapes,
      content = content,
    )
  }
}
