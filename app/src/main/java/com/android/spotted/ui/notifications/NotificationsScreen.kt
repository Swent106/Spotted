// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.spotted.model.notification.SightingNotification
import com.android.spotted.ui.home.PetPhoto
import com.android.spotted.ui.home.timeAgo
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta

object NotificationsScreenTestTags {
  const val SCREEN = "notifications_screen"
  const val BACK_BUTTON = "notifications_backButton"
  const val LIST = "notifications_list"
  const val EMPTY = "notifications_empty"
  const val ERROR = "notifications_error"
  const val LOADING = "notifications_loading"

  fun item(sightingId: String) = "notifications_item_$sightingId"
}

/**
 * Notifications screen wired to its [NotificationsViewModel]. When the owner taps a sighting, it
 * opens "My alert" for that alert through [onOpenAlert].
 *
 * @param now Current time, read when the list changes, for the "12 min ago" texts.
 */
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = viewModel(factory = NotificationsViewModel.Factory),
    onBack: () -> Unit = {},
    onOpenAlert: (alertId: String) -> Unit = {},
    now: () -> Long = System::currentTimeMillis,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val nowMillis = remember(state.items) { now() }

  LaunchedEffect(state.destinationAlertId) {
    state.destinationAlertId?.let { alertId ->
      onOpenAlert(alertId)
      viewModel.onNavigated()
    }
  }

  NotificationsContent(
      state = state,
      nowMillis = nowMillis,
      onOpen = viewModel::open,
      onBack = onBack,
  )
}

/** Stateless Notifications screen: every new sighting on the owner's alerts, newest first. */
@Composable
fun NotificationsContent(
    state: NotificationsUiState,
    nowMillis: Long,
    onOpen: (SightingNotification) -> Unit = {},
    onBack: () -> Unit = {},
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(SpottedTheme.colors.screenBackground)
              .statusBarsPadding()
              .padding(16.dp)
              .testTag(NotificationsScreenTestTags.SCREEN),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      TextButton(
          onClick = onBack,
          modifier = Modifier.testTag(NotificationsScreenTestTags.BACK_BUTTON),
      ) {
        Text("<", style = jakarta(20, 24f, 700, SpottedTheme.colors.ink))
      }
      Text("Notifications", style = jakarta(22, 28f, 700, SpottedTheme.colors.ink))
    }

    when {
      state.isLoading ->
          CircularProgressIndicator(
              Modifier.size(32.dp).testTag(NotificationsScreenTestTags.LOADING)
          )
      // A failure after a list keeps it on screen, below the message
      state.errorMessage != null && state.items.isEmpty() -> ErrorText(state.errorMessage)
      state.isEmpty ->
          Text(
              "No new sightings",
              style = jakarta(14, 20f, 500, SpottedTheme.colors.textSecondary),
              modifier = Modifier.testTag(NotificationsScreenTestTags.EMPTY),
          )
      else -> {
        state.errorMessage?.let { ErrorText(it) }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().testTag(NotificationsScreenTestTags.LIST),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(state.items, key = { it.sightingId }) { notification ->
            NotificationRow(notification, nowMillis, onClick = { onOpen(notification) })
          }
        }
      }
    }
  }
}

@Composable
private fun ErrorText(message: String) {
  Text(
      message,
      style = jakarta(14, 20f, 600, SpottedTheme.colors.error),
      modifier = Modifier.testTag(NotificationsScreenTestTags.ERROR),
  )
}

/** "Someone saw Milo · 12 min ago", with the helper's photo. */
@Composable
private fun NotificationRow(
    notification: SightingNotification,
    nowMillis: Long,
    onClick: () -> Unit,
) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(SpottedTheme.colors.surface)
              .clickable(onClick = onClick)
              .padding(12.dp)
              .testTag(NotificationsScreenTestTags.item(notification.sightingId)),
  ) {
    PetPhoto(
        notification.sightingPhotoUrl,
        Modifier.size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SpottedTheme.colors.avatarBackground),
    )
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(
          "Someone saw ${notification.petName}",
          style = jakarta(15, 20f, FontWeight.SemiBold, SpottedTheme.colors.ink),
      )
      Text(
          timeAgo(notification.seenAtMillis, nowMillis),
          style = jakarta(12, 16f, 500, SpottedTheme.colors.textSecondary),
      )
    }
    Text("Review", style = jakarta(13, 16f, 700, SpottedTheme.colors.blue))
  }
}
