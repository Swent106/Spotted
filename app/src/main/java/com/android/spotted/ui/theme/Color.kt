// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Spotted palette (Figma "Spotted — App Screens")
private val SpottedBlue = Color(0xFF1B5BD7)
private val SpottedBlueTint = Color(0xFFEDF2FD)
private val SpottedMapBackground = Color(0xFFE8EEFC)
private val SpottedInk = Color(0xFF14161A)
private val SpottedInkSoft = Color(0xFF2C3340)
private val SpottedTextSecondary = Color(0xFF5F6672)
private val SpottedTextLabel = Color(0xFF6B7280)
private val SpottedInputBackground = Color(0xFFF5F6F8)
private val SpottedButtonBorder = Color(0xFFDDE0E6)
private val SpottedScreenBackground = Color(0xFFEFEFEF)
private val SpottedAvatarBackground = Color(0xFFD9E2E8)
private val SpottedError = Color(0xFFD93025)

// Home screen (Figma "01 · Near you")
private val SpottedHomeBackground = Color(0xFFE9E9E9)
private val SpottedLostRed = Color(0xFFC32F2F)
private val SpottedReportBorder = Color(0xFFB00808)
private val SpottedFeaturedPhotoBackground = Color(0xFFF0E0CE)
private val SpottedTabSelected = Color(0xFF005BE4)
private val SpottedTabBorder = Color(0xFFDADADA)
private val SpottedTabBarBorder = Color(0xFFE6E8EC)

// Helper profile (Figma "13 · Helper profile")
private val SpottedProfileBackground = Color(0xFFEDEEEF)
private val SpottedProfileAvatar = Color(0xFFDDE3EE)
private val SpottedProfileInitial = Color(0xFF30415F)
private val SpottedStatText = Color(0xFFD5E1FA)
private val SpottedLogoutRed = Color(0xFFFF6767)
private val SpottedBadgeEarned = Color(0xFFE8EEFC)
private val SpottedBadgeLocked = Color(0xFFF0F1F4)
private val SpottedBadgeLockedIcon = Color(0xFFA7ACB6)
private val SpottedTextMuted = Color(0xFF8D929B)
private val SpottedTextFaint = Color(0xFF9AA0AA)
private val SpottedRankYou = Color(0xFF1B4FC0)

/**
 * The Spotted palette for one appearance, light or dark. Screens read it through
 * [SpottedTheme.colors], so they follow the system dark mode instead of using fixed light colours.
 *
 * Each colour has the role of the `Spotted...` constant of the same name; [surface] is the
 * background of cards and dialogs, white in light mode. [warning] and [warningTint] are the amber
 * of notices (offline, pet found), [errorTint] the pale red behind error messages, and [onAccent]
 * the white of text and icons on blue or red buttons and on photos, in both modes.
 */
@Immutable
data class SpottedColors(
    val blue: Color,
    val blueTint: Color,
    val mapBackground: Color,
    val ink: Color,
    val inkSoft: Color,
    val textSecondary: Color,
    val textLabel: Color,
    val inputBackground: Color,
    val buttonBorder: Color,
    val screenBackground: Color,
    val avatarBackground: Color,
    val error: Color,
    val homeBackground: Color,
    val lostRed: Color,
    val reportBorder: Color,
    val featuredPhotoBackground: Color,
    val tabSelected: Color,
    val tabBorder: Color,
    val tabBarBorder: Color,
    val profileBackground: Color,
    val profileAvatar: Color,
    val profileInitial: Color,
    val statText: Color,
    val logoutRed: Color,
    val badgeEarned: Color,
    val badgeLocked: Color,
    val badgeLockedIcon: Color,
    val textMuted: Color,
    val textFaint: Color,
    val rankYou: Color,
    val surface: Color,
    val warning: Color,
    val warningTint: Color,
    val errorTint: Color,
    val onAccent: Color,
)

/** The Figma colours, as they are used today. */
val LightSpottedColors =
    SpottedColors(
        blue = SpottedBlue,
        blueTint = SpottedBlueTint,
        mapBackground = SpottedMapBackground,
        ink = SpottedInk,
        inkSoft = SpottedInkSoft,
        textSecondary = SpottedTextSecondary,
        textLabel = SpottedTextLabel,
        inputBackground = SpottedInputBackground,
        buttonBorder = SpottedButtonBorder,
        screenBackground = SpottedScreenBackground,
        avatarBackground = SpottedAvatarBackground,
        error = SpottedError,
        homeBackground = SpottedHomeBackground,
        lostRed = SpottedLostRed,
        reportBorder = SpottedReportBorder,
        featuredPhotoBackground = SpottedFeaturedPhotoBackground,
        tabSelected = SpottedTabSelected,
        tabBorder = SpottedTabBorder,
        tabBarBorder = SpottedTabBarBorder,
        profileBackground = SpottedProfileBackground,
        profileAvatar = SpottedProfileAvatar,
        profileInitial = SpottedProfileInitial,
        statText = SpottedStatText,
        logoutRed = SpottedLogoutRed,
        badgeEarned = SpottedBadgeEarned,
        badgeLocked = SpottedBadgeLocked,
        badgeLockedIcon = SpottedBadgeLockedIcon,
        textMuted = SpottedTextMuted,
        textFaint = SpottedTextFaint,
        rankYou = SpottedRankYou,
        surface = Color.White,
        warning = Color(0xFF875000),
        warningTint = Color(0xFFFFF2DE),
        errorTint = Color(0xFFFCE9E9),
        onAccent = Color.White,
    )

/**
 * Dark mode, matching the "5 · Dark mode" frames of the Figma: near-black backgrounds, dark grey
 * cards, light text, and the blue and red a bit brighter so they stay readable on dark.
 */
val DarkSpottedColors =
    SpottedColors(
        blue = Color(0xFF4D85F0),
        blueTint = Color(0xFF16284A),
        mapBackground = Color(0xFF15233F),
        ink = Color(0xFFECEEF1),
        inkSoft = Color(0xFFC9CED6),
        textSecondary = Color(0xFFA3A9B3),
        textLabel = Color(0xFF9AA1AC),
        inputBackground = Color(0xFF262A31),
        buttonBorder = Color(0xFF3A3F48),
        screenBackground = Color(0xFF0E1013),
        avatarBackground = Color(0xFF2A3138),
        error = Color(0xFFF28B82),
        homeBackground = Color(0xFF0E1013),
        lostRed = Color(0xFFE35D5D),
        reportBorder = Color(0xFFE35D5D),
        featuredPhotoBackground = Color(0xFF3A2E22),
        tabSelected = Color(0xFF4D85F0),
        tabBorder = Color(0xFF3A3F48),
        tabBarBorder = Color(0xFF2E333B),
        profileBackground = Color(0xFF0E1013),
        profileAvatar = Color(0xFF2A3140),
        profileInitial = Color(0xFFC9D6F0),
        statText = Color(0xFFD5E1FA),
        logoutRed = Color(0xFFFF7B7B),
        badgeEarned = Color(0xFF16284A),
        badgeLocked = Color(0xFF262A31),
        badgeLockedIcon = Color(0xFF6B717C),
        textMuted = Color(0xFF7E848E),
        textFaint = Color(0xFF8A909A),
        rankYou = Color(0xFF7FA6F5),
        surface = Color(0xFF1B1E24),
        warning = Color(0xFFF0B455),
        warningTint = Color(0xFF3A2A10),
        errorTint = Color(0xFF3A1A1C),
        onAccent = Color.White,
    )

/**
 * Colours of drawings rather than of the interface (the Sign in hero, the photo placeholder): they
 * stay the same in light and dark mode, like a picture.
 */
object SpottedIllustration {
  val bubbleGreen = Color(0xFFDEE9DC)
  val bubblePurple = Color(0xFFE7DCEC)
  val bubbleBeige = Color(0xFFF0E0CE)
  val bubbleBlue = Color(0xFFE8EEFC)
  val bubbleGrey = Color(0xFFDDE3EE)
  val bubbleSlate = Color(0xFFD9E2E8)
  val ring = Color(0xFFD3DBF5)
  val photoBorder = Color.White
  // Behind the camera preview and the photo just taken: dark in both modes, like a viewfinder
  val cameraBackground = Color(0xFF14161A)
  val photoPlaceholderStart = Color(0xFFA88D71)
  val photoPlaceholderEnd = Color(0xFFE3B88F)
}
