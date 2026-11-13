// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.scantag

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.model.tag.ScannedTag
import com.android.spotted.ui.components.NfcTagScanEffect
import com.android.spotted.ui.components.SpottedBackButton
import com.android.spotted.ui.components.rememberNfcAvailability
import com.android.spotted.ui.components.rememberNfcTagReader
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta

/** Test tags of the tag-scan screen. */
object ScanTagScreenTestTags {
  const val SCREEN = "scanTag_screen"
  const val BACK_BUTTON = "scanTag_backButton"
  const val HEADER = "scanTag_header"
  const val STATUS_ICON = "scanTag_statusIcon"
  const val STATUS_TITLE = "scanTag_statusTitle"
  const val STATUS_MESSAGE = "scanTag_statusMessage"
  const val WAITING = "scanTag_waiting"
  const val LOADING = "scanTag_loading"
  const val FOUND = "scanTag_found"
  const val UNKNOWN = "scanTag_unknown"
  const val ERROR = "scanTag_error"
  const val NFC_OFF = "scanTag_nfcOff"
  const val REPORT_SIGHTING = "scanTag_reportSighting"
  const val RETRY = "scanTag_retry"
  const val CANCEL = "scanTag_cancel"
  const val OPEN_NFC_SETTINGS = "scanTag_openNfcSettings"
  const val PHONE = "scanTag_phone"
  const val EMAIL = "scanTag_email"
  const val PET_NAME = "scanTag_petName"
  const val PHOTO_PLACEHOLDER = "scanTag_photoPlaceholder"
  const val ALLERGIES = "scanTag_allergies"
  const val BEHAVIORS = "scanTag_behaviors"
}

/**
 * Collects scan state and runs NFC reading only while this screen is resumed. Android intents
 * remain in the View, while the ViewModel receives only plain scanned-tag values.
 */
@Composable
fun ScanTagScreen(
    viewModel: ScanTagViewModel = viewModel(factory = ScanTagViewModel.Factory),
    onBack: () -> Unit = {},
    onReportSighting: (alertId: String?) -> Unit = {},
) {
  val scanState by viewModel.uiState.collectAsStateWithLifecycle()
  val reader = rememberNfcTagReader()
  val nfcAvailability by rememberNfcAvailability(reader)
  NfcTagScanEffect(reader) { scannedTag: ScannedTag -> viewModel.onTagScanned(scannedTag.tagId) }

  val context = LocalContext.current
  ScanTagContent(
      state = scanState,
      nfcAvailability = nfcAvailability,
      onCall = { phone -> launchPhoneIntent(context, phone) },
      onEmail = { email -> launchEmailIntent(context, email) },
      onReportSighting = onReportSighting,
      onRetry = viewModel::resetForRetry,
      onCancel = onBack,
      onOpenNfcSettings = { openNfcSettings(context) },
  )
}

internal fun launchPhoneIntent(context: Context, phone: String) {
  startActivitySafely(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
}

internal fun launchEmailIntent(context: Context, email: String) {
  startActivitySafely(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null)))
}

internal fun openNfcSettings(context: Context) {
  if (!startActivitySafely(context, Intent(Settings.ACTION_NFC_SETTINGS))) {
    startActivitySafely(context, Intent(Settings.ACTION_WIRELESS_SETTINGS))
  }
}

private fun startActivitySafely(context: Context, intent: Intent): Boolean =
    try {
      context.startActivity(intent)
      true
    } catch (_: ActivityNotFoundException) {
      false
    }

/** Stateless tag-scan content, suitable for previews and Compose tests. */
@Composable
fun ScanTagContent(
    state: ScanTagUiState,
    nfcAvailability: NfcAvailability = NfcAvailability.AVAILABLE,
    onCall: (String) -> Unit = {},
    onEmail: (String) -> Unit = {},
    onReportSighting: (alertId: String?) -> Unit = {},
    onRetry: () -> Unit = {},
    onCancel: () -> Unit = {},
    onOpenNfcSettings: () -> Unit = {},
) {
  val nfcUnavailable = state.step == ScanTagStep.WaitingForTag || state.step == ScanTagStep.Loading
  Box(
      Modifier.fillMaxSize()
          .background(SpottedTheme.colors.screenBackground)
          .testTag(ScanTagScreenTestTags.SCREEN)
  ) {
    Image(
        painterResource(R.drawable.background_textures),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().alpha(0.42f),
    )
    Column(
        modifier =
            Modifier.fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp)
    ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().testTag(ScanTagScreenTestTags.HEADER),
      ) {
        SpottedBackButton(
            onClick = onCancel,
            testTag = ScanTagScreenTestTags.BACK_BUTTON,
        )
        Column(Modifier.padding(start = 12.dp)) {
          Text(
              "Scan a pet’s tag",
              style = jakarta(22, 27f, FontWeight.Bold, SpottedTheme.colors.ink),
          )
          Text(
              "Hold your phone near the tag on its collar",
              style = jakarta(12, 16f, FontWeight.Normal, SpottedTheme.colors.textSecondary),
          )
        }
      }

      Spacer(Modifier.height(16.dp))
      when {
        state.step == ScanTagStep.Found -> {
          FoundPetContent(
              modifier = Modifier.weight(1f),
              state = state,
              onCall = onCall,
              onEmail = onEmail,
              onReportSighting = onReportSighting,
          )
        }
        state.step == ScanTagStep.Unknown -> {
          UnknownTagContent()
          Spacer(Modifier.weight(1f))
          PrimaryAction(
              text = "Report a sighting",
              tag = ScanTagScreenTestTags.REPORT_SIGHTING,
              onClick = { onReportSighting(null) },
          )
          SecondaryAction(
              text = "Scan another tag",
              tag = ScanTagScreenTestTags.RETRY,
              onClick = onRetry,
          )
        }
        state.step == ScanTagStep.Error -> {
          ErrorContent()
          Spacer(Modifier.weight(1f))
          PrimaryAction(
              text = "Try again",
              tag = ScanTagScreenTestTags.RETRY,
              onClick = onRetry,
          )
          SecondaryAction("Cancel", ScanTagScreenTestTags.CANCEL, onCancel)
        }
        nfcUnavailable && nfcAvailability == NfcAvailability.DISABLED -> {
          NfcOffContent()
          Spacer(Modifier.weight(1f))
          PrimaryAction(
              text = "Open NFC settings",
              tag = ScanTagScreenTestTags.OPEN_NFC_SETTINGS,
              onClick = onOpenNfcSettings,
          )
          SecondaryAction("Cancel", ScanTagScreenTestTags.CANCEL, onCancel)
        }
        nfcUnavailable && nfcAvailability == NfcAvailability.UNSUPPORTED -> {
          UnsupportedNfcContent()
          Spacer(Modifier.weight(1f))
          SecondaryAction("Cancel", ScanTagScreenTestTags.CANCEL, onCancel)
        }
        state.step == ScanTagStep.Loading -> {
          LoadingContent()
          Spacer(Modifier.weight(1f))
          SecondaryAction("Cancel", ScanTagScreenTestTags.CANCEL, onCancel)
        }
        else -> {
          WaitingContent(onReportSighting)
          Spacer(Modifier.weight(1f))
          SecondaryAction("Cancel", ScanTagScreenTestTags.CANCEL, onCancel)
        }
      }
    }
  }
}

@Composable
private fun WaitingContent(onReportSighting: (String?) -> Unit) {
  Column {
    StatusCard(
        icon = "NFC",
        title = "Hold your phone near the tag",
        message = "Usually on the collar. The back of the phone, near the camera, reads best.",
        tag = ScanTagScreenTestTags.WAITING,
    )
    TextButton(
        onClick = { onReportSighting(null) },
        modifier =
            Modifier.padding(top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SpottedTheme.colors.blueTint)
                .testTag(ScanTagScreenTestTags.REPORT_SIGHTING),
    ) {
      Text(
          "Found a pet without a tag? Report a sighting instead.",
          textAlign = TextAlign.Center,
          style = jakarta(12, 16f, FontWeight.Medium, SpottedTheme.colors.blue),
      )
    }
  }
}

@Composable
private fun UnknownTagContent() {
  StatusCard(
      icon = "?",
      title = "This tag isn’t linked to a pet",
      message =
          "It may belong to another app, or its owner unlinked it. You can still help by reporting a sighting.",
      tag = ScanTagScreenTestTags.UNKNOWN,
      iconColor = SpottedTheme.colors.textMuted,
      iconHalo = SpottedTheme.colors.inputBackground,
  )
}

@Composable
private fun NfcOffContent() {
  StatusCard(
      icon = "NFC",
      title = "NFC is off",
      message = "Turn on NFC in your phone’s settings to read pet tags. It uses almost no battery.",
      tag = ScanTagScreenTestTags.NFC_OFF,
      iconColor = SpottedTheme.colors.textMuted,
      iconHalo = SpottedTheme.colors.inputBackground,
  )
}

@Composable
private fun UnsupportedNfcContent() {
  StatusCard(
      icon = "NFC",
      title = "NFC isn’t available",
      message = "This phone can’t read NFC pet tags. You can still help by reporting a sighting.",
      tag = ScanTagScreenTestTags.NFC_OFF,
      iconColor = SpottedTheme.colors.textMuted,
      iconHalo = SpottedTheme.colors.inputBackground,
  )
}

@Composable
private fun LoadingContent() {
  StatusCard(
      icon = "",
      title = "Checking this tag",
      message = "Please wait while we look up this pet.",
      tag = ScanTagScreenTestTags.LOADING,
      iconContent = {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = SpottedTheme.colors.blue,
            strokeWidth = 3.dp,
        )
      },
  )
}

@Composable
private fun ErrorContent() {
  StatusCard(
      icon = "!",
      title = "We couldn’t load this tag",
      message = "Check your connection and try again.",
      tag = ScanTagScreenTestTags.ERROR,
      iconColor = SpottedTheme.colors.textMuted,
      iconHalo = SpottedTheme.colors.inputBackground,
  )
}

@Composable
private fun StatusCard(
    icon: String,
    title: String,
    message: String,
    tag: String,
    iconColor: Color = SpottedTheme.colors.blue,
    iconHalo: Color = SpottedTheme.colors.blueTint,
    iconContent: (@Composable () -> Unit)? = null,
) {
  Card(
      modifier = Modifier.fillMaxWidth().testTag(tag),
      shape = RoundedCornerShape(28.dp),
      colors = CardDefaults.cardColors(containerColor = SpottedTheme.colors.surface),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
          Modifier.size(88.dp).clip(CircleShape).background(iconHalo),
          contentAlignment = Alignment.Center,
      ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(iconColor),
            contentAlignment = Alignment.Center,
        ) {
          if (iconContent != null) {
            iconContent()
          } else {
            Text(
                icon,
                style = jakarta(18, 22f, FontWeight.Bold, Color.White),
                modifier = Modifier.testTag(ScanTagScreenTestTags.STATUS_ICON),
            )
          }
        }
      }
      Spacer(Modifier.height(14.dp))
      Text(
          title,
          textAlign = TextAlign.Center,
          style = jakarta(16, 20f, FontWeight.Bold, SpottedTheme.colors.ink),
          modifier = Modifier.testTag(ScanTagScreenTestTags.STATUS_TITLE),
      )
      Spacer(Modifier.height(8.dp))
      Text(
          message,
          textAlign = TextAlign.Center,
          style = jakarta(13, 18f, FontWeight.Normal, SpottedTheme.colors.textSecondary),
          modifier = Modifier.testTag(ScanTagScreenTestTags.STATUS_MESSAGE),
      )
      if (tag == ScanTagScreenTestTags.WAITING) {
        Text(
            "Waiting for a tag…",
            modifier =
                Modifier.padding(top = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SpottedTheme.colors.inputBackground)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            style = jakarta(12, 16f, FontWeight.Medium, SpottedTheme.colors.textSecondary),
        )
      }
    }
  }
}

@Composable
private fun FoundPetContent(
    modifier: Modifier,
    state: ScanTagUiState,
    onCall: (String) -> Unit,
    onEmail: (String) -> Unit,
    onReportSighting: (String?) -> Unit,
) {
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .testTag(ScanTagScreenTestTags.FOUND),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (state.photoUrl != null) {
      AsyncImage(
          model = state.photoUrl,
          contentDescription = state.petName?.let { "$it’s photo" } ?: "Pet photo",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp)),
      )
    } else {
      Box(
          Modifier.fillMaxWidth()
              .height(150.dp)
              .clip(RoundedCornerShape(24.dp))
              .background(SpottedTheme.colors.featuredPhotoBackground),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            "Photo unavailable",
            style = jakarta(24, 30f, FontWeight.Bold, SpottedTheme.colors.ink),
            modifier = Modifier.testTag(ScanTagScreenTestTags.PHOTO_PLACEHOLDER),
        )
      }
    }
    Text(
        state.petName ?: "Pet found",
        style = jakarta(24, 30f, FontWeight.Bold, SpottedTheme.colors.ink),
        modifier = Modifier.testTag(ScanTagScreenTestTags.PET_NAME),
    )
    state.species?.let {
      Text(
          it.label(),
          style = jakarta(14, 18f, FontWeight.Normal, SpottedTheme.colors.textSecondary),
      )
    }
    if (state.allergies.isNotEmpty()) {
      DetailHighlight(
          title = "Allergies",
          items = state.allergies,
          tag = ScanTagScreenTestTags.ALLERGIES,
      )
    }
    if (state.behaviors.isNotEmpty()) {
      DetailHighlight(
          title = "Behaviours",
          items = state.behaviors.map(Behavior::label),
          tag = ScanTagScreenTestTags.BEHAVIORS,
      )
    }

    if (state.visiblePhone != null || state.visibleEmail != null) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        state.visiblePhone?.let { phone ->
          OutlinedButton(
              onClick = { onCall(phone) },
              modifier = Modifier.weight(1f).testTag(ScanTagScreenTestTags.PHONE),
          ) {
            Text("Call")
          }
        }
        state.visibleEmail?.let { email ->
          OutlinedButton(
              onClick = { onEmail(email) },
              modifier = Modifier.weight(1f).testTag(ScanTagScreenTestTags.EMAIL),
          ) {
            Text("Email")
          }
        }
      }
    } else {
      Text(
          "No contact details are available for this pet.",
          style = jakarta(13, 18f, FontWeight.Normal, SpottedTheme.colors.textSecondary),
      )
    }

    if (state.openAlertId != null) {
      Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = SpottedTheme.colors.blueTint),
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
              "This pet has been reported lost.",
              style = jakarta(14, 18f, FontWeight.Medium, SpottedTheme.colors.blue),
          )
          Button(
              onClick = { onReportSighting(state.openAlertId) },
              modifier = Modifier.fillMaxWidth().testTag(ScanTagScreenTestTags.REPORT_SIGHTING),
          ) {
            Text("Report a sighting")
          }
        }
      }
    }
  }
}

@Composable
private fun DetailHighlight(title: String, items: List<String>, tag: String) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(SpottedTheme.colors.warningTint)
              .padding(14.dp)
              .semantics(mergeDescendants = true) {}
              .testTag(tag),
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(title, style = jakarta(13, 17f, FontWeight.Bold, SpottedTheme.colors.ink))
    Text(
        items.joinToString(" · "),
        style = jakarta(13, 18f, FontWeight.Medium, SpottedTheme.colors.ink),
    )
  }
}

@Composable
private fun PrimaryAction(text: String, tag: String, onClick: () -> Unit) {
  Button(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag(tag),
      shape = RoundedCornerShape(14.dp),
      colors = ButtonDefaults.buttonColors(containerColor = SpottedTheme.colors.blue),
  ) {
    Text(text, style = jakarta(14, 18f, FontWeight.SemiBold, Color.White))
  }
}

@Composable
private fun SecondaryAction(text: String, tag: String, onClick: () -> Unit) {
  TextButton(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag(tag),
  ) {
    Text(text, style = jakarta(14, 18f, FontWeight.SemiBold, SpottedTheme.colors.ink))
  }
}

private fun Species.label(): String =
    when (this) {
      Species.DOG -> "Dog"
      Species.CAT -> "Cat"
      Species.OTHER -> "Other"
    }

private fun Behavior.label(): String =
    when (this) {
      Behavior.STRESSED_IN_CROWDS -> "Stressed in crowds"
      Behavior.DOESNT_LIKE_BEING_TOUCHED -> "Doesn’t like being touched"
      Behavior.REACTIVE_TO_OTHER_ANIMALS -> "Reactive to other animals"
      Behavior.FEARFUL_OF_STRANGERS -> "Fearful of strangers"
    }

@Preview(name = "16c · Waiting · light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ScanTagWaitingLightPreview() {
  ScanTagPreview(state = ScanTagUiState(), darkTheme = false)
}

@Preview(
    name = "16c · Waiting · dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun ScanTagWaitingDarkPreview() {
  ScanTagPreview(state = ScanTagUiState(), darkTheme = true)
}

@Preview(name = "16d · Unlinked · light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ScanTagUnknownLightPreview() {
  ScanTagPreview(state = ScanTagUiState(step = ScanTagStep.Unknown), darkTheme = false)
}

@Preview(
    name = "16d · Unlinked · dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun ScanTagUnknownDarkPreview() {
  ScanTagPreview(state = ScanTagUiState(step = ScanTagStep.Unknown), darkTheme = true)
}

@Preview(
    name = "16e · NFC off · dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun ScanTagNfcOffDarkPreview() {
  ScanTagPreview(
      state = ScanTagUiState(),
      nfcAvailability = NfcAvailability.DISABLED,
      darkTheme = true,
  )
}

@Composable
private fun ScanTagPreview(
    state: ScanTagUiState,
    nfcAvailability: NfcAvailability = NfcAvailability.AVAILABLE,
    darkTheme: Boolean,
) {
  SpottedTheme(darkTheme = darkTheme) {
    ScanTagContent(state = state, nfcAvailability = nfcAvailability)
  }
}
