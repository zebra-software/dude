package com.zebrasoftware.dude.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Dude's own colour schemes, generated with the Material colour utilities from the
 * seed colour #2B5FA6, a parking-sign blue.
 *  - Accent colours: content scheme (2021 spec), which keeps the seed as primaryContainer.
 *  - Backgrounds and surfaces: neutral palettes in the seed's hue with a bit more
 *    chroma than standard (14 / 20 instead of about 4 / 8), so they read as a light
 *    blue-grey instead of plain white and black.
 */

internal val LightColors = lightColorScheme(
    primary = Color(0xFF01478D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2B5FA6),
    onPrimaryContainer = Color(0xFFC8DAFF),
    secondary = Color(0xFF4F5F7C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCADAFD),
    onSecondaryContainer = Color(0xFF4F5F7D),
    tertiary = Color(0xFF683076),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF824890),
    onTertiaryContainer = Color(0xFFF9CAFF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    inversePrimary = Color(0xFFA9C7FF),
    scrim = Color(0xFF000000),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF141C29),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF141C29),
    surfaceVariant = Color(0xFFD6E3FF),
    onSurfaceVariant = Color(0xFF3A475E),
    outline = Color(0xFF6B7790),
    outlineVariant = Color(0xFFBAC7E3),
    inverseSurface = Color(0xFF29313F),
    inverseOnSurface = Color(0xFFECF0FF),
    surfaceDim = Color(0xFFD3DAED),
    surfaceBright = Color(0xFFF9F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3FF),
    surfaceContainer = Color(0xFFE8EEFF),
    surfaceContainerHigh = Color(0xFFE1E8FB),
    surfaceContainerHighest = Color(0xFFDBE2F5),
    surfaceTint = Color(0xFF01478D)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF2B5FA6),
    onPrimaryContainer = Color(0xFFC8DAFF),
    secondary = Color(0xFFB6C7E9),
    onSecondary = Color(0xFF20314B),
    secondaryContainer = Color(0xFF3C4C68),
    onSecondaryContainer = Color(0xFFACBCDE),
    tertiary = Color(0xFFF2AFFF),
    onTertiary = Color(0xFF4E165D),
    tertiaryContainer = Color(0xFF824890),
    onTertiaryContainer = Color(0xFFF9CAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inversePrimary = Color(0xFF2A5EA5),
    scrim = Color(0xFF000000),
    background = Color(0xFF0C1320),
    onBackground = Color(0xFFDBE2F5),
    surface = Color(0xFF0C1320),
    onSurface = Color(0xFFDBE2F5),
    surfaceVariant = Color(0xFF3A475E),
    onSurfaceVariant = Color(0xFFBAC7E3),
    outline = Color(0xFF8491AB),
    outlineVariant = Color(0xFF3A475E),
    inverseSurface = Color(0xFFDBE2F5),
    inverseOnSurface = Color(0xFF29313F),
    surfaceDim = Color(0xFF0C1320),
    surfaceBright = Color(0xFF323948),
    surfaceContainerLowest = Color(0xFF070E1B),
    surfaceContainerLow = Color(0xFF141C29),
    surfaceContainer = Color(0xFF18202D),
    surfaceContainerHigh = Color(0xFF232A38),
    surfaceContainerHighest = Color(0xFF2D3543),
    surfaceTint = Color(0xFFA9C7FF)
)
