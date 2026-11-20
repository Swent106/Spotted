// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.model.Pet.Species
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "N sightings waiting to send", from the Figma frame "05b · Home · Sighting waiting to send":
 * under "Your pets" while the queue is not empty. Tapping it opens the "Waiting to send" sheet.
 */
internal fun LazyListScope.waitingBanner(state: HomeUiState, onClick: () -> Unit) {
  val count = state.waitingSightings.size
  if (count == 0) return
  item(key = "waitingBanner") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier.padding(top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SpottedTheme.colors.warningTint)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag(HomeScreenTestTags.WAITING_BANNER),
    ) {
      Icon(
          painterResource(R.drawable.ic_cloud_off),
          contentDescription = null,
          tint = SpottedTheme.colors.warning,
          modifier = Modifier.size(22.dp),
      )
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            pluralStringResource(R.plurals.home_waiting_banner_title, count, count),
            style = jakarta(13, 16f, 700, SpottedTheme.colors.warning),
        )
        Text(
            stringResource(R.string.home_waiting_banner_text),
            style = jakarta(12, 15f, 500, SpottedTheme.colors.warning),
        )
      }
      // The back chevron, turned to point right
      Icon(
          painterResource(R.drawable.ic_chevron_left),
          contentDescription = null,
          tint = SpottedTheme.colors.warning,
          modifier = Modifier.size(18.dp).rotate(180f),
      )
    }
  }
}

/**
 * "Waiting to send", from the Figma frame "05c · Home · Waiting to send, delete": one row per
 * waiting sighting, each with Delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WaitingSightingsSheet(
    sightings: List<WaitingSighting>,
    onDeleteClick: (sightingId: String) -> Unit,
    onClose: () -> Unit,
) {
  ModalBottomSheet(
      onDismissRequest = onClose,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = SpottedTheme.colors.surface,
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
      dragHandle = {
        Box(
            Modifier.padding(top = 12.dp)
                .size(width = 40.dp, height = 4.dp)
                .background(SpottedTheme.colors.buttonBorder, RoundedCornerShape(2.dp))
        )
      },
  ) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 34.dp)
                .testTag(HomeScreenTestTags.WAITING_SHEET),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.home_waiting_sheet_title),
            style = jakarta(20, 25f, 800, SpottedTheme.colors.ink),
        )
        Text(
            stringResource(R.string.home_waiting_sheet_text),
            style = jakarta(13, 16f, 500, SpottedTheme.colors.textSecondary),
        )
      }
      sightings.forEach { sighting -> WaitingRow(sighting, onDeleteClick) }
      GreyButton(
          stringResource(R.string.home_waiting_close),
          onClose,
          HomeScreenTestTags.WAITING_CLOSE,
      )
    }
  }
}

@Composable
private fun WaitingRow(sighting: WaitingSighting, onDeleteClick: (sightingId: String) -> Unit) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(SpottedTheme.colors.inputBackground)
              .padding(horizontal = 14.dp, vertical = 12.dp)
              .testTag(HomeScreenTestTags.waitingRow(sighting.id)),
  ) {
    val title = sighting.petName ?: speciesName(sighting.species)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SpottedTheme.colors.featuredPhotoBackground),
    ) {
      if (sighting.photoUri != null) {
        AsyncImage(
            model = sighting.photoUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
      } else {
        Text(title.take(1), style = jakarta(16, 20f, 700, SpottedTheme.colors.ink))
      }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
          title,
          style = jakarta(14, 18f, 700, SpottedTheme.colors.ink),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          stringResource(
              R.string.home_waiting_saved,
              waitingTime(sighting.savedAtMillis),
              sighting.place,
          ),
          style = jakarta(12, 15f, 500, SpottedTheme.colors.textSecondary),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
        stringResource(R.string.home_waiting_delete),
        style = jakarta(13, 16f, 700, SpottedTheme.colors.lostRed),
        modifier =
            Modifier.clip(RoundedCornerShape(100.dp))
                .background(SpottedTheme.colors.errorTint)
                .clickable { onDeleteClick(sighting.id) }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(HomeScreenTestTags.waitingDelete(sighting.id)),
    )
  }
}

/** "Delete this sighting?", from the Figma frame "05d · Home · Confirm delete waiting sighting". */
@Composable
internal fun DeleteWaitingDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
  Dialog(onDismissRequest = onCancel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier.fillMaxWidth()
                .background(SpottedTheme.colors.surface, RoundedCornerShape(24.dp))
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp)
                .testTag(HomeScreenTestTags.DELETE_DIALOG),
    ) {
      Text(
          stringResource(R.string.home_waiting_delete_title),
          style = jakarta(20, 25f, 800, SpottedTheme.colors.ink),
      )
      Text(
          stringResource(R.string.home_waiting_delete_text),
          style = jakarta(13, 18f, 500, SpottedTheme.colors.textSecondary),
      )
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.fillMaxWidth()
                  .height(49.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(SpottedTheme.colors.lostRed)
                  .clickable(onClick = onConfirm)
                  .testTag(HomeScreenTestTags.DELETE_CONFIRM),
      ) {
        Text(
            stringResource(R.string.home_waiting_delete),
            style = jakarta(15, 19f, 700, SpottedTheme.colors.onAccent),
        )
      }
      GreyButton(
          stringResource(R.string.home_waiting_keep),
          onCancel,
          HomeScreenTestTags.DELETE_CANCEL,
      )
    }
  }
}

/** The light grey full-width button of the sheet and the dialog ("Close", "Keep it"). */
@Composable
private fun GreyButton(text: String, onClick: () -> Unit, tag: String) {
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.fillMaxWidth()
              .height(49.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(SpottedTheme.colors.inputBackground)
              .clickable(onClick = onClick)
              .testTag(tag),
  ) {
    Text(text, style = jakarta(15, 19f, 700, SpottedTheme.colors.ink))
  }
}

/** A snackbar shown as "Your sighting of Milo was sent", with its check mark. */
internal class SentSnackbarVisuals(override val message: String) : SnackbarVisuals {
  override val actionLabel: String? = null
  override val withDismissAction: Boolean = false
  override val duration: SnackbarDuration = SnackbarDuration.Short
}

/** "Your sighting of Milo was sent", from the Figma frame "05e · Home · Sighting sent". */
@Composable
internal fun SentSnackbar(message: String) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier =
          Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
              .fillMaxWidth()
              .shadow(16.dp, RoundedCornerShape(14.dp))
              .background(SpottedTheme.colors.ink, RoundedCornerShape(14.dp))
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag(HomeScreenTestTags.SENT_SNACKBAR),
  ) {
    CheckCircle()
    Text(
        message,
        style = jakarta(14, 18f, 600, SpottedTheme.colors.surface, TextAlign.Start),
        modifier = Modifier.weight(1f),
    )
  }
}

/** The green "icon/check" of the snackbar. */
@Composable
private fun CheckCircle() {
  // Read before drawing: the Canvas lambda is not composable
  val circle = SpottedTheme.colors.success
  val tick = SpottedTheme.colors.onAccent
  Canvas(Modifier.size(20.dp)) {
    val unit = size.width / 20f
    drawCircle(circle, radius = 9f * unit)
    val path =
        Path().apply {
          moveTo(6f * unit, 10.2f * unit)
          lineTo(8.7f * unit, 12.8f * unit)
          lineTo(14f * unit, 7.5f * unit)
        }
    drawPath(
        path,
        tick,
        style = Stroke(width = 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}

/** "Your sighting of Milo was sent", or without a name when its alert is unknown. */
@Composable
internal fun sentMessage(sighting: WaitingSighting): String =
    sighting.petName?.let { stringResource(R.string.home_sighting_sent_pet, it) }
        ?: stringResource(R.string.home_sighting_sent)

/** "Dog", "Cat", "Other". */
internal fun speciesName(species: Species): String =
    species.name.lowercase().replaceFirstChar(Char::titlecase)

/** "14:32": the time of [millis], in the phone's time zone. */
internal fun waitingTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
