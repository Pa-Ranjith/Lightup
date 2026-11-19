package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ZestLightColorScheme = lightColorScheme(
    primary = ZestPrimary,
    onPrimary = ZestOnPrimary,
    primaryContainer = ZestPrimarySoft,
    onPrimaryContainer = ZestInk,
    secondary = ZestSecondaryDeep,
    onSecondary = ZestOnSecondary,
    secondaryContainer = ZestSecondarySoft,
    onSecondaryContainer = ZestInk,
    tertiary = ZestTertiary,
    onTertiary = ZestOnTertiary,
    tertiaryContainer = ZestTertiarySoft,
    onTertiaryContainer = ZestInk,
    background = ZestBackground,
    onBackground = ZestInk,
    surface = ZestSurface,
    onSurface = ZestInk,
    surfaceVariant = ZestSurface2,
    onSurfaceVariant = ZestInk2,
    surfaceContainerLowest = ZestSurface,
    surfaceContainerLow = ZestBackground,
    surfaceContainer = ZestSurface2,
    surfaceContainerHigh = ZestSurface2,
    surfaceContainerHighest = ZestSurfaceDim,
    outline = ZestOutline,
    outlineVariant = ZestLine,
    inverseSurface = ZestInverse,
    inverseOnSurface = ZestInverseSubtext,
    inversePrimary = ZestPrimaryFixed,
    error = ZestDanger,
    onError = ZestOnPrimary,
    errorContainer = ZestErrorContainer,
    onErrorContainer = ZestOnErrorContainer
)

private val ZestDarkColorScheme = darkColorScheme(
    primary = ZestDarkPrimary,
    onPrimary = ZestDarkOnPrimary,
    primaryContainer = ZestInverse,
    onPrimaryContainer = ZestInverseSubtext,
    secondary = ZestSecondary,
    onSecondary = ZestDarkOnPrimary,
    secondaryContainer = ZestSecondaryDeep,
    onSecondaryContainer = ZestSecondarySoft,
    tertiary = ZestTertiary,
    onTertiary = ZestOnTertiary,
    background = ZestDarkBackground,
    onBackground = ZestDarkInk,
    surface = ZestDarkSurface,
    onSurface = ZestDarkInk,
    surfaceVariant = ZestDarkSurface2,
    onSurfaceVariant = ZestDarkInk2,
    surfaceContainerLowest = ZestDarkSurface,
    surfaceContainerLow = ZestDarkBackground,
    surfaceContainer = ZestDarkSurface2,
    surfaceContainerHigh = ZestDarkSurface2,
    surfaceContainerHighest = ZestDarkLine,
    outline = ZestDarkInk2,
    outlineVariant = ZestDarkLine,
    inverseSurface = ZestInverse,
    inverseOnSurface = ZestInverseSubtext,
    error = ZestDanger,
    onError = ZestOnPrimary
)

@Composable
fun LightUpTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ZestDarkColorScheme else ZestLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LightUpTypography,
        shapes = LightUpShapes,
        content = content
    )
}
