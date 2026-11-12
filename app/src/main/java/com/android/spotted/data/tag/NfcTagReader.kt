// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.tag

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.model.tag.ScannedTag
import com.android.spotted.model.tag.TagReader
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * [TagReader] on the phone's NFC hardware, through Android's reader mode.
 *
 * Works on any NFC Forum tag (Types 1–5, e.g. NTAG213/215): the id comes from the standard NDEF URL
 * record, with the hardware UID as a fallback, so no chip-specific code is needed. Reader mode is
 * tied to [activity] and is on only while [scans] is collected.
 */
class NfcTagReader(
    private val activity: Activity,
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity),
    /** Reads the NDEF message Android already loaded with the tag; replaced in tests. */
    private val readNdefMessage: (Tag) -> NdefMessage? = { Ndef.get(it)?.cachedNdefMessage },
) : TagReader {

  override val availability: Flow<NfcAvailability> = callbackFlow {
    val nfc = adapter
    if (nfc == null) {
      send(NfcAvailability.UNSUPPORTED)
      close()
      return@callbackFlow
    }
    val receiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
            trySend(availabilityOf(nfc))
          }
        }
    // System broadcasts still reach a not-exported receiver.
    ContextCompat.registerReceiver(
        activity,
        receiver,
        IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED, // no other app is allowed to receive
    )
    trySend(
        availabilityOf(nfc)
    ) // non-suspending function as onReceive isn't a coroutine,runs on the main thread and never
    // blocks
    awaitClose { activity.unregisterReceiver(receiver) }
  }
      .distinctUntilChanged()

  override val scans: Flow<ScannedTag> = callbackFlow {
    val nfc = adapter
    if (nfc == null) {
      close()
      return@callbackFlow
    }
    // Called on an NFC binder thread; trySend is thread-safe.
    val callback = NfcAdapter.ReaderCallback { tag ->
      val scanned = scannedTagFrom(tag.id, readNdefMessage(tag))
      if (scanned != null) {
        trySend(scanned)
        // Ignore this tag until it has left the phone: held in place = no repeat,
        // taken away and tapped again = a new scan.
        nfc.ignore(tag, REMOVAL_DEBOUNCE_MILLIS, null, null)
      }
    }
    nfc.enableReaderMode(activity, callback, READER_FLAGS, readerOptions())
    awaitClose { nfc.disableReaderMode(activity) }
  }

  companion object {
    /** Every NFC Forum tag type; the NDEF check stays on so the URL record is read with the tag. */
    const val READER_FLAGS =
        NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V

    /** How often Android checks the tag is still there (ms); lower than the 125 ms default. */
    const val PRESENCE_CHECK_DELAY_MILLIS = 500

    /** How long a tag must be away from the phone before the same tag counts as a new tap (ms). */
    const val REMOVAL_DEBOUNCE_MILLIS = 300

    private fun readerOptions() =
        Bundle().apply {
          putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, PRESENCE_CHECK_DELAY_MILLIS)
        }
  }
}

/** [TagReader] for phones without NFC, and for previews or tests with no activity. */
object UnsupportedTagReader : TagReader {
  override val availability: Flow<NfcAvailability> = flowOf(NfcAvailability.UNSUPPORTED)
  override val scans: Flow<ScannedTag> = emptyFlow()
}
