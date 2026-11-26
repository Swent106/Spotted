// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.spotted.R
import com.android.spotted.ui.components.SpottedTopBar
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta
import kotlinx.coroutines.launch

object SettingsScreenTestTags {
  const val SCREEN = "settings_screen"
  const val BACK_BUTTON = "settings_backButton"
  const val ACCOUNT_SECTION = "settings_accountSection"
  const val SIGN_OUT = "settings_signOut"
  const val ERROR = "settings_error"
}

/** Texts of the Settings screen that tests check. */
object SettingsScreenTexts {
  const val TITLE = "Settings"
  const val ACCOUNT = "ACCOUNT"
  const val SIGN_OUT = "Sign out"
}

/**
 * Settings screen wired to its [SettingsViewModel]. [onBack] returns to the previous screen.
 *
 * Signing out first clears the Google account remembered by Credential Manager (#75), so the next
 * sign-in asks which account to use; MainViewModel then sends the app back to Sign in.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    onBack: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  SettingsContent(
      state = state,
      onBack = onBack,
      onSignOut = {
        scope.launch {
          runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
          }
          viewModel.signOut()
        }
      },
  )
}

/** Stateless Settings screen: a top bar with a Back button, then the Account section. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
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
      AccountSection(state, onSignOut)
    }
  }
}

/** "Account" section: the Sign out button, disabled while signing out, and its error. */
@Composable
private fun AccountSection(state: SettingsUiState, onSignOut: () -> Unit) {
  val colors = SpottedTheme.colors
  Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth().testTag(SettingsScreenTestTags.ACCOUNT_SECTION),
  ) {
    Text(
        SettingsScreenTexts.ACCOUNT,
        style = jakarta(11, 14f, FontWeight.SemiBold, colors.textLabel, spacing = 0.66f),
    )
    TextButton(
        onClick = onSignOut,
        enabled = !state.isLeaving,
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .testTag(SettingsScreenTestTags.SIGN_OUT),
    ) {
      Text(
          SettingsScreenTexts.SIGN_OUT,
          style =
              jakarta(
                  16,
                  22f,
                  FontWeight.SemiBold,
                  if (state.isLeaving) colors.textSecondary else colors.logoutRed,
              ),
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
      )
    }
    state.errorMessage?.let { message ->
      Text(
          message,
          style = jakarta(13, 17f, FontWeight.SemiBold, colors.error),
          modifier = Modifier.testTag(SettingsScreenTestTags.ERROR),
      )
    }
  }
}
