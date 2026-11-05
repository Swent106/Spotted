// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [SpottedTheme]: the palette it provides in light and dark mode. */
@RunWith(AndroidJUnit4::class)
class SpottedThemeTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var colors: SpottedColors
  private lateinit var scheme: ColorScheme
  private lateinit var typography: Typography

  private fun showTheme(darkTheme: Boolean) {
    composeTestRule.setContent {
      SpottedTheme(darkTheme = darkTheme) {
        colors = SpottedTheme.colors
        scheme = MaterialTheme.colorScheme
        typography = MaterialTheme.typography
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun lightTheme_providesTheFigmaColors() {
    showTheme(darkTheme = false)

    assertEquals(LightSpottedColors, colors)
    assertEquals(LightSpottedColors.blue, scheme.primary)
    assertEquals(LightSpottedColors.screenBackground, scheme.background)
    assertEquals(LightSpottedColors.ink, scheme.onSurface)
  }

  @Test
  fun darkTheme_providesTheDarkPalette() {
    showTheme(darkTheme = true)

    assertEquals(DarkSpottedColors, colors)
    assertEquals(DarkSpottedColors.blue, scheme.primary)
    assertEquals(DarkSpottedColors.surface, scheme.surface)
    assertEquals(DarkSpottedColors.ink, scheme.onSurface)
  }

  @Test
  fun darkTheme_dialogsUseTheDarkSurface() {
    showTheme(darkTheme = true)

    // AlertDialog's default background: it must match the cards, not Material's own dark grey
    assertEquals(DarkSpottedColors.surface, scheme.surfaceContainerHigh)
  }

  @Test
  fun darkPalette_keepsTextReadable() {
    // Light text on dark backgrounds, the reverse of the light palette
    with(DarkSpottedColors) {
      assertTrue(ink.luminance() > 0.7f)
      assertTrue(screenBackground.luminance() < 0.05f)
      assertTrue(surface.luminance() < 0.05f)
    }
    with(LightSpottedColors) {
      assertTrue(ink.luminance() < 0.05f)
      assertTrue(screenBackground.luminance() > 0.7f)
    }
  }

  @Test
  fun materialTypography_usesPlusJakartaSans() {
    showTheme(darkTheme = false)

    assertEquals(PlusJakartaSans, typography.bodyLarge.fontFamily)
    assertEquals(PlusJakartaSans, typography.titleLarge.fontFamily)
    assertEquals(PlusJakartaSans, typography.labelLarge.fontFamily)
  }
}
