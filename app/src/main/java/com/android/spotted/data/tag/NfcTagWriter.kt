// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.tag

import android.app.Activity
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.android.spotted.model.tag.PetTag
import com.android.spotted.model.tag.TagUri
import com.android.spotted.model.tag.TagWriteError
import com.android.spotted.model.tag.TagWriteException
import com.android.spotted.model.tag.TagWriteOutcome
import com.android.spotted.model.tag.TagWriter
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [TagWriter] on the phone's NFC hardware, through Android's reader mode.
 *
 * Writes one NDEF URI record, `https://<TagUri.HOST>/t/<tagId>`, which any phone opens without the
 * app. Reader mode is tied to [activity] and is on only while [writeOnNextTap] waits, so it must
 * not run together with an [NfcTagReader] on the same screen. The tag is never locked (made
 * read-only): whether it is taken is decided in Firestore, so it can be unlinked and reused.
 */
class NfcTagWriter(
    private val activity: Activity,
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity),
    /** Opens the NDEF technology of a tapped tag; replaced in tests. */
    private val openConnection: (Tag) -> NdefConnection? = ::openNdefConnection,
) : TagWriter {

  override suspend fun writeOnNextTap(newTagId: String): Result<TagWriteOutcome> {
    if (!TagUri.isValidTagId(newTagId)) {
      return Result.failure(IllegalArgumentException("Invalid tag id: $newTagId"))
    }
    val nfc = adapter
    if (nfc == null || !nfc.isEnabled) {
      return Result.failure(TagWriteException(TagWriteError.NFC_UNAVAILABLE))
    }
    return try {
      suspendCancellableCoroutine { continuation ->
        // Only the first tap counts, even if Android reports another before reader mode stops.
        val handled = AtomicBoolean(false)
        // Called on an NFC binder thread, where blocking tag I/O is allowed.
        val callback = NfcAdapter.ReaderCallback { tag ->
          if (handled.compareAndSet(false, true)) {
            continuation.resume(writeSpottedUrl(openConnection(tag), newTagId))
          }
        }
        nfc.enableReaderMode(activity, callback, NfcTagReader.READER_FLAGS, null)
      }
    } finally {
      nfc.disableReaderMode(activity)
    }
  }
}

/** The few NDEF operations the writer needs, so its rules can be tested without a real chip. */
interface NdefConnection : Closeable {
  /** The message on the tag, or `null` if it holds none (blank or unformatted tag). */
  fun readMessage(): NdefMessage?

  val isWritable: Boolean

  /** Space for an NDEF message, in bytes. */
  val maxSize: Int

  fun write(message: NdefMessage)
}

/**
 * Writes the Spotted URL of [newTagId] on the tag behind [connection], unless the tag already holds
 * a Spotted URL with a valid id ([PetTag.isValidId]). Always closes [connection].
 */
internal fun writeSpottedUrl(
    connection: NdefConnection?,
    newTagId: String,
): Result<TagWriteOutcome> {
  if (connection == null) return writeFailure(TagWriteError.UNSUPPORTED)
  return try {
    // An id Firestore refuses can never be linked: such a tag is rewritten like a blank one.
    val existingId =
        scannedTagFrom(uid = null, message = connection.readMessage())
            ?.tagId
            ?.takeIf(PetTag::isValidId)
    when {
      existingId != null -> Result.success(TagWriteOutcome.AlreadySpotted(existingId))
      !connection.isWritable -> writeFailure(TagWriteError.READ_ONLY)
      else -> {
        val message = NdefMessage(NdefRecord.createUri(TagUri.forTag(newTagId)))
        if (message.byteArrayLength > connection.maxSize) {
          writeFailure(TagWriteError.NOT_ENOUGH_SPACE)
        } else {
          connection.write(message)
          Result.success(TagWriteOutcome.Written(newTagId))
        }
      }
    }
  } catch (exception: IOException) { // includes TagLostException
    writeFailure(TagWriteError.TAG_LOST, exception)
  } catch (exception: SecurityException) { // Android's "tag is out of date": it left the field
    writeFailure(TagWriteError.TAG_LOST, exception)
  } catch (exception: FormatException) {
    writeFailure(TagWriteError.UNSUPPORTED, exception)
  } finally {
    try {
      connection.close()
    } catch (_: IOException) {
      // Nothing to do: the tag is already gone.
    }
  }
}

private fun writeFailure(error: TagWriteError, cause: Throwable? = null): Result<TagWriteOutcome> =
    Result.failure(TagWriteException(error, cause))

/** An NDEF tag, or a blank tag that can be formatted to NDEF; `null` for any other tag. */
internal fun openNdefConnection(tag: Tag): NdefConnection? =
    Ndef.get(tag)?.let(::NdefTagConnection) ?: NdefFormatable.get(tag)?.let(::FormatableConnection)

private class NdefTagConnection(private val ndef: Ndef) : NdefConnection {
  // Read by Android with the tag (the NDEF check is on), so no I/O here.
  override fun readMessage(): NdefMessage? = ndef.cachedNdefMessage

  override val isWritable: Boolean
    get() = ndef.isWritable

  override val maxSize: Int
    get() = ndef.maxSize

  override fun write(message: NdefMessage) {
    if (!ndef.isConnected) ndef.connect()
    ndef.writeNdefMessage(message)
  }

  override fun close() = ndef.close()
}

private class FormatableConnection(private val formatable: NdefFormatable) : NdefConnection {
  override fun readMessage(): NdefMessage? = null

  override val isWritable: Boolean = true

  // Unknown until formatted; formatting fails with an IOException if the message does not fit.
  override val maxSize: Int = Int.MAX_VALUE

  override fun write(message: NdefMessage) {
    if (!formatable.isConnected) formatable.connect()
    formatable.format(message)
  }

  override fun close() = formatable.close()
}
