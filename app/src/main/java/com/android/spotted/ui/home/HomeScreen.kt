// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.ui.components.RequestLocationPermissionOnce
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta

/** Test tags of the Home screen (`<feature>_<element>`). */
object HomeScreenTestTags {
  const val SCREEN = "home_screen"
  const val BODY = "home_body"
  const val SETTINGS_BUTTON = "home_settingsButton"
  const val SNACKBAR = "home_snackbar"
  const val NOTIFICATIONS_BUTTON = "home_notificationsButton"
  const val NOTIFICATIONS_BADGE = "home_notificationsBadge"
  const val ERROR_MESSAGE = "home_errorMessage"
  const val PETS_TITLE = "home_petsTitle"
  const val PETS_LOADER = "home_petsLoader"
  const val PETS_ERROR = "home_petsError"
  const val PETS_RETRY = "home_petsRetry"
  const val PETS_EMPTY = "home_petsEmpty"
  const val PETS_CAROUSEL = "home_petsCarousel"
  const val ALERTS_TITLE = "home_alertsTitle"
  const val ALERTS_LOADER = "home_alertsLoader"
  const val ALERTS_ERROR = "home_alertsError"
  const val ALERTS_RETRY = "home_alertsRetry"
  const val ALERTS_EMPTY = "home_alertsEmpty"
  const val OFFLINE_BANNER = "home_offlineBanner"
  const val OFFLINE_RETRY = "home_offlineRetry"
  const val REPORT_MISSING_BUTTON = "home_reportMissingButton"
  const val SCAN_TAG_BUTTON = "home_scanTagButton"
  const val ADD_PET_BUTTON = "home_addPetButton"
  const val AVATAR = "home_avatar"
  const val TAB_BAR = "home_tabBar"
  const val TAB_HOME = "home_tabHome"
  const val TAB_MAP = "home_tabMap"
  const val TAB_CAMERA = "home_tabCamera"
  const val TAB_PROFILE = "home_tabProfile"
  const val WAITING_BANNER = "home_waitingBanner"
  const val WAITING_SHEET = "home_waitingSheet"
  const val WAITING_CLOSE = "home_waitingClose"
  const val WAITING_ERROR = "home_waitingError"
  const val DELETE_DIALOG = "home_deleteDialog"
  const val DELETE_CONFIRM = "home_deleteConfirm"
  const val DELETE_CANCEL = "home_deleteCancel"
  const val SENT_SNACKBAR = "home_sentSnackbar"

  fun petCard(petId: String) = "home_pet_$petId"

  fun missingBadge(petId: String) = "home_missingBadge_$petId"

  fun alertCard(alertId: String) = "home_alert_$alertId"

  fun alertDistance(alertId: String) = "home_alertDistance_$alertId"

  fun seenButton(alertId: String) = "home_seenButton_$alertId"

  fun waitingRow(sightingId: String) = "home_waitingRow_$sightingId"

  fun waitingDelete(sightingId: String) = "home_waitingDelete_$sightingId"
}

/** Texts of the Home screen that tests check. */
object HomeScreenTexts {
  const val PETS_TITLE = "Your pets"
  const val NO_PETS = "No pets yet. Add one so people know how to care for them."
  const val NO_LOST_PETS = "No missing pets near you"
  const val RETRY = "Retry"
  const val OFFLINE_ALERTS = "You're offline · showing the last 5 alerts"
  const val ADD_PET = "Add a pet"
}

/**
 * Home screen wired to its [HomeViewModel]. Navigation is done by the caller through the callbacks.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    onReportMissingClick: () -> Unit = {},
    onAlertClick: (alertId: String) -> Unit = {},
    onSeenPetClick: (alertId: String) -> Unit = {},
    onAddPetClick: () -> Unit = {},
    onPetClick: (petId: String) -> Unit = {},
    onMissingPetClick: (alertId: String) -> Unit = {},
    onMapClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onScanTagClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // Home stays in the back stack: reload it when coming back, so a pet just reported missing or
  // a profile change (radius, home) shows up. The first time, the ViewModel has just loaded
  var shownBefore by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(Unit) { if (shownBefore) viewModel.refresh() else shownBefore = true }
  // Once granted, reload so "600 m away" is measured from the GPS instead of the home area
  RequestLocationPermissionOnce(onGranted = viewModel::refresh)
  HomeScreenContent(
      state = state,
      onReportMissingClick = onReportMissingClick,
      onAddPetClick = onAddPetClick,
      onPetClick = onPetClick,
      onMissingPetClick = onMissingPetClick,
      onMapClick = onMapClick,
      onCameraClick = onCameraClick,
      onProfileClick = onProfileClick,
      onScanTagClick = onScanTagClick,
      onAlertClick = onAlertClick,
      onSeenPetClick = onSeenPetClick,
      onRetryClick = viewModel::refresh,
      onNotificationsClick = onNotificationsClick,
      onWaitingBannerClick = viewModel::openWaitingSheet,
      onWaitingSheetClose = viewModel::closeWaitingSheet,
      onWaitingDeleteClick = viewModel::askToDeleteWaiting,
      onWaitingDeleteConfirm = viewModel::confirmDeleteWaiting,
      onWaitingDeleteCancel = viewModel::cancelDeleteWaiting,
      onSentSightingShown = viewModel::sentSightingShown,
      onSettingsClick = onSettingsClick,
  )
}

/** Stateless Home ("Near you") screen, from the Figma frame "01 · Near you". */
@Composable
fun HomeScreenContent(
    state: HomeUiState,
    onReportMissingClick: () -> Unit = {},
    onAlertClick: (alertId: String) -> Unit = {},
    onSeenPetClick: (alertId: String) -> Unit = {},
    onRetryClick: () -> Unit = {},
    onAddPetClick: () -> Unit = {},
    onPetClick: (petId: String) -> Unit = {},
    onMissingPetClick: (alertId: String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onScanTagClick: () -> Unit = {},
    onWaitingBannerClick: () -> Unit = {},
    onWaitingSheetClose: () -> Unit = {},
    onWaitingDeleteClick: (sightingId: String) -> Unit = {},
    onWaitingDeleteConfirm: () -> Unit = {},
    onWaitingDeleteCancel: () -> Unit = {},
    onSentSightingShown: () -> Unit = {},
) {
  var isOpeningPetProfile by remember { mutableStateOf(false) }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) isOpeningPetProfile = false
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  val snackbarHostState = remember { SnackbarHostState() }
  // "Your sighting of Milo was sent": marked as shown once it is gone, as a new state would cancel
  // it. It waits for the "Waiting to send" sheet to close, as it would be hidden behind it.
  val sentMessage = state.sentSighting?.let { sentMessage(it) }
  LaunchedEffect(state.sentSighting, state.isWaitingSheetOpen) {
    if (sentMessage != null && !state.isWaitingSheetOpen) {
      snackbarHostState.currentSnackbarData?.dismiss()
      snackbarHostState.showSnackbar(SentSnackbarVisuals(sentMessage))
      onSentSightingShown()
    }
  }

  Box(
      Modifier.fillMaxSize()
          .background(SpottedTheme.colors.homeBackground)
          .testTag(HomeScreenTestTags.SCREEN)
  ) {
    // Background texture
    Image(
        painterResource(R.drawable.background_textures),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize().alpha(0.4f),
    )

    // The tab bar is the Scaffold's bottom bar, so the list ends above it: no item can be
    // hidden behind the tab bar, whatever the screen size or the number of items
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
          // The "sighting sent" message is the only snackbar on Home
          SnackbarHost(snackbarHostState, Modifier.testTag(HomeScreenTestTags.SNACKBAR)) { data ->
            SentSnackbar(data.visuals.message)
          }
        },
        bottomBar = {
          Box(Modifier.fillMaxWidth().navigationBarsPadding(), Alignment.Center) {
            TabBar(onClicks = listOf({}, onMapClick, onCameraClick, onProfileClick))
          }
        },
    ) { innerPadding ->
      HomeList(
          state = state,
          onReportMissingClick = onReportMissingClick,
          onAlertClick = onAlertClick,
          onSeenPetClick = onSeenPetClick,
          onRetryClick = onRetryClick,
          onAddPetClick = onAddPetClick,
          onPetClick = { petId ->
            if (!isOpeningPetProfile) {
              isOpeningPetProfile = true
              onPetClick(petId)
            }
          },
          // Same guard: a double tap must not open the alert twice
          onMissingPetClick = { alertId ->
            if (!isOpeningPetProfile) {
              isOpeningPetProfile = true
              onMissingPetClick(alertId)
            }
          },
          onSettingsClick = onSettingsClick,
          onNotificationsClick = onNotificationsClick,
          onScanTagClick = onScanTagClick,
          onWaitingBannerClick = onWaitingBannerClick,
          modifier = Modifier.padding(innerPadding),
      )
    }
  }

  if (state.isWaitingSheetOpen) {
    WaitingSightingsSheet(
        sightings = state.waitingSightings,
        errorMessage = state.waitingErrorMessage,
        onDeleteClick = onWaitingDeleteClick,
        onClose = onWaitingSheetClose,
    )
  }
  if (state.waitingDeleteId != null) {
    DeleteWaitingDialog(onConfirm = onWaitingDeleteConfirm, onCancel = onWaitingDeleteCancel)
  }
}

/** Header, "Your pets", "Lost near you" and the Report missing button, in one lazy list. */
@Composable
private fun HomeList(
    state: HomeUiState,
    onReportMissingClick: () -> Unit,
    onAlertClick: (alertId: String) -> Unit,
    onSeenPetClick: (alertId: String) -> Unit,
    onRetryClick: () -> Unit,
    onAddPetClick: () -> Unit,
    onPetClick: (petId: String) -> Unit,
    onMissingPetClick: (alertId: String) -> Unit,
    onSettingsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onScanTagClick: () -> Unit,
    onWaitingBannerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // Lazy, so that a long list of lost pets only composes what is on screen
  LazyColumn(
      modifier = modifier.fillMaxSize().testTag(HomeScreenTestTags.BODY),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
  ) {
    item(key = "header") {
      Column {
        Header(
            state.userName,
            state.userPhotoUrl,
            state.pendingSightings,
            onSettingsClick,
            onNotificationsClick,
        )
        state.errorMessage?.let { message ->
          Spacer(Modifier.height(8.dp))
          Text(
              message,
              style = jakarta(13, 17f, FontWeight.SemiBold, SpottedTheme.colors.error),
              modifier = Modifier.testTag(HomeScreenTestTags.ERROR_MESSAGE),
          )
        }
      }
    }

    petsSection(state, onRetryClick, onAddPetClick, onPetClick, onMissingPetClick)

    waitingBanner(state, onWaitingBannerClick)

    lostPetsSection(state, onAlertClick, onSeenPetClick, onRetryClick)

    // Report a pet missing: at the end of the scroll, as in Figma
    item(key = "reportMissing") {
      OutlinedButton(
          onClick = onReportMissingClick,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, SpottedTheme.colors.reportBorder),
          colors =
              ButtonDefaults.outlinedButtonColors(containerColor = SpottedTheme.colors.surface),
          modifier =
              Modifier.padding(top = 9.dp)
                  .fillMaxWidth()
                  .height(52.dp)
                  .testTag(HomeScreenTestTags.REPORT_MISSING_BUTTON),
      ) {
        Text(
            "Report a pet missing",
            style = jakarta(15, 20f, FontWeight.SemiBold, SpottedTheme.colors.lostRed),
        )
      }
    }
    item(key = "scanTag") {
      OutlinedButton(
          onClick = onScanTagClick,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, SpottedTheme.colors.reportBorder),
          colors =
              ButtonDefaults.outlinedButtonColors(containerColor = SpottedTheme.colors.surface),
          modifier =
              Modifier.padding(top = 9.dp)
                  .fillMaxWidth()
                  .height(52.dp)
                  .testTag(HomeScreenTestTags.SCAN_TAG_BUTTON),
      ) {
        Text(
            "Scan a pet’s tag",
            style = jakarta(15, 20f, FontWeight.SemiBold, SpottedTheme.colors.blue),
        )
      }
    }
  }
}

/** "Hey Rayen", settings and notifications. */
@Composable
private fun Header(
    userName: String,
    photoUrl: String?,
    pendingSightings: Int,
    onSettingsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    // Avatar: the profile photo, or empty while there is none
    PetPhoto(
        photoUrl,
        Modifier.size(58.dp)
            .clip(CircleShape)
            .background(SpottedTheme.colors.avatarBackground)
            .testTag(HomeScreenTestTags.AVATAR),
    )
    Spacer(Modifier.width(9.dp))
    Text(
        if (userName.isBlank()) "Hey" else "Hey $userName",
        style = jakarta(19, 24f, FontWeight.SemiBold, SpottedTheme.colors.ink),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    HeaderButton(
        R.drawable.ic_settings,
        "Settings",
        19.dp,
        onSettingsClick,
        HomeScreenTestTags.SETTINGS_BUTTON,
    )
    HeaderButton(
        R.drawable.ic_notifications,
        "Notifications",
        21.dp,
        onNotificationsClick,
        HomeScreenTestTags.NOTIFICATIONS_BUTTON,
        badgeCount = pendingSightings,
    )
  }
}

/**
 * A 32 dp white circle as in Figma, inside a 48 dp touch target, with a red badge when [badgeCount]
 * is above 0 ("9+" above 9).
 */
@Composable
private fun HeaderButton(
    icon: Int,
    description: String,
    iconSize: Dp,
    onClick: () -> Unit,
    tag: String,
    badgeCount: Int = 0,
) {
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.size(48.dp)
              .clip(RoundedCornerShape(24.dp))
              .clickable(onClick = onClick)
              .testTag(tag),
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SpottedTheme.colors.surface),
    ) {
      // Tinted: the drawable is black, invisible on the dark surface
      Image(
          painterResource(icon),
          description,
          Modifier.size(iconSize),
          colorFilter = ColorFilter.tint(SpottedTheme.colors.ink),
      )
    }
    if (badgeCount > 0) {
      Text(
          if (badgeCount > 9) "9+" else "$badgeCount",
          style = jakarta(10, 12f, FontWeight.Bold, SpottedTheme.colors.onAccent),
          textAlign = TextAlign.Center,
          modifier =
              Modifier.align(Alignment.TopEnd)
                  .padding(top = 4.dp, end = 4.dp)
                  .defaultMinSize(minWidth = 16.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(SpottedTheme.colors.error)
                  .padding(horizontal = 4.dp, vertical = 2.dp)
                  .testTag(HomeScreenTestTags.NOTIFICATIONS_BADGE),
      )
    }
  }
}

/**
 * "Your pets · N", from the Figma frame "05 · Home · Near you": one white card with a horizontal
 * carousel of the user's pets, ending with "Add a pet" so pets can still be added after skipping
 * that onboarding step. A missing pet's card is red and opens its alert; the others open their
 * profile. The card has its own loading, error and empty state.
 */
private fun LazyListScope.petsSection(
    state: HomeUiState,
    onRetryClick: () -> Unit,
    onAddPetClick: () -> Unit,
    onPetClick: (petId: String) -> Unit,
    onMissingPetClick: (alertId: String) -> Unit,
) {
  item(key = "pets") {
    val pets = state.pets
    val error = pets.errorMessage
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier.padding(top = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SpottedTheme.colors.surface)
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
    ) {
      // "Your pets" keeps its own node, so it can still be found by its exact text
      Row {
        Text(
            HomeScreenTexts.PETS_TITLE,
            style = jakarta(15, 19f, FontWeight.Bold, SpottedTheme.colors.ink),
            modifier = Modifier.testTag(HomeScreenTestTags.PETS_TITLE),
        )
        if (pets.items.isNotEmpty()) {
          Text(
              " · ${pets.items.size}",
              style = jakarta(15, 19f, FontWeight.Bold, SpottedTheme.colors.ink),
          )
        }
      }
      Column(Modifier.padding(end = 14.dp)) {
        when {
          pets.isLoading && pets.items.isEmpty() -> SectionLoader(HomeScreenTestTags.PETS_LOADER)
          error != null && pets.items.isEmpty() -> PetsError(error, onRetryClick)
          pets.isEmpty ->
              SectionMessage(
                  HomeScreenTexts.NO_PETS,
                  SpottedTheme.colors.textSecondary,
                  HomeScreenTestTags.PETS_EMPTY,
              )
        }
      }
      if (!(pets.isLoading && pets.items.isEmpty())) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 14.dp),
            modifier = Modifier.fillMaxWidth().testTag(HomeScreenTestTags.PETS_CAROUSEL),
        ) {
          items(pets.items, key = { "pet_${it.id}" }) { pet ->
            val alert = state.alertByPetId[pet.id]
            PetCard(pet.id, pet.name, pet.photoUrl, alert, state.nowMillis) {
              if (alert != null) onMissingPetClick(alert.id) else onPetClick(pet.id)
            }
          }
          // A missing pet whose profile is not loaded still gets its card, drawn from its alert
          items(state.lostPetsNotListed, key = { "lost_${it.id}" }) { alert ->
            PetCard(alert.petId, alert.petName, alert.petPhotoUrl, alert, state.nowMillis) {
              onMissingPetClick(alert.id)
            }
          }
          item(key = "addPet") { AddPetCard(onAddPetClick) }
        }
      }
      // A refresh that failed keeps the pets and shows the error under them
      if (error != null && pets.items.isNotEmpty()) {
        Column(Modifier.padding(end = 14.dp)) { PetsError(error, onRetryClick) }
      }
    }
  }
}

@Composable
private fun PetsError(message: String, onRetryClick: () -> Unit) {
  SectionError(message, HomeScreenTestTags.PETS_ERROR, HomeScreenTestTags.PETS_RETRY, onRetryClick)
}

/** An error message, with a button to try loading again. */
@Composable
internal fun SectionError(
    message: String,
    tag: String,
    retryTag: String,
    onRetryClick: () -> Unit,
) {
  Column {
    SectionMessage(message, SpottedTheme.colors.error, tag)
    TextButton(onClick = onRetryClick, modifier = Modifier.testTag(retryTag)) {
      Text(
          HomeScreenTexts.RETRY,
          style = jakarta(14, 18f, FontWeight.SemiBold, SpottedTheme.colors.tabSelected),
      )
    }
  }
}

/**
 * One pet of the carousel: photo, name, status and a chip. A pet with an open [alert] is red, says
 * since when it is lost and has a "Missing" chip; the others are "Profile ready" and "Safe".
 */
@Composable
private fun PetCard(
    petId: String,
    name: String,
    photoUrl: String?,
    alert: Alert?,
    nowMillis: Long,
    onClick: () -> Unit,
) {
  val missing = alert != null
  val colors = SpottedTheme.colors
  val shape = RoundedCornerShape(16.dp)
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier =
          Modifier.clip(shape)
              .background(if (missing) colors.missingCardBackground else colors.petCardBackground)
              .border(
                  if (missing) 2.dp else 1.dp,
                  if (missing) colors.lostRed else colors.petCardBorder,
                  shape,
              )
              .clickable(
                  onClickLabel = if (missing) "See ${name}'s alert" else "See ${name}'s profile",
                  onClick = onClick,
              )
              .padding(start = 10.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)
              .testTag(HomeScreenTestTags.petCard(petId)),
  ) {
    PetPhoto(
        photoUrl,
        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(colors.avatarBackground),
    )
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(
          name,
          style = jakarta(14, 18f, FontWeight.Bold, colors.ink),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.widthIn(max = 140.dp),
      )
      Text(
          if (alert != null) missingDetail(alert.lostAtMillis, nowMillis) else "Profile ready",
          style = jakarta(11, 14f, FontWeight.Medium, colors.textSecondary),
      )
      if (missing) {
        PetChip(
            "Missing",
            colors.missingChipBackground,
            colors.missingChipText,
            Modifier.testTag(HomeScreenTestTags.missingBadge(petId)),
        )
      } else {
        PetChip("Safe", colors.safeChipBackground, colors.safeChipText)
      }
    }
  }
}

/** "Missing" or "Safe" under a pet's name. */
@Composable
private fun PetChip(
    label: String,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
  Text(
      label,
      style = jakarta(11, 14f, FontWeight.SemiBold, textColor),
      modifier =
          modifier
              .clip(RoundedCornerShape(8.dp))
              .background(background)
              .padding(horizontal = 7.dp, vertical = 2.dp),
  )
}

/** "+ Add" at the end of the carousel, in a dashed box as tall as a pet card. */
@Composable
private fun AddPetCard(onClick: () -> Unit) {
  val borderColor = SpottedTheme.colors.addPetBorder
  val blue = SpottedTheme.colors.blue
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
      modifier =
          Modifier.height(72.dp)
              .clip(RoundedCornerShape(16.dp))
              .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(16.dp.toPx() - stroke / 2),
                    style =
                        Stroke(
                            width = stroke,
                            pathEffect =
                                PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                        ),
                )
              }
              .testTag(HomeScreenTestTags.ADD_PET_BUTTON)
              .clickable(onClick = onClick)
              // Read as one "Add a pet" button, not as "+" and "Add"
              .clearAndSetSemantics {
                contentDescription = HomeScreenTexts.ADD_PET
                role = Role.Button
                onClick {
                  onClick()
                  true
                }
              }
              .padding(horizontal = 16.dp),
  ) {
    Text("+", style = jakarta(20, 25f, FontWeight.ExtraBold, blue))
    Text("Add", style = jakarta(11, 14f, FontWeight.SemiBold, blue))
  }
}

@Composable
internal fun SectionLoader(tag: String) {
  Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(Modifier.testTag(tag))
  }
}

@Composable
internal fun SectionMessage(message: String, color: Color, tag: String) {
  Text(
      message,
      style = jakarta(14, 18f, FontWeight.SemiBold, color),
      modifier =
          Modifier.padding(top = 12.dp)
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(SpottedTheme.colors.surface)
              .padding(16.dp)
              .testTag(tag),
  )
}

/** A tab of the tab bar, sized as in Figma. */
/** One tab; it is wider when selected, as in the Figma frames "01 · Near you" and "13". */
private class Tab(
    val icon: Int,
    val iconSize: Dp,
    val width: Dp,
    val selectedWidth: Dp,
    val label: String,
    val tag: String,
)

private val TABS =
    listOf(
        Tab(R.drawable.ic_tab_home, 26.dp, 54.dp, 81.dp, "Home", HomeScreenTestTags.TAB_HOME),
        Tab(R.drawable.ic_tab_map, 31.dp, 53.dp, 81.dp, "Map", HomeScreenTestTags.TAB_MAP),
        Tab(R.drawable.ic_tab_camera, 26.dp, 52.dp, 81.dp, "Camera", HomeScreenTestTags.TAB_CAMERA),
        Tab(
            R.drawable.ic_tab_profile,
            33.dp,
            51.dp,
            85.dp,
            "Profile",
            HomeScreenTestTags.TAB_PROFILE,
        ),
    )

/** Index of each tab in the [TabBar], to pass as its `selectedIndex`. */
internal object TabIndex {
  const val HOME = 0
  const val PROFILE = 3
}

/**
 * The [TABS], with one click callback per tab in the same order. The tab at [selectedIndex] is
 * highlighted, and each tab tells accessibility services whether it is the selected one. Also used
 * by the Helper profile screen.
 */
@Composable
internal fun TabBar(
    onClicks: List<() -> Unit>,
    modifier: Modifier = Modifier,
    selectedIndex: Int = TabIndex.HOME,
) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      // Spreads the remaining width between the tabs: 7 dp with Home selected, as in Figma
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier =
          modifier
              .padding(bottom = 7.dp)
              .width(264.dp)
              .height(56.dp)
              .clip(RoundedCornerShape(28.dp))
              .background(SpottedTheme.colors.surface)
              .border(1.dp, SpottedTheme.colors.tabBarBorder, RoundedCornerShape(28.dp))
              .padding(horizontal = 3.dp)
              .testTag(HomeScreenTestTags.TAB_BAR),
  ) {
    TABS.forEachIndexed { index, tab ->
      val selected = index == selectedIndex
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.width(if (selected) tab.selectedWidth else tab.width)
                  .height(51.dp)
                  .clip(RoundedCornerShape(27.dp))
                  .background(
                      if (selected) SpottedTheme.colors.tabSelected else SpottedTheme.colors.surface
                  )
                  .border(1.dp, SpottedTheme.colors.tabBorder, RoundedCornerShape(27.dp))
                  .selectable(selected = selected, role = Role.Tab, onClick = onClicks[index])
                  .testTag(tab.tag),
      ) {
        // Tinted: the drawables are black, invisible on the dark surface
        Image(
            painterResource(tab.icon),
            tab.label,
            Modifier.size(tab.iconSize),
            colorFilter = ColorFilter.tint(SpottedTheme.colors.ink),
        )
      }
    }
  }
}

/**
 * Photo loaded from its URL with Coil. Null shows only the background. Coil loads nothing in
 * Android Studio previews, so the fake "preview://" URLs use local drawables there.
 */
@Composable
internal fun PetPhoto(url: String?, modifier: Modifier) {
  Box(modifier) {
    val previewRes = if (LocalInspectionMode.current) PREVIEW_PHOTOS[url] else null
    if (previewRes != null) {
      Image(
          painterResource(previewRes),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize(),
      )
    } else if (url != null) {
      AsyncImage(
          model = url,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize(),
      )
    }
  }
}

// ------------------------------------------ Preview ------------------------------------------

private val PREVIEW_PHOTOS =
    mapOf(
        "preview://luna" to R.drawable.hero_helper_4,
        "preview://milo" to R.drawable.hero_pet_lost,
        "preview://nala" to R.drawable.hero_helper_2,
    )

private const val PREVIEW_NOW = 1_000_000_000_000L
private const val HOUR = 3_600_000L

private val previewState =
    HomeUiState(
        userName = "Rayen",
        homeArea = "Geneva",
        pets =
            HomeSection(
                listOf(
                    Pet(
                        id = "pixel",
                        ownerId = "me",
                        name = "Pixel",
                        species = Species.CAT,
                        photoUrl = "preview://milo",
                    ),
                    Pet(
                        id = "luna",
                        ownerId = "me",
                        name = "Luna",
                        species = Species.DOG,
                        photoUrl = "preview://luna",
                    ),
                )
            ),
        // Pixel is missing: its card is red
        ownLostPets =
            listOf(
                Alert(
                    id = "pixel-alert",
                    petId = "pixel",
                    ownerId = "me",
                    lastKnownLocation = Location(latitude = 46.2, longitude = 6.14),
                    lostAtMillis = PREVIEW_NOW - 2 * HOUR,
                    petName = "Pixel",
                    petSpecies = Species.CAT,
                    petPhotoUrl = "preview://milo",
                )
            ),
        lostPetsNearby =
            HomeSection(
                listOf(
                    Alert(
                        id = "milo",
                        petId = "milo",
                        ownerId = "someone",
                        lastKnownLocation = Location(latitude = 46.2, longitude = 6.14),
                        lostAtMillis = PREVIEW_NOW - 2 * HOUR,
                        petName = "Milo",
                        petSpecies = Species.CAT,
                        petPhotoUrl = "preview://milo",
                    ),
                    Alert(
                        id = "nala",
                        petId = "nala",
                        ownerId = "someone-else",
                        lastKnownLocation = Location(latitude = 46.19, longitude = 6.14),
                        lostAtMillis = PREVIEW_NOW - 30 * HOUR,
                        petName = "Nala",
                        petSpecies = Species.DOG,
                        petPhotoUrl = "preview://nala",
                    ),
                )
            ),
        nowMillis = PREVIEW_NOW,
    )

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenContentPreview() {
  HomeScreenContent(state = previewState)
}

@Preview(name = "Empty", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenContentEmptyPreview() {
  HomeScreenContent(state = HomeUiState(userName = "Rayen", homeArea = "Geneva"))
}

@Preview(name = "Loading", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenContentLoadingPreview() {
  HomeScreenContent(
      state =
          HomeUiState(
              userName = "Rayen",
              pets = HomeSection(isLoading = true),
              lostPetsNearby = HomeSection(isLoading = true),
          )
  )
}
