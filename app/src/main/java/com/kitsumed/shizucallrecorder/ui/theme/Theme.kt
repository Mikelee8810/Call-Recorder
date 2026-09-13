/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = IOSBlue,
    onPrimary = IOSCell,
    primaryContainer = IOSBlueContainerLight,
    onPrimaryContainer = IOSBlue,

    secondary = IOSSecondaryLabel,
    onSecondary = IOSCell,
    secondaryContainer = IOSSecondarySurface,
    onSecondaryContainer = IOSLabel,

    tertiary = IOSBlue,
    onTertiary = IOSCell,
    tertiaryContainer = IOSBlueContainerLight,
    onTertiaryContainer = IOSBlue,

    background = IOSGroupedBackground,
    onBackground = IOSLabel,

    surface = IOSCell,
    onSurface = IOSLabel,
    surfaceVariant = IOSSecondarySurface,
    onSurfaceVariant = IOSSecondaryLabel,
    surfaceContainer = IOSCell,
    surfaceContainerLow = IOSGroupedBackground,
    surfaceContainerHigh = IOSSecondarySurface,
    surfaceContainerHighest = IOSTertiarySurface,

    outline = IOSSeparator,
    outlineVariant = IOSSeparator,

    error = RecordingRed,
    onError = OnRecordingRed,
    errorContainer = RecordingRedContainerLight,
    onErrorContainer = RecordingRed
)

private val DarkColorScheme = darkColorScheme(
    primary = IOSBlueDark,
    onPrimary = IOSDarkLabel,
    primaryContainer = IOSBlueContainerDark,
    onPrimaryContainer = IOSBlueDark,

    secondary = IOSDarkSecondaryLabel,
    onSecondary = IOSDarkLabel,
    secondaryContainer = IOSDarkSecondarySurface,
    onSecondaryContainer = IOSDarkLabel,

    tertiary = IOSBlueDark,
    onTertiary = IOSDarkLabel,
    tertiaryContainer = IOSBlueContainerDark,
    onTertiaryContainer = IOSBlueDark,

    background = IOSDarkBackground,
    onBackground = IOSDarkLabel,

    surface = IOSDarkCell,
    onSurface = IOSDarkLabel,
    surfaceVariant = IOSDarkSecondarySurface,
    onSurfaceVariant = IOSDarkSecondaryLabel,
    surfaceContainer = IOSDarkCell,
    surfaceContainerLow = IOSDarkBackground,
    surfaceContainerHigh = IOSDarkSecondarySurface,
    surfaceContainerHighest = IOSDarkTertiarySurface,

    outline = IOSDarkSeparator,
    outlineVariant = IOSDarkSeparator,

    error = RecordingRedDark,
    onError = OnRecordingRed,
    errorContainer = RecordingRedContainerDark,
    onErrorContainer = OnRecordingRed
)

/**
 * Custom shape scheme: generous, consistently rounded corners throughout (cards, sheets,
 * buttons) for the soft, comfortable, iOS-adjacent feel called for in the design direction -
 * favoring restraint over a novelty shape (no cut corners, no sharp mixed geometry).
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun ShizuCallRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Dynamic color scheme
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // System Bar icon theme sync
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
