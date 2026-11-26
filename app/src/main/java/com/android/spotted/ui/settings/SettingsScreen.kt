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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
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
  const val DELETE_ACCOUNT = "settings_deleteAccount"
  const val DELETE_CONFIRM_DIALOG = "settings_deleteConfirmDialog"
  const val DELETE_CONFIRM = "settings_deleteConfirm"
  const val DELETE_CANCEL = "settings_deleteCancel"
  const val ERROR = "settings_error"
}

/** Texts of the Settings screen that tests check. */
object SettingsScreenTexts {
  const val TITLE = "Settings"
  const val ACCOUNT = "ACCOUNT"
  const val SIGN_OUT = "Sign out"
  const val DELETE_ACCOUNT = "Delete account"
}

/**
 * Settings screen wired to its [SettingsViewModel]. [onBack] returns to the previous screen.
 *
 * Deleting the account asks for a confirmation first. Signing out and deleting first clear the
 * Google account remembered by Credential Manager (#75), so the next sign-in asks which account to
 * use; MainViewModel then sends the app back to Sign in.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    onBack: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  // Forgets the chosen Google account before leaving, then runs the account action
  fun leave(action: () -> Unit) {
    scope.launch {
      runCatching {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
      }
      action()
    }
  }

  SettingsContent(
      state = state,
      onBack = onBack,
      onSignOut = { leave(viewModel::signOut) },
      onDeleteAccount = viewModel::requestDeleteAccount,
  )

  if (state.isDeleteConfirmationVisible) {
    DeleteAccountConfirmDialog(
        onConfirm = {
          // Hide the dialog first: clearing Credential Manager takes a moment
          viewModel.cancelDeleteAccount()
          leave(viewModel::deleteAccount)
        },
        onCancel = viewModel::cancelDeleteAccount,
    )
  }
}

/** Stateless Settings screen: a top bar with a Back button, then the Account section. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
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
      AccountSection(state, onSignOut, onDeleteAccount)
    }
  }
}

/**
 * "Account" section: the Sign out and Delete account buttons, both disabled while leaving, and the
 * error.
 */
@Composable
private fun AccountSection(
    state: SettingsUiState,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
  val colors = SpottedTheme.colors
  Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth().testTag(SettingsScreenTestTags.ACCOUNT_SECTION),
  ) {
    Text(
        SettingsScreenTexts.ACCOUNT,
        style = jakarta(11, 14f, FontWeight.SemiBold, colors.textLabel, spacing = 0.66f),
    )
    AccountButton(
        text = SettingsScreenTexts.SIGN_OUT,
        enabled = !state.isLeaving,
        onClick = onSignOut,
        testTag = SettingsScreenTestTags.SIGN_OUT,
    )
    AccountButton(
        text = SettingsScreenTexts.DELETE_ACCOUNT,
        enabled = !state.isLeaving,
        onClick = onDeleteAccount,
        testTag = SettingsScreenTestTags.DELETE_ACCOUNT,
    )
    state.errorMessage?.let { message ->
      Text(
          message,
          style = jakarta(13, 17f, FontWeight.SemiBold, colors.error),
          modifier = Modifier.testTag(SettingsScreenTestTags.ERROR),
      )
    }
  }
}

/** Full-width red button of the Account section; greyed out while disabled. */
@Composable
private fun AccountButton(text: String, enabled: Boolean, onClick: () -> Unit, testTag: String) {
  val colors = SpottedTheme.colors
  TextButton(
      onClick = onClick,
      enabled = enabled,
      modifier =
          Modifier.fillMaxWidth()
              .heightIn(min = 56.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(colors.surface)
              .testTag(testTag),
  ) {
    Text(
        text,
        style =
            jakarta(
                16,
                22f,
                FontWeight.SemiBold,
                if (enabled) colors.logoutRed else colors.textSecondary,
            ),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
  }
}

/**
 * Asks the user to confirm the account deletion. Tapping outside the dialog or going back counts as
 * [onCancel].
 */
@Composable
internal fun DeleteAccountConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
  val colors = SpottedTheme.colors
  AlertDialog(
      onDismissRequest = onCancel,
      containerColor = colors.surface,
      modifier = Modifier.testTag(SettingsScreenTestTags.DELETE_CONFIRM_DIALOG),
      title = {
        Text(
            stringResource(R.string.delete_account_confirm_title),
            style = jakarta(18, 24f, FontWeight.ExtraBold, colors.ink),
        )
      },
      text = {
        Text(
            stringResource(R.string.delete_account_confirm_text),
            style = jakarta(14, 20f, FontWeight.Medium, colors.textSecondary),
        )
      },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.testTag(SettingsScreenTestTags.DELETE_CONFIRM),
        ) {
          Text(
              stringResource(R.string.delete_account_confirm_delete),
              style = jakarta(15, 20f, FontWeight.SemiBold, colors.error),
          )
        }
      },
      dismissButton = {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.testTag(SettingsScreenTestTags.DELETE_CANCEL),
        ) {
          Text(
              stringResource(R.string.delete_account_confirm_cancel),
              style = jakarta(15, 20f, FontWeight.SemiBold, colors.blue),
          )
        }
      },
  )
}
