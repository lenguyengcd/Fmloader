package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val FmDarkColorScheme =
  darkColorScheme(
    primary = MillenniumGold,
    onPrimary = OnMillenniumGold,
    primaryContainer = MillenniumGoldContainer,
    onPrimaryContainer = MillenniumGold,
    secondary = CyberCyan,
    onSecondary = OnCyberCyan,
    secondaryContainer = CyberCyanContainer,
    onSecondaryContainer = CyberCyan,
    tertiary = CrimsonMod,
    onTertiary = TextPrimary,
    tertiaryContainer = CrimsonModContainer,
    onTertiaryContainer = CrimsonMod,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = OutlineSubtle,
    outlineVariant = OutlineSubtle,
  )

val FmShapes =
  Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
  )

@Composable
fun FmModLoaderTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = FmDarkColorScheme,
    typography = AppTypography,
    shapes = FmShapes,
    content = content,
  )
}
