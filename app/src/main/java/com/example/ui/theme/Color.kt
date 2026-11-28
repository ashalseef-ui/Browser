package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.AppThemeMode
import com.example.data.SearchEngine

// Graphite Ember (Dark) Palette — Warm volcanic carbon & electric vermilion coral
val EmberCoral = Color(0xFFFF6347)
val EmberCoralSoft = Color(0xFFFF856D)
val EmberOnCoral = Color(0xFF1F0602)
val EmberMint = Color(0xFF34D399)
val EmberAmber = Color(0xFFFBBF24)
val EmberCobalt = Color(0xFF60A5FA)
val EmberIris = Color(0xFFA78BFA)
val EmberCrimson = Color(0xFFF87171)

val GraphiteCanvas = Color(0xFF0D0D10)
val GraphiteSurface = Color(0xFF151519)
val GraphiteSurfaceHigh = Color(0xFF1D1D23)
val GraphiteSurfaceHighest = Color(0xFF26262F)
val GraphiteBorder = Color(0xFF2E2E39)
val GraphiteTextPrimary = Color(0xFFF5F5F7)
val GraphiteTextSecondary = Color(0xFF9C9CA8)

// Alabaster Paper (Light) Palette — Warm ceramic stone & deep vermilion
val AlabasterCanvas = Color(0xFFF6F4EF)
val AlabasterSurface = Color(0xFFFFFFFF)
val AlabasterSurfaceHigh = Color(0xFFEDE9E0)
val AlabasterSurfaceHighest = Color(0xFFE3DDD1)
val AlabasterBorder = Color(0xFFDAD3C5)
val AlabasterTextPrimary = Color(0xFF161619)
val AlabasterTextSecondary = Color(0xFF5D5C66)

val AlabasterCoral = Color(0xFFDC3D1E)
val AlabasterMint = Color(0xFF059669)
val AlabasterAmber = Color(0xFFD97706)
val AlabasterCobalt = Color(0xFF2563EB)
val AlabasterIris = Color(0xFF7C3AED)
val AlabasterCrimson = Color(0xFFDC2626)

@Immutable
data class VoltExtendedColors(
  val themeMode: AppThemeMode,
  val isLightTheme: Boolean,
  val brandAccent: Color,
  val onBrandAccent: Color,
  val infoAccent: Color,
  val amberAccent: Color,
  val emeraldShield: Color,
  val incognitoAccent: Color,
  val cardSurface: Color,
  val cardElevatedSurface: Color,
  val cardBorder: Color,
  val omniboxSurface: Color,
  val dockSurface: Color,
  val pillSurface: Color,
  val pillBorder: Color,
)

val GraphiteExtendedColors =
  VoltExtendedColors(
    themeMode = AppThemeMode.CYBER_OBSIDIAN,
    isLightTheme = false,
    brandAccent = EmberCoral,
    onBrandAccent = EmberOnCoral,
    infoAccent = EmberCobalt,
    amberAccent = EmberAmber,
    emeraldShield = EmberMint,
    incognitoAccent = EmberIris,
    cardSurface = GraphiteSurfaceHigh,
    cardElevatedSurface = GraphiteSurfaceHighest,
    cardBorder = GraphiteBorder,
    omniboxSurface = GraphiteSurfaceHighest,
    dockSurface = Color(0xFF131318),
    pillSurface = Color(0xCC1B1B22),
    pillBorder = Color(0x22FFFFFF),
  )

val AlabasterExtendedColors =
  VoltExtendedColors(
    themeMode = AppThemeMode.ZEN_LIGHT,
    isLightTheme = true,
    brandAccent = AlabasterCoral,
    onBrandAccent = Color.White,
    infoAccent = AlabasterCobalt,
    amberAccent = AlabasterAmber,
    emeraldShield = AlabasterMint,
    incognitoAccent = AlabasterIris,
    cardSurface = AlabasterSurface,
    cardElevatedSurface = AlabasterSurfaceHigh,
    cardBorder = AlabasterBorder,
    omniboxSurface = AlabasterSurfaceHigh,
    dockSurface = AlabasterSurface,
    pillSurface = Color(0xFFF0EDE5),
    pillBorder = AlabasterBorder,
  )

val LocalVoltColors = staticCompositionLocalOf { GraphiteExtendedColors }

fun SearchEngine.badgeAccentColor(isLightTheme: Boolean): Color =
  when {
    this == SearchEngine.WIKIPEDIA && isLightTheme -> Color(0xFF475569)
    this == SearchEngine.KAGI && isLightTheme -> Color(0xFFD97706)
    this == SearchEngine.MARGINALIA && isLightTheme -> Color(0xFFD97706)
    else -> Color(accentColorHex)
  }
