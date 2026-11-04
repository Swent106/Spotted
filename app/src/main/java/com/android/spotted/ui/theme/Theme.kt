// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** The Spotted palette of the current appearance; read it through [SpottedTheme.colors]. */
val LocalSpottedColors = staticCompositionLocalOf { LightSpottedColors }

/**
 * The Material colour roles, filled with the Spotted palette so that Material components (dialogs,
 * text fields, switches, snackbars) match the screens.
 */
private fun SpottedColors.toColorScheme(dark: Boolean): ColorScheme {
  val base = if (dark) darkColorScheme() else lightColorScheme()
  return base.copy(
      primary = blue,
      onPrimary = Color.White,
      primaryContainer = blueTint,
      onPrimaryContainer = blue,
      secondary = inkSoft,
      onSecondary = surface,
      error = error,
      onError = Color.White,
      background = screenBackground,
      onBackground = ink,
      surface = surface,
      onSurface = ink,
      surfaceVariant = inputBackground,
      onSurfaceVariant = textSecondary,
      outline = buttonBorder,
      outlineVariant = tabBarBorder,
      surfaceContainerLowest = surface,
      surfaceContainerLow = surface,
      surfaceContainer = surface,
      surfaceContainerHigh = surface,
      surfaceContainerHighest = inputBackground,
  )
}

/**
 * Theme of the app: the Spotted colours and Plus Jakarta Sans, light or dark.
 *
 * The phone's dynamic colours (Android 12+) are not used, so the app keeps its own identity.
 *
 * @param darkTheme whether to use the dark palette; follows the system setting by default.
 */
@Composable
fun SpottedTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val colors = if (darkTheme) DarkSpottedColors else LightSpottedColors
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window ?: return@SideEffect
      window.statusBarColor = colors.screenBackground.toArgb()
      // Dark icons on the light background, light icons on the dark one
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
  }
  CompositionLocalProvider(LocalSpottedColors provides colors) {
    MaterialTheme(
        colorScheme = colors.toColorScheme(darkTheme),
        typography = Typography,
        content = content,
    )
  }
}

/** Access to the current Spotted palette, e.g. `SpottedTheme.colors.ink`. */
object SpottedTheme {
  val colors: SpottedColors
    @Composable @ReadOnlyComposable get() = LocalSpottedColors.current
}
