// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.spotted.R
import com.android.spotted.ui.components.SpottedTopBar
import com.android.spotted.ui.theme.SpottedTheme

object SettingsScreenTestTags {
  const val SCREEN = "settings_screen"
  const val BACK_BUTTON = "settings_backButton"
}

/** Texts of the Settings screen that tests check. */
object SettingsScreenTexts {
  const val TITLE = "Settings"
}

/** Settings screen wired to its [SettingsViewModel]. [onBack] returns to the previous screen. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    onBack: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  SettingsContent(state = state, onBack = onBack)
}

/** Stateless Settings screen: a top bar with a Back button. Later tasks add the settings. */
@Composable
fun SettingsContent(state: SettingsUiState, onBack: () -> Unit = {}) {
  Box(
      Modifier.fillMaxSize()
          .background(SpottedTheme.colors.homeBackground)
          .testTag(SettingsScreenTestTags.SCREEN)
  ) {
    // Same paw texture as Home
    Image(
        painterResource(R.drawable.background_textures),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize().alpha(0.4f),
    )
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      SpottedTopBar(
          title = SettingsScreenTexts.TITLE,
          onBack = onBack,
          backTestTag = SettingsScreenTestTags.BACK_BUTTON,
      )
    }
  }
}
