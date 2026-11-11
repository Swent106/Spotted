// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.linktag

import androidx.annotation.StringRes

/**
 * Where the owner is in linking an NFC tag to their pet. The normal flow is Idle → WaitingForTag →
 * Writing → Success; any failure ends in Error, from which the owner can retry.
 */
enum class LinkTagStep {
  /** Not started: the owner chooses which contacts finders can see. */
  Idle,
  /** Waiting for the owner to tap a tag on the phone. */
  WaitingForTag,
  /** The tag was tapped: its link and the chosen contacts are being saved. */
  Writing,
  /** The tag is linked to the pet with the chosen contacts. */
  Success,
  /** Nothing was linked; [LinkTagUiState.errorMessage] says why and the owner can retry. */
  Error,
}

/**
 * State of the link tag screen. Finders can only be shown the owner's phone and email: the address
 * is never one of the choices.
 */
data class LinkTagUiState(
    val step: LinkTagStep = LinkTagStep.Idle,
    /** The pet's name, or blank until the pet is loaded. */
    val petName: String = "",
    /** Finders who scan the tag see the owner's phone number. On by default, like `linkTag`. */
    val showPhone: Boolean = true,
    /** Finders who scan the tag see the owner's email. On by default, like `linkTag`. */
    val showEmail: Boolean = true,
    /** Why linking failed, shown while [step] is [LinkTagStep.Error]; `null` otherwise. */
    @StringRes val errorMessage: Int? = null,
) {
  /** True when linking can start or be retried: the pet is loaded and one contact is shown. */
  val canStart: Boolean
    get() =
        (step == LinkTagStep.Idle || step == LinkTagStep.Error) &&
            petName.isNotBlank() &&
            (showPhone || showEmail)
}
