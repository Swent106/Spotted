// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.myalert

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.android.spotted.model.Location
import com.android.spotted.model.alert.alertShareText
import com.android.spotted.model.sighting.LikelyArea
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus
import com.android.spotted.model.sighting.compassLabel
import com.android.spotted.ui.components.SpottedTopBar
import com.android.spotted.ui.components.createMapView
import com.android.spotted.ui.components.shareText
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/** Test tags used by UI tests to find the elements of the My alert screen. */
object MyAlertScreenTestTags {
  const val SCREEN = "myAlertScreen"
  const val BACK_BUTTON = "myAlertBackButton"
  const val MAP = "myAlertMap"
  const val LOADING = "myAlertLoading"
  const val ERROR = "myAlertError"
  const val NO_SIGHTINGS = "myAlertNoSightings"
  const val SIGHTING_PREFIX = "myAlertSighting_"
  const val STATUS_PREFIX = "myAlertStatus_"
  const val CONFIRM_PREFIX = "myAlertConfirm_"
  const val DISMISS_PREFIX = "myAlertDismiss_"
  const val PET_HOME_BUTTON = "myAlertPetHomeButton"
  const val SHARE_BUTTON = "myAlertShareButton"
  const val PET_HOME_DIALOG = "myAlertPetHomeDialog"
  const val PET_HOME_CONFIRM = "myAlertPetHomeConfirm"
  const val PET_HOME_CANCEL = "myAlertPetHomeCancel"

  fun sighting(id: String) = "$SIGHTING_PREFIX$id"

  fun status(id: String) = "$STATUS_PREFIX$id"

  fun confirm(id: String) = "$CONFIRM_PREFIX$id"

  fun dismiss(id: String) = "$DISMISS_PREFIX$id"
}

/** A sighting drawn on the map, with the number it has in the list below. */
data class SightingMarker(val number: Int, val location: Location)

/**
 * "My alert" wired to [viewModel]: shows [MyAlertContent] with its state and sends the owner's
 * actions to the ViewModel. Calls [onBack] on Back, and once the alert is closed ("My pet is
 * home"), since there is nothing left to review.
 */
@Composable
fun MyAlertScreen(viewModel: MyAlertViewModel, onBack: () -> Unit) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  LaunchedEffect(state.isClosed) { if (state.isClosed) onBack() }
  MyAlertContent(
      state = state,
      onBack = onBack,
      onConfirm = viewModel::confirm,
      onDismiss = viewModel::dismiss,
      onMarkFound = viewModel::markFound,
      // The system share sheet, launched from the View
      onShare = { state.alert?.let { context.shareText(alertShareText(it, state.placeName)) } },
  )
}

/**
 * Stateless "My alert" screen (Figma "02 · Lost pet · prediction map"): the owner sees the
 * sightings of their alert on a map with the likely area, reviews each one, and closes the alert
 * when the pet is home. Every value comes from [state]; every tap goes through a callback.
 *
 * @param map Draws the map. The real one is an OpenStreetMap view; UI tests pass a placeholder.
 */
@Composable
fun MyAlertContent(
    state: MyAlertUiState,
    onBack: () -> Unit = {},
    onConfirm: (sightingId: String) -> Unit = {},
    onDismiss: (sightingId: String) -> Unit = {},
    onMarkFound: () -> Unit = {},
    onShare: () -> Unit = {},
    map:
        @Composable
        (
            markers: List<SightingMarker>,
            likelyArea: LikelyArea?,
            center: Location,
            Modifier,
        ) -> Unit =
        { markers, likelyArea, center, modifier ->
          MyAlertMap(markers, likelyArea, center, modifier)
        },
) {
  var askPetHome by rememberSaveable { mutableStateOf(false) }
  // Numbers follow the list (newest first), so a number means the same sighting on the map
  val numbers =
      state.sightings.withIndex().associate { (index, sighting) -> sighting.id to index + 1 }

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(SpottedTheme.colors.screenBackground)
              .verticalScroll(rememberScrollState())
              .padding(16.dp)
              .testTag(MyAlertScreenTestTags.SCREEN),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SpottedTopBar(
        title = state.alert?.let { "${it.petName} is missing" } ?: "My alert",
        onBack = onBack,
        backTestTag = MyAlertScreenTestTags.BACK_BUTTON,
    )

    // One state shows one thing. An error with a loaded alert comes from a failed action (confirm,
    // dismiss, pet is home): it is shown above the alert instead of replacing it, so the owner
    // keeps
    // what they were looking at and can try again
    val alert = state.alert
    when {
      state.isLoading ->
          CircularProgressIndicator(Modifier.size(32.dp).testTag(MyAlertScreenTestTags.LOADING))
      alert == null -> state.errorMessage?.let { ErrorMessage(it) }
      else -> {
        state.errorMessage?.let { ErrorMessage(it) }
        map(
            state.mapSightings.map { SightingMarker(numbers.getValue(it.id), it.location) },
            state.likelyArea,
            state.likelyArea?.center
                ?: state.mapSightings.firstOrNull()?.location
                ?: alert.lastKnownLocation,
            Modifier.fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SpottedTheme.colors.mapBackground)
                .testTag(MyAlertScreenTestTags.MAP),
        )

        Text("LATEST SIGHTINGS", style = jakarta(12, 16f, 700, SpottedTheme.colors.textLabel))
        if (state.sightings.isEmpty()) {
          Text(
              "No sightings yet. You'll see them here as soon as someone reports one.",
              style = jakarta(13, 18f, 500, SpottedTheme.colors.textSecondary),
              modifier = Modifier.testTag(MyAlertScreenTestTags.NO_SIGHTINGS),
          )
        }
        state.sightings.forEach { sighting ->
          SightingRow(
              number = numbers.getValue(sighting.id),
              sighting = sighting,
              onConfirm = { onConfirm(sighting.id) },
              onDismiss = { onDismiss(sighting.id) },
          )
        }

        // Figma "20 · My alert": "My pet is home", then "Share" on the same line
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Button(
              onClick = { askPetHome = true },
              colors = ButtonDefaults.buttonColors(containerColor = SpottedTheme.colors.blue),
              shape = RoundedCornerShape(16.dp),
              modifier =
                  Modifier.weight(1f).height(56.dp).testTag(MyAlertScreenTestTags.PET_HOME_BUTTON),
          ) {
            Text("My pet is home", style = jakarta(16, 20f, 700, SpottedTheme.colors.onAccent))
          }
          Button(
              onClick = onShare,
              colors =
                  ButtonDefaults.buttonColors(containerColor = SpottedTheme.colors.inputBackground),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.height(56.dp).testTag(MyAlertScreenTestTags.SHARE_BUTTON),
          ) {
            Text("Share", style = jakarta(16, 20f, 700, SpottedTheme.colors.ink))
          }
        }
      }
    }
  }

  if (askPetHome) {
    AlertDialog(
        onDismissRequest = { askPetHome = false },
        title = { Text("Is your pet home?") },
        text = { Text("The alert will be closed and nearby users will stop looking.") },
        confirmButton = {
          TextButton(
              onClick = {
                askPetHome = false
                onMarkFound()
              },
              modifier = Modifier.testTag(MyAlertScreenTestTags.PET_HOME_CONFIRM),
          ) {
            Text("Yes, close the alert")
          }
        },
        dismissButton = {
          TextButton(
              onClick = { askPetHome = false },
              modifier = Modifier.testTag(MyAlertScreenTestTags.PET_HOME_CANCEL),
          ) {
            Text("Not yet")
          }
        },
        modifier = Modifier.testTag(MyAlertScreenTestTags.PET_HOME_DIALOG),
    )
  }
}

/** The screen's error, alone when the alert couldn't be loaded, else above it. */
@Composable
private fun ErrorMessage(message: String) {
  Text(
      message,
      style = jakarta(14, 20f, 600, SpottedTheme.colors.error),
      modifier = Modifier.testTag(MyAlertScreenTestTags.ERROR),
  )
}

/** One sighting in the list: number, photo, time and place, status, and the review buttons. */
@Composable
private fun SightingRow(
    number: Int,
    sighting: Sighting,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  val dismissed = sighting.status == SightingStatus.DISMISSED
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .background(SpottedTheme.colors.surface, RoundedCornerShape(16.dp))
              .padding(12.dp)
              // Dismissed sightings stay in the list, greyed out
              .alpha(if (dismissed) DISMISSED_ALPHA else 1f)
              .testTag(MyAlertScreenTestTags.sighting(sighting.id)),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
          Modifier.size(28.dp).clip(CircleShape).background(SpottedTheme.colors.blue),
          contentAlignment = Alignment.Center,
      ) {
        Text("$number", style = jakarta(13, 16f, 700, SpottedTheme.colors.onAccent))
      }
      Spacer(Modifier.width(12.dp))
      Box(
          Modifier.size(48.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(SpottedTheme.colors.avatarBackground)
      ) {
        sighting.photoUrl?.let { url ->
          AsyncImage(
              model = url,
              contentDescription = "Photo of sighting $number",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
          )
        }
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(
            formatTime(sighting.seenAtMillis),
            style = jakarta(14, 18f, 700, SpottedTheme.colors.ink),
        )
        Text(
            formatPlace(sighting.location),
            style = jakarta(12, 16f, 500, SpottedTheme.colors.textSecondary),
        )
        sighting.headingDegrees?.let { heading ->
          Row(
              modifier = Modifier.testTag("alertDetail_sightingDirection_${sighting.id}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
                text = "↑",
                modifier = Modifier.graphicsLayer { rotationZ = heading.toFloat() },
                style = jakarta(14, 18f, 700, SpottedTheme.colors.blue),
            )
            Text(
                "Went ${compassLabel(heading)}",
                style = jakarta(11, 15f, 600, SpottedTheme.colors.blue),
            )
          }
        }
      }
      StatusChip(sighting)
    }
    if (sighting.status == SightingStatus.PENDING) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = SpottedTheme.colors.blue),
            modifier = Modifier.weight(1f).testTag(MyAlertScreenTestTags.confirm(sighting.id)),
        ) {
          Text("It's my pet")
        }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).testTag(MyAlertScreenTestTags.dismiss(sighting.id)),
        ) {
          Text("Not my pet")
        }
      }
    }
  }
}

@Composable
private fun StatusChip(sighting: Sighting) {
  val (label, color) =
      when (sighting.status) {
        SightingStatus.PENDING -> "To review" to SpottedTheme.colors.blue
        SightingStatus.CONFIRMED -> "Confirmed" to SpottedTheme.colors.success
        SightingStatus.DISMISSED -> "Dismissed" to SpottedTheme.colors.error
      }
  Text(
      label,
      style = jakarta(11, 14f, 700, color),
      modifier =
          Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
              .padding(horizontal = 10.dp, vertical = 4.dp)
              .testTag(MyAlertScreenTestTags.status(sighting.id)),
  )
}

/**
 * OpenStreetMap view with a numbered marker per sighting and the likely area as a circle. It reuses
 * the app's map setup ([createMapView]) and pauses the map with the screen.
 */
@Composable
private fun MyAlertMap(
    markers: List<SightingMarker>,
    likelyArea: LikelyArea?,
    center: Location,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val mapView = remember { createMapView(context) }
  DisposableEffect(mapView) {
    mapView.onResume()
    onDispose {
      mapView.onPause()
      mapView.onDetach()
    }
  }
  // Re-centre when the centre moves (a new likely area), not on every recomposition
  LaunchedEffect(center) {
    mapView.controller.setZoom(MAP_ZOOM)
    mapView.controller.setCenter(GeoPoint(center.latitude, center.longitude))
  }
  // Read here: the likely area is drawn by osmdroid, outside of Compose
  val areaColor = SpottedTheme.colors.error
  AndroidView(
      factory = { mapView },
      modifier = modifier,
      update = { map -> map.showSightings(markers, likelyArea, areaColor) },
  )
}

/** Replaces the markers and the likely-area circle. */
private fun MapView.showSightings(
    markers: List<SightingMarker>,
    likelyArea: LikelyArea?,
    areaColor: Color,
) {
  overlays.removeAll { it is Marker || it is Polygon }
  likelyArea?.let { area ->
    overlays.add(
        Polygon(this).apply {
          // In metres, so the circle keeps its real size when the user zooms
          points =
              Polygon.pointsAsCircle(
                  GeoPoint(area.center.latitude, area.center.longitude),
                  area.radiusMeters,
              )
          fillPaint.color = areaColor.copy(alpha = 0.15f).toArgb()
          outlinePaint.color = areaColor.toArgb()
          outlinePaint.strokeWidth = 4f
        }
    )
  }
  markers.forEach { marker ->
    overlays.add(
        Marker(this).apply {
          position = GeoPoint(marker.location.latitude, marker.location.longitude)
          setTextIcon("${marker.number}")
          setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
          infoWindow = null
        }
    )
  }
  invalidate()
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatPlace(location: Location): String =
    String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)

private const val MAP_ZOOM = 15.0
private const val DISMISSED_ALPHA = 0.45f
