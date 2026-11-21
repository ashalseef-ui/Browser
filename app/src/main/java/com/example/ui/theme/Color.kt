package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Cyber-Obsidian Dark Palette
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

// Daylight Ice Light Palette
val DaylightBg = Color(0xFFF3F6FA)
val DaylightSurface = Color(0xFFFFFFFF)
val DaylightSurfaceHigh = Color(0xFFE9EFF6)
val DaylightSurfaceHighest = Color(0xFFDCE5F0)
val DaylightBorder = Color(0xFFCBD5E1)
val DaylightCyan = Color(0xFF007C91)
val DaylightAmber = Color(0xFFD97706)
val DaylightEmerald = Color(0xFF059669)
val DaylightTextPrimary = Color(0xFF0F172A)
val DaylightTextSecondary = Color(0xFF475569)

@Immutable
data class VoltExtendedColors(
  val cyanAccent: Color,
  val amberAccent: Color,
  val emeraldShield: Color,
  val incognitoAccent: Color,
  val cardSurface: Color,
  val cardBorder: Color,
  val omniboxSurface: Color,
)

val DarkVoltExtendedColors =
  VoltExtendedColors(
    cyanAccent = VoltCyan,
    amberAccent = VoltAmber,
    emeraldShield = VoltEmerald,
    incognitoAccent = VoltViolet,
    cardSurface = ObsidianSurfaceHigh,
    cardBorder = ObsidianBorder,
    omniboxSurface = ObsidianSurfaceHighest,
  )

val LightVoltExtendedColors =
  VoltExtendedColors(
    cyanAccent = DaylightCyan,
    amberAccent = DaylightAmber,
    emeraldShield = DaylightEmerald,
    incognitoAccent = Color(0xFF6D28D9),
    cardSurface = DaylightSurface,
    cardBorder = DaylightBorder,
    omniboxSurface = DaylightSurfaceHigh,
  )

val LocalVoltColors = staticCompositionLocalOf { DarkVoltExtendedColors }
