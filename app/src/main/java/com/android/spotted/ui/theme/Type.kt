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

fun jakarta(
    size: Int,
    line: Float,
    weight: Int,
    color: Long,
    align: TextAlign = TextAlign.Start,
) =
    TextStyle(
        fontSize = size.sp,
        lineHeight = line.sp,
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight(weight),
        color = Color(color),
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

// Set of Material typography styles to start with
val Typography =
    Typography(
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            )
    )
