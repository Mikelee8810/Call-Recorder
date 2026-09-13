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

/**
 * Light theme — the hero/default look: a warm cream ground (never sterile white) with a single
 * confident burnt-amber ("ember") accent. Individual chrome pieces (top bars, the now-playing
 * card, primary buttons) deliberately borrow the warm-charcoal palette for contrast and punch,
 * the way Linear/Notion mix a light canvas with confidently dark chrome — see
 * `ui/common/AppBackground.kt` and the "now playing" styling in `RecordingsScreen.kt`.
 */
private val LightColorScheme = lightColorScheme(
    primary = EmberDeep,
    onPrimary = OnEmberDeep,
    primaryContainer = EmberContainerLight,
    onPrimaryContainer = EmberDeep,

    secondary = TextOnCreamMuted,
    onSecondary = CreamSurface,
    secondaryContainer = CreamSurfaceHigh,
    onSecondaryContainer = TextOnCream,

    tertiary = EmberDeep,
    onTertiary = OnEmberDeep,
    tertiaryContainer = EmberContainerLight,
    onTertiaryContainer = EmberDeep,

    background = CreamGround,
    onBackground = TextOnCream,

    surface = CreamSurface,
    onSurface = TextOnCream,
    surfaceVariant = CreamSurfaceHigh,
    onSurfaceVariant = TextOnCreamMuted,
    surfaceContainer = CreamSurface,
    surfaceContainerLow = CreamGround,
    surfaceContainerHigh = CreamSurfaceHigh,
    surfaceContainerHighest = CreamSurfaceHigh,

    outline = CreamOutline,
    outlineVariant = CreamOutline,

    error = RecordingRed,
    onError = OnRecordingRed,
    errorContainer = RecordingRedContainerLight,
    onErrorContainer = RecordingRed
)

/**
 * Dark theme — a fully-considered warm-charcoal secondary (not a retrofit): same ember accent
 * and same product identity, just inverted for users who prefer or whose system requests it.
 */
private val DarkColorScheme = darkColorScheme(
    primary = EmberBright,
    onPrimary = OnEmberBright,
    primaryContainer = EmberContainerDark,
    onPrimaryContainer = EmberBright,

    secondary = TextOnCharcoalMuted,
    onSecondary = CharcoalGround,
    secondaryContainer = CharcoalSurfaceHigh,
    onSecondaryContainer = TextOnCharcoal,

    tertiary = EmberBright,
    onTertiary = OnEmberBright,
    tertiaryContainer = EmberContainerDark,
    onTertiaryContainer = EmberBright,

    background = CharcoalGround,
    onBackground = TextOnCharcoal,

    surface = CharcoalSurface,
    onSurface = TextOnCharcoal,
    surfaceVariant = CharcoalSurfaceHigh,
    onSurfaceVariant = TextOnCharcoalMuted,
    surfaceContainer = CharcoalSurface,
    surfaceContainerLow = CharcoalGround,
    surfaceContainerHigh = CharcoalSurfaceHigh,
    surfaceContainerHighest = CharcoalSurfaceHigh,

    outline = CharcoalOutline,
    outlineVariant = CharcoalOutline,

    error = RecordingRed,
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
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
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
