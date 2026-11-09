// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.spotted.data.tag.NfcTagReader
import com.android.spotted.data.tag.UnsupportedTagReader
import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.model.tag.ScannedTag
import com.android.spotted.model.tag.TagReader

/**
 * The [TagReader] for the current activity, or [UnsupportedTagReader] when there is none
 * (previews). NFC is a system feature, so like the photo picker it is started from the View; the
 * ViewModel only receives the [ScannedTag] values.
 */
@Composable
fun rememberNfcTagReader(): TagReader {
  val activity = LocalActivity.current
  return remember(activity) {
    if (activity == null) UnsupportedTagReader else NfcTagReader(activity)
  }
}

/**
 * Reads tags with [reader] while the screen is resumed and passes each tap to [onTagScanned]. The
 * reader stops when the screen pauses or leaves the composition.
 */
@Composable
fun NfcTagScanEffect(reader: TagReader, onTagScanned: (ScannedTag) -> Unit) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val currentOnTagScanned by rememberUpdatedState(onTagScanned)
  LaunchedEffect(reader, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      reader.scans.collect { currentOnTagScanned(it) }
    }
  }
}

/**
 * [reader]'s [NfcAvailability] as state, so a screen can show "NFC is off" or "no NFC". Starts at
 * [NfcAvailability.AVAILABLE] so no warning flashes before the first value arrives.
 */
@Composable
fun rememberNfcAvailability(reader: TagReader): State<NfcAvailability> =
    reader.availability.collectAsStateWithLifecycle(initialValue = NfcAvailability.AVAILABLE)
