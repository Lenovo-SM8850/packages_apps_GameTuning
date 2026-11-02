// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.miner7222.gametuning.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Used when the system has no dynamic colors; a dark scheme in the blue family of the design. */
private val FallbackDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFA0CAFD),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF0F4A73),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F8),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6B3B00),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    error = Color(0xFFFFB4AB),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A),
    inverseSurface = Color(0xFFE1E2E8),
    inverseOnSurface = Color(0xFF2E3135),
    inversePrimary = Color(0xFF0F4A73),
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun GameTuningTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    // Transparent system bars, with dark icons on the light theme and light icons on the dark one.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = AndroidColor.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = AndroidColor.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        else -> FallbackDark
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
