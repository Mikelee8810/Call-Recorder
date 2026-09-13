/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// "Ember" palette. Light theme (a warm cream, never sterile white) is the hero
// look; dark theme is a fully-considered warm-charcoal secondary, not an
// afterthought inversion. One committed accent hue (burnt amber/ember) is used
// consistently for primary actions and active/playing states. True red is
// reserved exclusively for the literal "recording live" indicator - never used
// decoratively elsewhere.
// ─────────────────────────────────────────────────────────────────────────────

// --- Accent ("Ember") — the app's one confident, sharp accent ---
val EmberDeep = Color(0xFFA34E0A)     // Primary on the light/cream hero theme (dark enough to read on cream)
val EmberBright = Color(0xFFFF9E45)   // Primary/tertiary on the dark theme, and shared "active" highlight
val EmberContainerLight = Color(0xFFF6DCBB)
val EmberContainerDark = Color(0xFF5C3210)
val OnEmberDeep = Color(0xFFFFF6EC)   // Cream text on the deep ember button (light theme)
val OnEmberBright = Color(0xFF2B1400) // Near-black brown text on the bright ember button (dark theme)

// --- Light theme surfaces: warm cream, not stark white ---
val CreamGround = Color(0xFFF5EFE3)
val CreamSurface = Color(0xFFFCF8F0)
val CreamSurfaceHigh = Color(0xFFEDE3CE)
val CreamOutline = Color(0xFFCFC0A6)

// --- Dark theme surfaces: warm charcoal, not a cold blue-gray ---
val CharcoalGround = Color(0xFF16130F)
val CharcoalSurface = Color(0xFF1F1B15)
val CharcoalSurfaceHigh = Color(0xFF2A241C)
val CharcoalOutline = Color(0xFF4C4234)

// --- Text ---
val TextOnCream = Color(0xFF201A10)
val TextOnCreamMuted = Color(0xFF6E6350)
val TextOnCharcoal = Color(0xFFF1E9DA)
val TextOnCharcoalMuted = Color(0xFFB7AB96)

// --- Status: true red, reserved only for the live-recording indicator / destructive actions ---
val RecordingRed = Color(0xFFD8402E)
val OnRecordingRed = Color(0xFFFFF5F2)
val RecordingRedContainerLight = Color(0xFFF6D6CF)
val RecordingRedContainerDark = Color(0xFF4A1F17)
