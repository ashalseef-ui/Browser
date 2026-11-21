package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val VoltDarkColorScheme =
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

private val VoltLightColorScheme =
  lightColorScheme(
    primary = DaylightCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F4FA),
    onPrimaryContainer = Color(0xFF003640),
    secondary = DaylightAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = DaylightEmerald,
    onTertiary = Color.White,
    background = DaylightBg,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceHigh,
    onSurfaceVariant = DaylightTextSecondary,
    surfaceContainerLowest = DaylightSurface,
    surfaceContainerLow = DaylightBg,
    surfaceContainer = DaylightSurfaceHigh,
    surfaceContainerHigh = DaylightSurfaceHighest,
    outline = DaylightBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
  )

@Composable
fun VoltBrowserTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> VoltDarkColorScheme
      else -> VoltLightColorScheme
    }

  val extendedColors = if (darkTheme) DarkVoltExtendedColors else LightVoltExtendedColors

  CompositionLocalProvider(LocalVoltColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = VoltTypography,
      content = content,
    )
  }
}
