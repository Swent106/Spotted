// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.tag

import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.model.tag.ScannedTag
import com.android.spotted.model.tag.TagIdSource
import com.android.spotted.model.tag.TagUri

/**
 * The [ScannedTag] for a tag with hardware [uid] and NDEF [message]: the id from the first Spotted
 * URL record, else the UID in hexadecimal, else `null` (nothing usable on the tag).
 */
internal fun scannedTagFrom(
    uid: ByteArray?,
    message: NdefMessage?,
): ScannedTag? { // NDEF=NFC Data Exchange Format
  val spottedId =
      message?.records?.firstNotNullOfOrNull { record ->
        record.toUri()?.toString()?.let(TagUri::parseTagId)
      }
  if (spottedId != null) return ScannedTag(spottedId, TagIdSource.SPOTTED_URL)
  if (uid == null || uid.isEmpty()) return null
  return ScannedTag(uid.toHex(), TagIdSource.HARDWARE_UID)
}

/** [NfcAvailability] of [adapter]; `null` means the phone has no NFC hardware. */
internal fun availabilityOf(adapter: NfcAdapter?): NfcAvailability =
    when {
      adapter == null -> NfcAvailability.UNSUPPORTED
      adapter.isEnabled -> NfcAvailability.AVAILABLE
      else -> NfcAvailability.DISABLED
    }

private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
