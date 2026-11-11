// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.linktag

import androidx.annotation.StringRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.spotted.R
import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.ui.addpets.FormCard
import com.android.spotted.ui.addpets.SectionLabel
import com.android.spotted.ui.editpet.PetScreenHeader
import com.android.spotted.ui.petprofile.TexturedBackground
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta

object LinkTagScreenTestTags {
  const val BACK_BUTTON = "link_tag_back_button"
  const val STATUS_ICON = "link_tag_status_icon"
  const val STATUS_TITLE = "link_tag_status_title"
  const val STATUS_CHIP = "link_tag_status_chip"
  const val ERROR_MESSAGE = "link_tag_error_message"
  const val PHONE_SWITCH = "link_tag_phone_switch"
  const val EMAIL_SWITCH = "link_tag_email_switch"
  const val ADDRESS_NOTE = "link_tag_address_note"
  const val NFC_MESSAGE = "link_tag_nfc_message"
  const val NFC_SETTINGS_BUTTON = "link_tag_nfc_settings_button"
  const val START_BUTTON = "link_tag_start_button"
  const val RETRY_BUTTON = "link_tag_retry_button"
  const val DONE_BUTTON = "link_tag_done_button"
  const val CANCEL_BUTTON = "link_tag_cancel_button"
}

/**
 * Stateless link tag screen. One layout for every [LinkTagUiState.step]: a status card on top, the
 * contacts finders can see (editable only before starting or after an error), then the step's
 * buttons. When [nfcAvailability] is not available, says so and offers the NFC settings.
 */
@Composable
fun LinkTagContent(
    state: LinkTagUiState,
    nfcAvailability: NfcAvailability,
    onShowPhoneChange: (Boolean) -> Unit,
    onShowEmailChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val nfcReady = nfcAvailability == NfcAvailability.AVAILABLE
  Box(modifier.fillMaxSize().background(SpottedTheme.colors.screenBackground)) {
    TexturedBackground()
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, top = 52.dp, end = 20.dp, bottom = 34.dp)
    ) {
      Column(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
      ) {
        PetScreenHeader(
            title =
                if (state.petName.isBlank()) stringResource(R.string.link_tag_title_no_pet)
                else stringResource(R.string.link_tag_title, state.petName),
            subtitle = subtitle(state),
            backTag = LinkTagScreenTestTags.BACK_BUTTON,
            onBack = if (state.step == LinkTagStep.Success) onDone else onCancel,
        )
        StatusCard(state)
        if (!nfcReady && state.step in NFC_STEPS) {
          NfcNotice(nfcAvailability, onOpenNfcSettings)
        }
        ContactsCard(state, onShowPhoneChange, onShowEmailChange)
        Text(
            stringResource(R.string.link_tag_address_note),
            style = jakarta(12, 16f, 600, SpottedTheme.colors.warning),
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpottedTheme.colors.warningTint)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag(LinkTagScreenTestTags.ADDRESS_NOTE),
        )
      }
      Spacer(Modifier.height(12.dp))
      Footer(state, nfcReady, onStart, onRetry, onDone, onCancel)
    }
  }
}

/** The steps where the owner can still start or is waiting for a tap, so NFC must be on. */
private val NFC_STEPS = setOf(LinkTagStep.Idle, LinkTagStep.WaitingForTag, LinkTagStep.Error)

@Composable
private fun subtitle(state: LinkTagUiState): String =
    when (state.step) {
      LinkTagStep.Idle,
      LinkTagStep.WaitingForTag -> stringResource(R.string.link_tag_subtitle_choose)
      LinkTagStep.Writing -> stringResource(R.string.link_tag_subtitle_writing)
      LinkTagStep.Error -> stringResource(R.string.link_tag_subtitle_error)
      LinkTagStep.Success -> stringResource(R.string.link_tag_subtitle_success, state.petName)
    }

@Composable
private fun StatusCard(state: LinkTagUiState) {
  val colors = SpottedTheme.colors
  val successTint = colors.success.copy(alpha = 0.14f)
  FormCard(spacing = 12.dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
      when (state.step) {
        LinkTagStep.Idle,
        LinkTagStep.WaitingForTag ->
            StatusIcon(stringResource(R.string.link_tag_icon_nfc), colors.blueTint, colors.blue)
        LinkTagStep.Writing -> StatusIcon("…", colors.blueTint, colors.blue)
        LinkTagStep.Error -> StatusIcon("!", colors.errorTint, colors.error)
        LinkTagStep.Success -> StatusIcon("✓", successTint, colors.success)
      }
      Text(
          when (state.step) {
            LinkTagStep.Idle,
            LinkTagStep.WaitingForTag -> stringResource(R.string.link_tag_status_hold)
            LinkTagStep.Writing -> stringResource(R.string.link_tag_status_writing, state.petName)
            LinkTagStep.Error -> stringResource(R.string.link_tag_status_error)
            LinkTagStep.Success -> stringResource(R.string.link_tag_status_success, state.petName)
          },
          style = jakarta(17, 22f, 700, colors.ink, TextAlign.Center),
          modifier = Modifier.testTag(LinkTagScreenTestTags.STATUS_TITLE),
      )
      when (state.step) {
        LinkTagStep.Idle -> Unit
        LinkTagStep.WaitingForTag ->
            StatusChip(R.string.link_tag_chip_waiting, colors.inputBackground, colors.textSecondary)
        LinkTagStep.Writing ->
            StatusChip(R.string.link_tag_chip_writing, colors.blueTint, colors.blue)
        LinkTagStep.Success ->
            StatusChip(R.string.link_tag_chip_linked, successTint, colors.success)
        LinkTagStep.Error ->
            state.errorMessage?.let {
              Text(
                  stringResource(it),
                  style = jakarta(13, 18f, 500, colors.textSecondary, TextAlign.Center),
                  modifier = Modifier.testTag(LinkTagScreenTestTags.ERROR_MESSAGE),
              )
            }
      }
    }
  }
}

/** The round icon of the status card: [label] in a filled circle inside a pale ring. */
@Composable
private fun StatusIcon(label: String, ring: Color, fill: Color) {
  Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.size(88.dp).clip(CircleShape).background(ring),
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(52.dp)
                .clip(CircleShape)
                .background(fill)
                .testTag(LinkTagScreenTestTags.STATUS_ICON),
    ) {
      Text(label, style = jakarta(14, 18f, 800, SpottedTheme.colors.onAccent))
    }
  }
}

@Composable
private fun StatusChip(@StringRes text: Int, background: Color, content: Color) {
  Text(
      stringResource(text),
      style = jakarta(12, 16f, 600, content),
      modifier =
          Modifier.clip(RoundedCornerShape(100.dp))
              .background(background)
              .padding(horizontal = 12.dp, vertical = 6.dp)
              .testTag(LinkTagScreenTestTags.STATUS_CHIP),
  )
}

@Composable
private fun NfcNotice(availability: NfcAvailability, onOpenNfcSettings: () -> Unit) {
  val colors = SpottedTheme.colors
  Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(colors.errorTint)
              .padding(14.dp),
  ) {
    Text(
        stringResource(
            if (availability == NfcAvailability.DISABLED) R.string.link_tag_nfc_disabled
            else R.string.link_tag_nfc_unsupported
        ),
        style = jakarta(13, 18f, 600, colors.error),
        modifier = Modifier.testTag(LinkTagScreenTestTags.NFC_MESSAGE),
    )
    // Without NFC hardware, there is no setting to turn on.
    if (availability == NfcAvailability.DISABLED) {
      SecondaryButton(
          R.string.link_tag_open_nfc_settings,
          LinkTagScreenTestTags.NFC_SETTINGS_BUTTON,
          onOpenNfcSettings,
      )
    }
  }
}

@Composable
private fun ContactsCard(
    state: LinkTagUiState,
    onShowPhoneChange: (Boolean) -> Unit,
    onShowEmailChange: (Boolean) -> Unit,
) {
  // Same rule as the ViewModel: contacts can change only before starting or after an error.
  val editable = state.step == LinkTagStep.Idle || state.step == LinkTagStep.Error
  FormCard(spacing = 10.dp) {
    SectionLabel(R.string.link_tag_contacts_section)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Text(
          if (state.petName.isBlank()) stringResource(R.string.link_tag_name_and_photo_no_pet)
          else stringResource(R.string.link_tag_name_and_photo, state.petName),
          style = jakarta(14, 18f, 700, SpottedTheme.colors.ink),
          modifier = Modifier.weight(1f),
      )
      Text(
          stringResource(R.string.link_tag_always),
          style = jakarta(12, 16f, 600, SpottedTheme.colors.textSecondary),
      )
    }
    HorizontalDivider(color = SpottedTheme.colors.inputBackground)
    ContactRow(
        R.string.link_tag_phone,
        state.showPhone,
        editable,
        LinkTagScreenTestTags.PHONE_SWITCH,
        onShowPhoneChange,
    )
    HorizontalDivider(color = SpottedTheme.colors.inputBackground)
    ContactRow(
        R.string.link_tag_email,
        state.showEmail,
        editable,
        LinkTagScreenTestTags.EMAIL_SWITCH,
        onShowEmailChange,
    )
  }
}

/** One contact and its switch. The whole row toggles, so its touch target is large enough. */
@Composable
private fun ContactRow(
    @StringRes label: Int,
    shown: Boolean,
    enabled: Boolean,
    testTag: String,
    onChange: (Boolean) -> Unit,
) {
  val colors = SpottedTheme.colors
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth()
              .toggleable(
                  value = shown,
                  enabled = enabled,
                  role = Role.Switch,
                  onValueChange = onChange,
              )
              .testTag(testTag),
  ) {
    Column(Modifier.weight(1f)) {
      Text(stringResource(label), style = jakarta(14, 18f, 700, colors.ink))
      Text(
          stringResource(if (shown) R.string.link_tag_shown else R.string.link_tag_hidden),
          style = jakarta(12, 16f, 500, colors.textSecondary),
      )
    }
    Switch(
        checked = shown,
        onCheckedChange = null,
        enabled = enabled,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.blue,
                uncheckedThumbColor = colors.surface,
                uncheckedTrackColor = colors.buttonBorder,
                uncheckedBorderColor = colors.buttonBorder,
            ),
    )
  }
}

@Composable
private fun Footer(
    state: LinkTagUiState,
    nfcReady: Boolean,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
  val canStart = state.canStart && nfcReady
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    when (state.step) {
      LinkTagStep.Idle ->
          PrimaryButton(
              R.string.link_tag_start,
              LinkTagScreenTestTags.START_BUTTON,
              canStart,
              onStart,
          )
      // Without the pet, retrying can't work: only Cancel is offered.
      LinkTagStep.Error ->
          if (state.petName.isNotBlank()) {
            PrimaryButton(
                R.string.link_tag_retry,
                LinkTagScreenTestTags.RETRY_BUTTON,
                canStart,
                onRetry,
            )
          }
      LinkTagStep.Success ->
          PrimaryButton(R.string.link_tag_done, LinkTagScreenTestTags.DONE_BUTTON, true, onDone)
      LinkTagStep.WaitingForTag,
      LinkTagStep.Writing -> Unit
    }
    if (state.step != LinkTagStep.Success) {
      SecondaryButton(R.string.link_tag_cancel, LinkTagScreenTestTags.CANCEL_BUTTON, onCancel)
    }
  }
}

@Composable
private fun PrimaryButton(
    @StringRes text: Int,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
  Button(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(14.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = SpottedTheme.colors.blue,
              contentColor = SpottedTheme.colors.onAccent,
              disabledContainerColor = SpottedTheme.colors.buttonBorder,
              disabledContentColor = SpottedTheme.colors.textSecondary,
          ),
      modifier = Modifier.fillMaxWidth().height(49.dp).testTag(testTag),
  ) {
    Text(stringResource(text), style = jakarta(15, 20f, 700, Color.Unspecified))
  }
}

@Composable
private fun SecondaryButton(@StringRes text: Int, testTag: String, onClick: () -> Unit) {
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.fillMaxWidth()
              .height(49.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(SpottedTheme.colors.inputBackground)
              .clickable(role = Role.Button, onClick = onClick)
              .testTag(testTag),
  ) {
    Text(stringResource(text), style = jakarta(15, 20f, 700, SpottedTheme.colors.ink))
  }
}
