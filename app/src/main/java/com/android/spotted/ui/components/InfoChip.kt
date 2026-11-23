// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta

/**
 * Small gray pill that shows one fact, e.g. "Male" or "Suburb". It is read only: nothing happens on
 * a tap. Used by [PetDetailsChips] and by the loss summary on Alert detail.
 */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
  Text(
      text,
      style = jakarta(12, 15f, 600, SpottedTheme.colors.inkSoft),
      modifier =
          modifier
              .clip(RoundedCornerShape(100.dp))
              .background(SpottedTheme.colors.inputBackground)
              .padding(horizontal = 11.dp, vertical = 5.dp),
  )
}
