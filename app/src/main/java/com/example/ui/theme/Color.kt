package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.AppThemeMode
import com.example.data.SearchEngine

// Cyber Obsidian Dark Palette
val VoltCyan = Color(0xFF00E5FF)
val VoltCyanDim = Color(0xFF00B8D4)
val VoltAmber = Color(0xFFFFB300)
val VoltEmerald = Color(0xFF00E676)
val VoltCrimson = Color(0xFFFF5252)
val VoltViolet = Color(0xFFB388FF)

val ObsidianBg = Color(0xFF090D14)
val ObsidianSurface = Color(0xFF111824)
val ObsidianSurfaceHigh = Color(0xFF1A2333)
val ObsidianSurfaceHighest = Color(0xFF232F44)
val ObsidianBorder = Color(0xFF2B3952)
val ObsidianTextPrimary = Color(0xFFF1F5F9)
val ObsidianTextSecondary = Color(0xFF94A3B8)

// Zen Light Paper Palette
val ZenPaperBg = Color(0xFFF6F8FB)
val ZenPaperSurface = Color(0xFFFFFFFF)
val ZenPaperSurfaceHigh = Color(0xFFEFF3F8)
val ZenPaperSurfaceHighest = Color(0xFFE2E8F0)
val ZenBorder = Color(0xFFD5DEE8)
val ZenPrimary = Color(0xFF0F172A)
val ZenSecondary = Color(0xFF475569)

@Immutable
data class VoltExtendedColors(
  val themeMode: AppThemeMode,
  val isLightTheme: Boolean,
  val cyanAccent: Color,
  val amberAccent: Color,
  val emeraldShield: Color,
  val incognitoAccent: Color,
  val cardSurface: Color,
  val cardBorder: Color,
  val omniboxSurface: Color,
  val pillSurface: Color,
  val pillBorder: Color,
)

val DarkVoltExtendedColors =
  VoltExtendedColors(
    themeMode = AppThemeMode.CYBER_OBSIDIAN,
    isLightTheme = false,
    cyanAccent = VoltCyan,
    amberAccent = VoltAmber,
    emeraldShield = VoltEmerald,
    incognitoAccent = VoltViolet,
    cardSurface = ObsidianSurfaceHigh,
    cardBorder = ObsidianBorder,
    omniboxSurface = ObsidianSurfaceHighest,
    pillSurface = Color(0xAA111824),
    pillBorder = Color(0x22FFFFFF),
  )

val ZenLightExtendedColors =
  VoltExtendedColors(
    themeMode = AppThemeMode.ZEN_LIGHT,
    isLightTheme = true,
    cyanAccent = Color(0xFF0284C7),
    amberAccent = Color(0xFFD97706),
    emeraldShield = Color(0xFF059669),
    incognitoAccent = Color(0xFF7C3AED),
    cardSurface = ZenPaperSurface,
    cardBorder = ZenBorder,
    omniboxSurface = ZenPaperSurfaceHigh,
    pillSurface = Color(0xFFF1F5F9),
    pillBorder = ZenBorder,
  )

val LocalVoltColors = staticCompositionLocalOf { DarkVoltExtendedColors }

fun SearchEngine.badgeAccentColor(isLightTheme: Boolean): Color =
  when {
    this == SearchEngine.WIKIPEDIA && isLightTheme -> Color(0xFF475569)
    this == SearchEngine.KAGI && isLightTheme -> Color(0xFFD97706)
    this == SearchEngine.MARGINALIA && isLightTheme -> Color(0xFFD97706)
    else -> Color(accentColorHex)
  }
