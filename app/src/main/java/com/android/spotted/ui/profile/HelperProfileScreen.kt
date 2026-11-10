// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.ui.home.TabBar
import com.android.spotted.ui.home.TabIndex
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta
import kotlinx.coroutines.launch

/** Test tags used by UI tests to find the elements of the [HelperProfileContent]. */
object HelperProfileTestTags {
  const val SCREEN = "helperProfile_screen"
  const val NAME = "helperProfile_name"
  const val AREA = "helperProfile_area"
  const val ACCOUNT_BUTTON = "helperProfile_accountButton"
  const val ACCOUNT_BUTTON_VISUAL = "helperProfile_accountButtonVisual"
  const val PETS_BROUGHT_HOME = "helperProfile_petsBroughtHome"
  const val EDIT_PROFILE = "helperProfile_editProfile"
  const val ERROR = "helperProfile_error"
  const val ACCOUNT_OPTIONS = "helperProfile_accountOptions"
  const val SIGN_OUT = "helperProfile_signOut"
  const val DELETE_ACCOUNT = "helperProfile_deleteAccount"
  const val DELETE_CONFIRM_DIALOG = "helperProfile_deleteConfirmDialog"
  const val DELETE_CONFIRM = "helperProfile_deleteConfirm"
  const val DELETE_CANCEL = "helperProfile_deleteCancel"

  fun badge(name: String) = "helperProfile_badge_$name"
}

/** Where the Helper profile can send the user: the other tabs and the profile form. */
data class HelperProfileNavigation(
    val onHomeClick: () -> Unit = {},
    val onMapClick: () -> Unit = {},
    val onCameraClick: () -> Unit = {},
    val onEditProfileClick: () -> Unit = {},
)

/**
 * Helper profile (Figma "13 · Helper profile"), wired to its [HelperProfileViewModel].
 *
 * The red button at the top offers to sign out or to delete the account; deleting asks for a
 * confirmation first. Both first clear the Google account remembered by Credential Manager (#75),
 * so the next sign-in asks which account to use; MainViewModel then sends the app back to Sign in.
 */
@Composable
fun HelperProfileScreen(
    viewModel: HelperProfileViewModel = viewModel(factory = HelperProfileViewModel.Factory),
    navigation: HelperProfileNavigation = HelperProfileNavigation(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var showAccountOptions by rememberSaveable { mutableStateOf(false) }
  // Runs again when coming back from the profile form, so the changes are shown
  LaunchedEffect(Unit) { viewModel.load() }

  // Forgets the chosen Google account before leaving, then runs the account action
  fun leave(action: () -> Unit) {
    showAccountOptions = false
    scope.launch {
      runCatching {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
      }
      action()
    }
  }

  HelperProfileContent(
      state = state,
      navigation = navigation,
      onAccountClick = { showAccountOptions = true },
  )

  if (showAccountOptions) {
    AccountOptionsDialog(
        onSignOut = { leave(viewModel::signOut) },
        onDeleteAccount = {
          showAccountOptions = false
          viewModel.requestDeleteAccount()
        },
        onDismiss = { showAccountOptions = false },
    )
  }

  if (state.isDeleteConfirmationVisible) {
    DeleteAccountConfirmDialog(
        onConfirm = { leave(viewModel::deleteAccount) },
        onCancel = viewModel::cancelDeleteAccount,
    )
  }
}

/** Stateless Helper profile, the part UI tests target. */
@Composable
fun HelperProfileContent(
    state: HelperProfileUiState,
    navigation: HelperProfileNavigation = HelperProfileNavigation(),
    onAccountClick: () -> Unit = {},
) {
  Box(
      Modifier.fillMaxSize()
          .background(SpottedTheme.colors.profileBackground)
          .testTag(HelperProfileTestTags.SCREEN)
  ) {
    Image(
        painterResource(R.drawable.background_textures),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize().alpha(0.4f),
    )
    Column(Modifier.fillMaxSize()) {
      Column(
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier =
              Modifier.weight(1f)
                  .verticalScroll(rememberScrollState())
                  .statusBarsPadding()
                  .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
      ) {
        Header(state, onAccountClick)
        state.errorMessage?.let { message ->
          Text(
              message,
              style = jakarta(13, 17f, FontWeight.SemiBold, SpottedTheme.colors.error),
              modifier = Modifier.testTag(HelperProfileTestTags.ERROR),
          )
        }
        PetsBroughtHome(state.petsBroughtHome)
        SectionLabel("BADGES")
        Badges(state)
        SectionLabel("HELPERS AROUND YOU THIS YEAR")
        Leaderboard(state.petsBroughtHome)
        EditProfile(state, navigation.onEditProfileClick)
      }
      Box(Modifier.fillMaxWidth().navigationBarsPadding(), Alignment.Center) {
        TabBar(
            onClicks =
                listOf(navigation.onHomeClick, navigation.onMapClick, navigation.onCameraClick, {}),
            selectedIndex = TabIndex.PROFILE,
        )
      }
    }
  }
}

/** Avatar, name and area on a white card, and the red account button on the right. */
@Composable
private fun Header(state: HelperProfileUiState, onAccountClick: () -> Unit) {
  Box(Modifier.fillMaxWidth()) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.align(Alignment.CenterStart)
                // Leaves room for the account button on the right
                .padding(end = 56.dp)
                // Half the avatar's height: the round photo fits the card's rounded end
                .clip(RoundedCornerShape(28.dp))
                .background(SpottedTheme.colors.surface)
                .padding(end = 16.dp),
    ) {
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.size(56.dp).clip(CircleShape).background(SpottedTheme.colors.profileAvatar),
      ) {
        Text(
            state.initial,
            style = jakarta(22, 29f, FontWeight.ExtraBold, SpottedTheme.colors.profileInitial),
        )
        // Covers the initial once loaded; if there is no photo, the initial stays visible
        state.photoUrl?.let { url ->
          AsyncImage(
              model = url,
              contentDescription = "Profile photo",
              contentScale = ContentScale.Crop,
              modifier = Modifier.matchParentSize().clip(CircleShape),
          )
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            state.name,
            style = jakarta(22, 29f, FontWeight.ExtraBold, SpottedTheme.colors.ink),
            maxLines = 1,
            modifier = Modifier.testTag(HelperProfileTestTags.NAME),
        )
        Text(
            if (state.homeArea.isBlank()) "Helping out nearby"
            else "Helping out in ${state.homeArea}",
            style = jakarta(10, 17f, FontWeight.Medium, SpottedTheme.colors.textSecondary),
            maxLines = 1,
            modifier = Modifier.testTag(HelperProfileTestTags.AREA),
        )
      }
    }
    // 48 x 32 dp red pill inside a 48 dp touch target
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.align(Alignment.CenterEnd)
                .size(48.dp)
                .clickable(
                    enabled = !state.isLeaving,
                    onClickLabel = "Sign out or delete account",
                    role = Role.Button,
                    onClick = onAccountClick,
                )
                .testTag(HelperProfileTestTags.ACCOUNT_BUTTON),
    ) {
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.width(48.dp)
                  .height(32.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(SpottedTheme.colors.logoutRed)
                  .testTag(HelperProfileTestTags.ACCOUNT_BUTTON_VISUAL),
      ) {
        Image(
            painterResource(R.drawable.ic_logout),
            contentDescription = "Sign out",
            modifier = Modifier.size(14.dp),
        )
      }
    }
  }
}

/** Blue card: how many pets the user helped bring home. */
@Composable
private fun PetsBroughtHome(count: Int) {
  Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(22.dp))
              .background(SpottedTheme.colors.blue)
              .padding(18.dp),
  ) {
    Text(
        if (count == 1) "1 pet brought home" else "$count pets brought home",
        style = jakarta(25, 33f, FontWeight.ExtraBold, SpottedTheme.colors.onAccent),
        modifier = Modifier.testTag(HelperProfileTestTags.PETS_BROUGHT_HOME),
    )
    Text(
        "Thanks to sightings you reported and their owners confirmed",
        style = jakarta(13, 17f, FontWeight.Medium, SpottedTheme.colors.statText),
    )
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
      text,
      style = jakarta(11, 14f, FontWeight.SemiBold, SpottedTheme.colors.textLabel, spacing = 0.66f),
  )
}

@Composable
private fun Badges(state: HelperProfileUiState) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
    Badge("First find", R.drawable.ic_badge_first_find, earned = state.hasFirstFind)
    Badge("5 confirmed", R.drawable.ic_badge_confirmed, earned = state.hasFiveConfirmed)
    // Not tracked yet: sightings don't record the time of day
    Badge("Night walker", R.drawable.ic_badge_night_walker, earned = false)
  }
}

/** A badge; not earned yet, it is greyed out. */
@Composable
private fun RowScope.Badge(label: String, icon: Int, earned: Boolean) {
  Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
          Modifier.weight(1f)
              .clip(RoundedCornerShape(18.dp))
              .background(SpottedTheme.colors.surface)
              .padding(vertical = 14.dp)
              .testTag(HelperProfileTestTags.badge(label)),
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(44.dp)
                .clip(CircleShape)
                .background(
                    if (earned) SpottedTheme.colors.badgeEarned else SpottedTheme.colors.badgeLocked
                ),
    ) {
      Image(
          painterResource(icon),
          contentDescription = null,
          // Tinted from the theme: the drawables' own blue is too dark on the dark badge
          colorFilter =
              ColorFilter.tint(
                  if (earned) SpottedTheme.colors.rankYou else SpottedTheme.colors.badgeLockedIcon
              ),
          modifier = Modifier.size(22.dp),
      )
    }
    Text(
        label,
        style =
            jakarta(
                12,
                16f,
                FontWeight.SemiBold,
                if (earned) SpottedTheme.colors.ink else SpottedTheme.colors.textMuted,
            ),
    )
  }
}

/** Other helpers are not ranked yet, so only the user's own count is listed. */
@Composable
private fun Leaderboard(petsBroughtHome: Int) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(SpottedTheme.colors.surface)
              .padding(horizontal = 16.dp, vertical = 6.dp),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
    ) {
      Text("1", style = jakarta(13, 17f, FontWeight.ExtraBold, SpottedTheme.colors.rankYou))
      Text(
          "You",
          style = jakarta(15, 20f, FontWeight.ExtraBold, SpottedTheme.colors.ink),
          modifier = Modifier.weight(1f),
      )
      Text(
          if (petsBroughtHome == 1) "1 pet" else "$petsBroughtHome pets",
          style = jakarta(13, 17f, FontWeight.SemiBold, SpottedTheme.colors.textSecondary),
      )
    }
  }
}

/** Opens the profile form, to update the name, home area and alert radius. */
@Composable
private fun EditProfile(state: HelperProfileUiState, onClick: () -> Unit) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(SpottedTheme.colors.surface)
              .clickable(onClickLabel = "Edit your profile", role = Role.Button, onClick = onClick)
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag(HelperProfileTestTags.EDIT_PROFILE),
  ) {
    Image(
        painterResource(R.drawable.ic_tab_profile),
        contentDescription = null,
        modifier = Modifier.size(22.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
      Text(
          "Edit your profile",
          style = jakarta(15, 20f, FontWeight.SemiBold, SpottedTheme.colors.ink),
      )
      Text(
          if (state.homeArea.isBlank()) "Name, home area and alerts"
          else "${state.homeArea} · alerts within ${state.alertRadiusKm} km",
          style = jakarta(12, 16f, FontWeight.Medium, SpottedTheme.colors.textSecondary),
          maxLines = 1,
      )
    }
    Text(">", style = jakarta(16, 21f, FontWeight.SemiBold, SpottedTheme.colors.textFaint))
  }
}

/** Sign out, or delete the account so the next sign-in starts with a new profile. */
@Composable
internal fun AccountOptionsDialog(
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
  // Colours from the theme: with fixed light ones, the title was black on the dark dialog
  val colors = SpottedTheme.colors
  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = colors.surface,
      title = { Text("Your account", style = jakarta(18, 24f, FontWeight.ExtraBold, colors.ink)) },
      text = {
        Column(Modifier.testTag(HelperProfileTestTags.ACCOUNT_OPTIONS)) {
          TextButton(
              onClick = onSignOut,
              modifier =
                  Modifier.fillMaxWidth()
                      .heightIn(min = 56.dp)
                      .testTag(HelperProfileTestTags.SIGN_OUT),
          ) {
            Text(
                "Sign out",
                style = jakarta(16, 22f, FontWeight.SemiBold, colors.blue),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
          }
          TextButton(
              onClick = onDeleteAccount,
              modifier = Modifier.fillMaxWidth().testTag(HelperProfileTestTags.DELETE_ACCOUNT),
          ) {
            // Centred like the Sign out button above it
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Delete account", style = jakarta(15, 20f, FontWeight.SemiBold, colors.error))
              Text(
                  "Removes your profile from Spotted. You can sign in again to start over.",
                  style = jakarta(12, 16f, FontWeight.Medium, colors.textSecondary),
                  textAlign = TextAlign.Center,
              )
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
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
      modifier = Modifier.testTag(HelperProfileTestTags.DELETE_CONFIRM_DIALOG),
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
            modifier = Modifier.testTag(HelperProfileTestTags.DELETE_CONFIRM),
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
            modifier = Modifier.testTag(HelperProfileTestTags.DELETE_CANCEL),
        ) {
          Text(
              stringResource(R.string.delete_account_confirm_cancel),
              style = jakarta(15, 20f, FontWeight.SemiBold, colors.blue),
          )
        }
      },
  )
}
