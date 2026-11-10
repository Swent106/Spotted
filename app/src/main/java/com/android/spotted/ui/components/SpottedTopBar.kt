// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.spotted.R
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta

/** Size of the back button: also its touch target, the 48 dp minimum for accessibility. */
val BACK_BUTTON_SIZE = 48.dp

/**
 * The app's back button (Figma "Top bar · Back button"): a 48 dp circle on the card colour with a
 * chevron. The same on every screen, alone on a photo or in a [SpottedTopBar].
 *
 * @param testTag the screen's tag for its back button, e.g. `report_backButton`.
 */
@Composable
fun SpottedBackButton(
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
  val backLabel = stringResource(R.string.back)
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          modifier
              .size(BACK_BUTTON_SIZE)
              .clip(CircleShape)
              .background(SpottedTheme.colors.surface)
              .clickable(
                  enabled = enabled,
                  onClickLabel = backLabel,
                  role = Role.Button,
                  onClick = onClick,
              )
              .testTag(testTag),
  ) {
    Icon(
        painterResource(R.drawable.ic_chevron_left),
        contentDescription = backLabel,
        tint = SpottedTheme.colors.ink,
        modifier = Modifier.size(22.dp),
    )
  }
}

/**
 * The top of a screen opened from another one: [SpottedBackButton], then the [title] and an
 * optional [subtitle] in the Figma header style.
 */
@Composable
fun SpottedTopBar(
    title: String,
    onBack: () -> Unit,
    backTestTag: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backEnabled: Boolean = true,
) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = modifier.fillMaxWidth(),
  ) {
    SpottedBackButton(onClick = onBack, testTag = backTestTag, enabled = backEnabled)
    Column(Modifier.weight(1f)) {
      Text(
          title,
          style = jakarta(23, 31f, FontWeight.ExtraBold, SpottedTheme.colors.ink),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
      subtitle?.let {
        Text(it, style = jakarta(12, 16f, FontWeight.Medium, SpottedTheme.colors.textSecondary))
      }
    }
  }
}
