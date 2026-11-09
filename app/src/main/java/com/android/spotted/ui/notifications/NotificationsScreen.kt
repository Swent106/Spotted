// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.spotted.R
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
  const val RETRY = "notifications_retry"

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
      onRetry = viewModel::retry,
  )
}

/**
 * Stateless Notifications screen (Figma "Notifications", S5-15): every new sighting on the owner's
 * alerts, newest first, or a card when there is none or the list couldn't be loaded.
 */
@Composable
fun NotificationsContent(
    state: NotificationsUiState,
    nowMillis: Long,
    onOpen: (SightingNotification) -> Unit = {},
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
  Box(
      Modifier.fillMaxSize()
          .background(SpottedTheme.colors.homeBackground)
          .testTag(NotificationsScreenTestTags.SCREEN)
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
      Header(onBack)
      Body(state, nowMillis, onOpen, onRetry)
    }
  }
}

/** Round back button and the title. */
@Composable
private fun Header(onBack: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
        modifier =
            Modifier.size(44.dp)
                .clip(CircleShape)
                .background(SpottedTheme.colors.surface)
                .clickable(onClickLabel = "Back", role = Role.Button, onClick = onBack)
                .testTag(NotificationsScreenTestTags.BACK_BUTTON),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painterResource(R.drawable.ic_arrow_back),
          contentDescription = "Back",
          tint = SpottedTheme.colors.ink,
          modifier = Modifier.size(20.dp),
      )
    }
    Spacer(Modifier.width(12.dp))
    Text("Notifications", style = jakarta(22, 28f, 700, SpottedTheme.colors.ink))
  }
}

@Composable
private fun Body(
    state: NotificationsUiState,
    nowMillis: Long,
    onOpen: (SightingNotification) -> Unit,
    onRetry: () -> Unit,
) {
  when {
    state.isLoading ->
        CircularProgressIndicator(Modifier.size(32.dp).testTag(NotificationsScreenTestTags.LOADING))
    // A failure after a list keeps it on screen, below the message
    state.errorMessage != null && state.items.isEmpty() ->
        MessageCard(
            icon = R.drawable.ic_warning,
            iconColor = SpottedTheme.colors.error,
            iconBackground = SpottedTheme.colors.errorTint,
            title = state.errorMessage,
            text = "Check your connection and try again.",
            tag = NotificationsScreenTestTags.ERROR,
            onRetry = onRetry,
        )
    state.isEmpty ->
        MessageCard(
            icon = R.drawable.ic_notifications,
            iconColor = SpottedTheme.colors.blue,
            iconBackground = SpottedTheme.colors.blueTint,
            title = "No new sightings",
            text = "When someone reports seeing one of your pets, it shows up here.",
            tag = NotificationsScreenTestTags.EMPTY,
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

/** White card with an icon in a tinted circle, a title, a text and an optional Retry button. */
@Composable
private fun MessageCard(
    icon: Int,
    iconColor: Color,
    iconBackground: Color,
    title: String,
    text: String,
    tag: String,
    onRetry: (() -> Unit)? = null,
) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(SpottedTheme.colors.surface)
              .padding(horizontal = 20.dp, vertical = 24.dp)
              .testTag(tag),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(iconBackground),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painterResource(icon),
          contentDescription = null,
          tint = iconColor,
          modifier = Modifier.size(24.dp),
      )
    }
    Spacer(Modifier.height(4.dp))
    Text(title, style = jakarta(17, 22f, 700, SpottedTheme.colors.ink, TextAlign.Center))
    Text(text, style = jakarta(13, 18f, 500, SpottedTheme.colors.textSecondary, TextAlign.Center))
    onRetry?.let {
      Spacer(Modifier.height(4.dp))
      Button(
          onClick = it,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = SpottedTheme.colors.blue),
          modifier = Modifier.testTag(NotificationsScreenTestTags.RETRY),
      ) {
        Text("Retry", style = jakarta(15, 20f, 700, SpottedTheme.colors.onAccent))
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
