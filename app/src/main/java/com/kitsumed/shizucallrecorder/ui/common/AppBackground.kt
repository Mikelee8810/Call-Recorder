/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

/**
 * Full-screen layered background used behind every top-level destination.
 *
 * Gives the app depth without resorting to a flat single-color surface or a generic
 * purple-to-white gradient: a soft radial "glow" anchored to the top-left in the app's own
 * primary hue, plus a very faint scattered grain/dot texture for tactile depth. Both layers sit
 * behind normal content and never affect contrast enough to hurt legibility.
 *
 * @param modifier Optional modifier applied to the root [Box].
 * @param content  The screen content drawn above the background.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    val glowColor = MaterialTheme.colorScheme.primary
    val grainColor = MaterialTheme.colorScheme.onBackground

    // Stable per-composition seed so the grain doesn't "shimmer" on every recomposition.
    val grainSeed = remember { Random.nextInt() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRadialGlow(glowColor)
            drawGrain(grainColor, grainSeed)
        }
        content()
    }
}

/** Soft, large, low-opacity radial glow anchored near the top-left corner. */
private fun DrawScope.drawRadialGlow(color: androidx.compose.ui.graphics.Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0f)),
            center = Offset(size.width * 0.08f, size.height * 0.02f),
            radius = size.maxDimension * 0.75f
        ),
        radius = size.maxDimension * 0.75f,
        center = Offset(size.width * 0.08f, size.height * 0.02f)
    )
}

/** Sparse, very low-opacity scattered dots that read as subtle grain/texture, not noise. */
private fun DrawScope.drawGrain(color: androidx.compose.ui.graphics.Color, seed: Int) {
    val random = Random(seed)
    val dotCount = 90
    repeat(dotCount) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val radius = 0.6f + random.nextFloat() * 1.1f
        drawCircle(
            color = color.copy(alpha = 0.035f),
            radius = radius,
            center = Offset(x, y)
        )
    }
}
