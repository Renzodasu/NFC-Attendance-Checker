package com.nezzar.nfcattendance.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's own palettes. One place to change the look.
 *
 * Dark (measured, WCAG relative luminance):
 *   BrandOutline       #6B6B6B  3.33:1 on #181818 cards, 3.52:1 on #121212
 *   BrandHairline      #2E2E2E  1.31:1 - decorative dividers only
 *   BrandTextSecondary #B0B0B0  8.19:1
 *   BrandPrimary       #22C55E  7.79:1
 *
 * Light (measured on #FFFFFF cards / #FAFAFA page):
 *   LightOutline       #6B6B6B  5.33:1
 *   LightHairline      #E2E2E2  1.20:1 - decorative dividers only
 *   LightTextSecondary #575757  7.23:1
 *   LightPrimary       #15803D  5.01:1 under white text, 4.80:1 as text on #FAFAFA
 */

// ------------------------------------------------------------------- dark
val BrandBackground = Color(0xFF121212)
val BrandSurface = Color(0xFF181818)
val BrandSurfaceRaised = Color(0xFF1F1F1F)
val BrandSurfaceHigh = Color(0xFF262626)

val BrandPrimary = Color(0xFF22C55E)
val BrandPrimaryPressed = Color(0xFF16A34A)
val BrandOnPrimary = Color(0xFF06210F)

val BrandTextPrimary = Color(0xFFF5F5F5)
val BrandTextSecondary = Color(0xFFB0B0B0)

/** Visible boundary for outlined buttons, switches and pickers (>= 3:1). */
val BrandOutline = Color(0xFF6B6B6B)

/** Decorative hairline only - deliberately below 3:1, never a control boundary. */
val BrandHairline = Color(0xFF2E2E2E)

val BrandError = Color(0xFFFF6B6B)

// ------------------------------------------------------------------ light
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceRaised = Color(0xFFF2F2F2)
val LightSurfaceHigh = Color(0xFFE8E8E8)

/** A deeper green: the bright #22C55E cannot carry white text (2.2:1). */
val LightPrimary = Color(0xFF15803D)
val LightPrimaryPressed = Color(0xFF116232)
val LightOnPrimary = Color(0xFFFFFFFF)

val LightTextPrimary = Color(0xFF171717)
val LightTextSecondary = Color(0xFF575757)
val LightOutline = Color(0xFF6B6B6B)
val LightHairline = Color(0xFFE2E2E2)
val LightError = Color(0xFFB3261E)
