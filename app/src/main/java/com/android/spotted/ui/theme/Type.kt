// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.android.spotted.R

/**
 * Plus Jakarta Sans, the font used by the Figma designs. Only Medium, SemiBold and ExtraBold are
 * bundled, so Figma's Bold (700) text uses SemiBold.
 */
val PlusJakartaSans =
    FontFamily(
        Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
        Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
        Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
    )

/**
 * A Plus Jakarta Sans text style, with a theme colour such as `SpottedTheme.colors.ink`. There is
 * no overload taking a hex colour, so text colours always come from the theme.
 */
fun jakarta(
    size: Int,
    line: Float,
    weight: Int,
    color: Color,
    align: TextAlign = TextAlign.Start,
) =
    TextStyle(
        fontSize = size.sp,
        lineHeight = line.sp,
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight(weight),
        color = color,
        textAlign = align,
    )

/** Same as the [jakarta] above, with Compose types for the weight and the colour. */
fun jakarta(size: Int, line: Float, weight: FontWeight, color: Color, spacing: Float = 0f) =
    TextStyle(
        fontSize = size.sp,
        lineHeight = line.sp,
        fontFamily = PlusJakartaSans,
        fontWeight = weight,
        color = color,
        letterSpacing = spacing.sp,
    )

/**
 * The Material type scale in Plus Jakarta Sans, so Material components (dialogs, buttons, text
 * fields) use the same font as the screens. Sizes are Material's defaults.
 */
val Typography =
    Typography().run {
      fun TextStyle.jakarta() = copy(fontFamily = PlusJakartaSans)
      Typography(
          displayLarge = displayLarge.jakarta(),
          displayMedium = displayMedium.jakarta(),
          displaySmall = displaySmall.jakarta(),
          headlineLarge = headlineLarge.jakarta(),
          headlineMedium = headlineMedium.jakarta(),
          headlineSmall = headlineSmall.jakarta(),
          titleLarge = titleLarge.jakarta(),
          titleMedium = titleMedium.jakarta(),
          titleSmall = titleSmall.jakarta(),
          bodyLarge = bodyLarge.jakarta(),
          bodyMedium = bodyMedium.jakarta(),
          bodySmall = bodySmall.jakarta(),
          labelLarge = labelLarge.jakarta(),
          labelMedium = labelMedium.jakarta(),
          labelSmall = labelSmall.jakarta(),
      )
    }
