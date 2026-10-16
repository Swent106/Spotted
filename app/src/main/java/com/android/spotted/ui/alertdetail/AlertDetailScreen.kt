// Portions of this code were generated with the help of GitHub Copilot.
package com.android.spotted.ui.alertdetail

import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.ui.theme.jakarta
import java.util.Locale

object AlertDetailTestTags {
  const val SCREEN = "alertDetail_screen"
  const val LOADING = "alertDetail_loading"
  const val ERROR = "alertDetail_error"
  const val NOT_FOUND = "alertDetail_notFound"
  const val PHOTO = "alertDetail_photo"
  const val PHOTO_PLACEHOLDER = "alertDetail_photoPlaceholder"
  const val NAME = "alertDetail_name"
  const val SPECIES = "alertDetail_species"
  const val LOST_AT = "alertDetail_lostAt"
  const val MAP = "alertDetail_map"
  const val LOCATION = "alertDetail_location"
  const val ALLERGIES = "alertDetail_allergies"
  const val ALLERGY_PREFIX = "alertDetail_allergy_"
  const val BEHAVIORS = "alertDetail_behaviors"
  const val BEHAVIOR_PREFIX = "alertDetail_behavior_"
  const val REPORT_SIGHTING = "alertDetail_reportSighting"
  const val BACK = "alertDetail_back"
  const val SHARE = "alertDetail_share"
}

@Composable
fun AlertDetailScreen(
    viewModel: AlertDetailViewModel,
    alertId: String,
    onReportSighting: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
  LaunchedEffect(alertId) { viewModel.loadAlert(alertId) }
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  AlertDetailContent(
      state = state,
      onReportSighting = onReportSighting,
      modifier = modifier,
      onBack = onBack,
      // The system share sheet, launched from the View
      onShare = { alert ->
        val text =
            "${alert.petName} is missing near ${locationCoordinates(alert.lastKnownLocation)}." +
                " Have you seen them? Open Spotted to help."
        val send =
            Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_TEXT, text)
            }
        context.startActivity(Intent.createChooser(send, null))
      },
  )
}

@Composable
fun AlertDetailContent(
    state: AlertDetailUiState,
    onReportSighting: () -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onShare: (Alert) -> Unit = {},
) {
  when {
    state.isLoading || (state.alert == null && !state.notFound && state.errorMessage == null) ->
        Box(
            modifier = modifier.fillMaxSize().testTag(AlertDetailTestTags.SCREEN),
            contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(modifier = Modifier.testTag(AlertDetailTestTags.LOADING))
        }
    state.errorMessage != null ->
        Box(
            modifier = modifier.fillMaxSize().testTag(AlertDetailTestTags.SCREEN),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = state.errorMessage,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(24.dp).testTag(AlertDetailTestTags.ERROR),
          )
        }
    state.notFound ->
        Box(
            modifier = modifier.fillMaxSize().testTag(AlertDetailTestTags.SCREEN),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = "This alert is no longer available.",
              modifier = Modifier.padding(24.dp).testTag(AlertDetailTestTags.NOT_FOUND),
          )
        }
    state.alert != null ->
        AlertDetails(
            alert = state.alert,
            onReportSighting = onReportSighting,
            onBack = onBack,
            onShare = { onShare(state.alert) },
            modifier = modifier,
        )
  }
}

@Composable
private fun AlertDetails(
    alert: Alert,
    onReportSighting: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier.fillMaxSize().background(Color(0xFFF5F4F0)).testTag(AlertDetailTestTags.SCREEN),
  ) {
    PetPhotoHeader(alert = alert, onBack = onBack, onShare = onShare)
    Column(
        modifier =
            Modifier.weight(1f)
                .offset(y = (-18).dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      PetSummary(alert)
      PetWarnings(alert)
      LastSeenArea(alert.lastKnownLocation)
    }
    ReportSightingBar(onClick = onReportSighting)
  }
}

@Composable
private fun PetPhotoHeader(alert: Alert, onBack: () -> Unit, onShare: () -> Unit) {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .height(255.dp)
              .background(
                  Brush.verticalGradient(
                      listOf(Color(0xFFA88D71), Color(0xFFE3B88F)),
                  )
              ),
  ) {
    if (alert.petPhotoUrl != null) {
      AsyncImage(
          model = alert.petPhotoUrl,
          contentDescription = "Photo of ${alert.petName}",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize().testTag(AlertDetailTestTags.PHOTO),
      )
    } else {
      Text(
          "[ PHOTO OF THE PET ]",
          style = jakarta(13, 18f, 700, 0xFFFFFFFF),
          modifier =
              Modifier.align(Alignment.Center).testTag(AlertDetailTestTags.PHOTO_PLACEHOLDER),
      )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      HeaderCircleButton(onClick = onBack, tag = AlertDetailTestTags.BACK)
      HeaderCircleButton("↗", onClick = onShare, tag = AlertDetailTestTags.SHARE)
    }
    Row(
        modifier =
            Modifier.align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 30.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(Color(0xFFC92C2C))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
      Spacer(Modifier.width(6.dp))
      Text(
          "Missing · ${timeSinceLost(alert.lostAtMillis).removeSuffix(" ago")}",
          style = jakarta(11, 14f, 700, 0xFFFFFFFF),
      )
    }
  }
}

@Composable
private fun HeaderCircleButton(symbol: String? = null, onClick: () -> Unit, tag: String) {
  Box(
      modifier =
          Modifier.size(38.dp)
              .clip(CircleShape)
              .background(Color.White)
              .clickable(
                  onClickLabel = if (symbol == null) "Back" else "Share",
                  role = Role.Button,
                  onClick = onClick,
              )
              .testTag(tag),
      contentAlignment = Alignment.Center,
  ) {
    if (symbol == null) {
      Icon(
          painter = painterResource(R.drawable.ic_arrow_back),
          contentDescription = "Back",
          tint = Color(0xFF14161A),
          modifier = Modifier.size(20.dp),
      )
    } else {
      Text(symbol, style = jakarta(22, 26f, 600, 0xFF14161A))
    }
  }
}

@Composable
private fun PetSummary(alert: Alert) {
  Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          alert.petName,
          style = jakarta(24, 29f, 700, 0xFF14161A),
          modifier = Modifier.testTag(AlertDetailTestTags.NAME),
      )
      Spacer(Modifier.width(10.dp))
      Text(
          alert.petSpecies.displayName(),
          modifier =
              Modifier.clip(RoundedCornerShape(50.dp))
                  .background(Color(0xFFF0F2F5))
                  .padding(horizontal = 11.dp, vertical = 5.dp)
                  .testTag(AlertDetailTestTags.SPECIES),
          style = jakarta(11, 15f, 600, 0xFF394150),
      )
    }
    Text(
        "Lost ${timeSinceLost(alert.lostAtMillis)} · ${locationCoordinates(alert.lastKnownLocation)}",
        style = jakarta(12, 17f, 600, 0xFFC92C2C),
        modifier = Modifier.testTag(AlertDetailTestTags.LOST_AT),
    )
  }
}

@Composable
private fun PetWarnings(alert: Alert) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        "IF YOU FIND ${alert.petName.uppercase()}",
        style = jakarta(11, 14f, 600, 0xFF626975),
    )
    alert.petBehaviors
        .sortedBy { it.displayName() }
        .forEachIndexed { index, behavior ->
          WarningCard(
              title = behavior.displayName(),
              detail = behavior.warningDetail(),
              warning = true,
              valueTag = "${AlertDetailTestTags.BEHAVIOR_PREFIX}$index",
          )
        }
    if (alert.petBehaviors.isEmpty()) {
      Text("None listed", style = jakarta(11, 15f, 500, 0xFF69717C))
    }
    alert.petAllergies.forEachIndexed { index, allergy ->
      WarningCard(
          title = "Allergic to",
          detail = "Please don't give this pet food containing ${allergy.lowercase()}.",
          warning = false,
          emphasis = allergy.replaceFirstChar(Char::titlecase),
          valueTag = "${AlertDetailTestTags.ALLERGY_PREFIX}$index",
      )
    }
    if (alert.petAllergies.isEmpty()) {
      Text("None listed", style = jakarta(11, 15f, 500, 0xFF69717C))
    }
  }
}

@Composable
private fun WarningCard(
    title: String,
    detail: String,
    warning: Boolean,
    emphasis: String? = null,
    valueTag: String? = null,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(if (warning) Color(0xFFFFF2DE) else Color(0xFFFCE9E9))
              .padding(horizontal = 13.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        modifier =
            Modifier.size(30.dp)
                .clip(CircleShape)
                .background(if (warning) Color(0xFFFFFBF5) else Color(0xFFFFF7F7)),
        contentAlignment = Alignment.Center,
    ) {
      Text(
          if (warning) "!" else "⊘",
          style =
              jakarta(
                  17,
                  20f,
                  700,
                  if (warning) 0xFF9A5A00 else 0xFFC92C2C,
              ),
      )
    }
    Spacer(Modifier.width(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = jakarta(13, 17f, 700, if (warning) 0xFF875000 else 0xFFB82222),
            modifier =
                if (emphasis == null && valueTag != null) Modifier.testTag(valueTag) else Modifier,
        )
        if (emphasis != null) {
          Spacer(Modifier.width(3.dp))
          Text(
              emphasis,
              style = jakarta(13, 17f, 700, if (warning) 0xFF875000 else 0xFFB82222),
              modifier = valueTag?.let(Modifier::testTag) ?: Modifier,
          )
        }
      }
      Text(
          detail,
          style = jakarta(11, 15f, 500, 0xFF69717C),
      )
    }
  }
}

@Composable
private fun LastSeenArea(location: Location) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("LAST SEEN AREA", style = jakarta(11, 14f, 600, 0xFF626975))
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(144.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color(0xFFE8EAE4))
                .testTag(AlertDetailTestTags.MAP),
    ) {
      Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.5f, size.height * 0.43f)
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color(0x55D93025), Color(0x00D93025)),
                    center = center,
                    radius = size.minDimension * 0.4f,
                ),
            radius = size.minDimension * 0.4f,
            center = center,
        )
        drawCircle(
            color = Color(0xFFD93025),
            radius = 12.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
      }
      Text(
          "[ MAP ]",
          modifier = Modifier.align(Alignment.TopStart).padding(11.dp),
          style = jakarta(9, 12f, 700, 0xFF626975),
      )
      Column(
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .fillMaxWidth()
                  .background(Color(0xFFF5F6F7).copy(alpha = 0.96f))
                  .padding(horizontal = 13.dp, vertical = 9.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
            "Around last known location",
            style = jakarta(12, 16f, 700, 0xFF14161A),
        )
        Text(
            locationCoordinates(location),
            style = jakarta(10, 14f, 500, 0xFF626975),
            modifier = Modifier.testTag(AlertDetailTestTags.LOCATION),
        )
      }
    }
  }
}

@Composable
private fun ReportSightingBar(onClick: () -> Unit) {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .background(Color.White)
              .padding(horizontal = 20.dp, vertical = 12.dp),
  ) {
    Button(
        onClick = onClick,
        modifier =
            Modifier.fillMaxWidth().height(48.dp).testTag(AlertDetailTestTags.REPORT_SIGHTING),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5BD7)),
    ) {
      Text("I have seen this pet", fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
  }
}

private fun locationCoordinates(location: Location) =
    "%.4f, %.4f".format(Locale.US, location.latitude, location.longitude)

private fun timeSinceLost(timestampMillis: Long): String {
  val minutes = ((System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L) / 60_000L)
  return when {
    minutes < 1 -> "just now"
    minutes < 60 -> "$minutes min ago"
    minutes < 24 * 60 -> "${minutes / 60}h ago"
    else -> "${minutes / (24 * 60)}d ago"
  }
}

private fun Species.displayName() =
    when (this) {
      Species.DOG -> "Dog"
      Species.CAT -> "Cat"
      Species.OTHER -> "Other"
    }

private fun Behavior.displayName() =
    when (this) {
      Behavior.STRESSED_IN_CROWDS -> "Stressed in crowds"
      Behavior.DOESNT_LIKE_BEING_TOUCHED -> "Doesn't like being touched"
      Behavior.REACTIVE_TO_OTHER_ANIMALS -> "Reactive to other animals"
      Behavior.FEARFUL_OF_STRANGERS -> "Fearful of strangers"
    }

private fun Behavior.warningDetail() =
    when (this) {
      Behavior.STRESSED_IN_CROWDS -> "Approach calmly and give them room."
      Behavior.DOESNT_LIKE_BEING_TOUCHED -> "Please don't try to pet or pick them up."
      Behavior.REACTIVE_TO_OTHER_ANIMALS -> "Keep some distance from other animals."
      Behavior.FEARFUL_OF_STRANGERS -> "Crouch, call their name softly, don't grab them."
    }

@Preview(showBackground = true, name = "Alert detail", widthDp = 360, heightDp = 775)
@Composable
private fun AlertDetailScreenPreview() {
  MaterialTheme {
    AlertDetailContent(
        state =
            AlertDetailUiState(
                alert =
                    Alert(
                        id = "preview-alert",
                        petId = "preview-pet",
                        ownerId = "preview-owner",
                        lastKnownLocation = Location(46.5197, 6.6323),
                        lostAtMillis = 1_700_000_000_000L,
                        petName = "Luna",
                        petSpecies = Species.CAT,
                        petAllergies = listOf("Chicken", "Dairy"),
                        petBehaviors =
                            setOf(
                                Behavior.FEARFUL_OF_STRANGERS,
                                Behavior.DOESNT_LIKE_BEING_TOUCHED,
                            ),
                    ),
            ),
    )
  }
}
