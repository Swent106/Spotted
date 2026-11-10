// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.tag

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.tag.TagUri
import com.android.spotted.model.tag.TagWriteError
import com.android.spotted.model.tag.TagWriteException
import com.android.spotted.model.tag.TagWriteOutcome
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NfcTagWriterTest {

  private val newId = "0000000000000000000000000000ABCD"
  private lateinit var activity: ComponentActivity
  private val adapter: NfcAdapter = mock()

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    whenever(adapter.isEnabled).thenReturn(true)
  }

  /** A tag the test controls: its content, whether it can be written, and how writing ends. */
  private class FakeConnection(
      var message: NdefMessage? = null,
      override val isWritable: Boolean = true,
      override val maxSize: Int = 137, // NTAG213
      val writeError: Exception? = null,
      val closeError: IOException? = null,
  ) : NdefConnection {
    var written: NdefMessage? = null
    var closed = false

    override fun readMessage(): NdefMessage? = message

    override fun write(message: NdefMessage) {
      writeError?.let { throw it }
      written = message
    }

    override fun close() {
      closed = true
      closeError?.let { throw it }
    }
  }

  private fun urlMessage(url: String) = NdefMessage(NdefRecord.createUri(url))

  private fun errorOf(result: Result<TagWriteOutcome>): TagWriteError =
      (result.exceptionOrNull() as TagWriteException).error

  private fun captureCallback(): NfcAdapter.ReaderCallback =
      argumentCaptor<NfcAdapter.ReaderCallback>()
          .apply { verify(adapter).enableReaderMode(any(), capture(), any(), anyOrNull()) }
          .firstValue

  // writeSpottedUrl: what one tap does to the tag

  @Test
  fun blankTagGetsTheSpottedUrlOfTheNewId() {
    val tag = FakeConnection()

    val result = writeSpottedUrl(tag, newId)

    assertEquals(TagWriteOutcome.Written(newId), result.getOrThrow())
    assertEquals(TagUri.forTag(newId), tag.written!!.records.single().toUri().toString())
    assertTrue(tag.closed)
  }

  @Test
  fun tagWithASpottedUrlIsReadAndNotRewritten() {
    val tag =
        FakeConnection(message = urlMessage(TagUri.forTag("00000000000000000000000000001234")))

    val result = writeSpottedUrl(tag, newId)

    assertEquals(
        TagWriteOutcome.AlreadySpotted("00000000000000000000000000001234"),
        result.getOrThrow(),
    )
    assertNull(tag.written)
    assertTrue(tag.closed)
  }

  @Test
  fun tagWithOtherContentIsOverwritten() {
    val tag = FakeConnection(message = urlMessage("https://example.com/t/abcd1234"))

    assertEquals(TagWriteOutcome.Written(newId), writeSpottedUrl(tag, newId).getOrThrow())
    assertEquals(TagUri.forTag(newId), tag.written!!.records.single().toUri().toString())
  }

  @Test
  fun readOnlyTagFailsWithoutWriting() {
    val tag = FakeConnection(isWritable = false)

    assertEquals(TagWriteError.READ_ONLY, errorOf(writeSpottedUrl(tag, newId)))
    assertNull(tag.written)
    assertTrue(tag.closed)
  }

  @Test
  fun tagTooSmallForTheUrlFailsWithoutWriting() {
    val tag = FakeConnection(maxSize = 10)

    assertEquals(TagWriteError.NOT_ENOUGH_SPACE, errorOf(writeSpottedUrl(tag, newId)))
    assertNull(tag.written)
  }

  @Test
  fun tagMovedAwayDuringTheWriteIsLost() {
    val tag = FakeConnection(writeError = TagLostException())

    assertEquals(TagWriteError.TAG_LOST, errorOf(writeSpottedUrl(tag, newId)))
    assertTrue(tag.closed)
  }

  @Test
  fun outdatedTagIsLost() {
    val tag = FakeConnection(writeError = SecurityException("Tag is out of date"))

    assertEquals(TagWriteError.TAG_LOST, errorOf(writeSpottedUrl(tag, newId)))
  }

  @Test
  fun malformedTagIsUnsupported() {
    val tag = FakeConnection(writeError = FormatException("bad NDEF"))

    assertEquals(TagWriteError.UNSUPPORTED, errorOf(writeSpottedUrl(tag, newId)))
    assertTrue(tag.closed)
  }

  @Test
  fun tagGoneWhenClosingStillKeepsTheWriteResult() {
    val tag = FakeConnection(closeError = IOException("tag gone"))

    assertEquals(TagWriteOutcome.Written(newId), writeSpottedUrl(tag, newId).getOrThrow())
  }

  @Test
  fun defaultAdapterIsThePhonesOne() = runTest {
    // Robolectric's phone has no NFC turned on, so the write fails at once.
    val result = NfcTagWriter(activity).writeOnNextTap(newId)

    assertEquals(TagWriteError.NFC_UNAVAILABLE, errorOf(result))
  }

  @Test
  fun tagThatIsNotNdefIsUnsupported() {
    assertEquals(TagWriteError.UNSUPPORTED, errorOf(writeSpottedUrl(null, newId)))
  }

  // NfcTagWriter: reader mode around one tap

  @Test
  fun tapIsWrittenAndReaderModeStopsAfterIt() = runTest {
    val tag = FakeConnection()
    val writer = NfcTagWriter(activity, adapter, openConnection = { tag })
    val result =
        backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
          writer.writeOnNextTap(newId)
        }
    verify(adapter).enableReaderMode(eq(activity), any(), eq(NfcTagReader.READER_FLAGS), isNull())
    verify(adapter, never()).disableReaderMode(any())

    captureCallback().onTagDiscovered(mock<Tag>())

    assertEquals(TagWriteOutcome.Written(newId), result.await().getOrThrow())
    verify(adapter).disableReaderMode(activity)
  }

  @Test
  fun onlyTheFirstTapIsWritten() = runTest {
    val connections = mutableListOf<FakeConnection>()
    val writer =
        NfcTagWriter(
            activity,
            adapter,
            openConnection = { FakeConnection().also(connections::add) },
        )
    val result =
        backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
          writer.writeOnNextTap(newId)
        }
    val callback = captureCallback()

    callback.onTagDiscovered(mock<Tag>())
    callback.onTagDiscovered(mock<Tag>())

    assertTrue(result.await().isSuccess)
    assertEquals(1, connections.size)
  }

  @Test
  fun cancellingTheWaitStopsReaderMode() = runTest {
    val writer = NfcTagWriter(activity, adapter, openConnection = { FakeConnection() })
    val job =
        backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
          writer.writeOnNextTap(newId)
        }

    job.cancel()
    runCurrent()

    verify(adapter).disableReaderMode(activity)
  }

  @Test
  fun nfcTurnedOffOrMissingFailsAtOnce() = runTest {
    whenever(adapter.isEnabled).thenReturn(false)

    assertEquals(
        TagWriteError.NFC_UNAVAILABLE,
        errorOf(NfcTagWriter(activity, adapter).writeOnNextTap(newId)),
    )
    assertEquals(
        TagWriteError.NFC_UNAVAILABLE,
        errorOf(NfcTagWriter(activity, adapter = null).writeOnNextTap(newId)),
    )
    verify(adapter, never()).enableReaderMode(any(), any(), any(), anyOrNull())
  }

  @Test
  fun invalidIdIsRefusedBeforeReaderModeStarts() = runTest {
    val result = NfcTagWriter(activity, adapter).writeOnNextTap("bad id")

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    verify(adapter, never()).enableReaderMode(any(), any(), any(), anyOrNull())
  }

  // openNdefConnection: the real NDEF technologies, mocked since they need a chip

  /** Runs [block] with a tag whose NDEF technologies are [ndef] and [formatable]. */
  private fun <T> withTagTech(ndef: Ndef?, formatable: NdefFormatable?, block: (Tag) -> T): T {
    val tag = mock<Tag>()
    mockStatic(Ndef::class.java).use { ndefStatic ->
      mockStatic(NdefFormatable::class.java).use { formatableStatic ->
        ndefStatic.`when`<Ndef?> { Ndef.get(tag) }.thenReturn(ndef)
        formatableStatic.`when`<NdefFormatable?> { NdefFormatable.get(tag) }.thenReturn(formatable)
        return block(tag)
      }
    }
  }

  @Test
  fun ndefTagIsReadFromWhatAndroidCachedAndWrittenAfterConnecting() {
    val message = urlMessage(TagUri.forTag(newId))
    val ndef =
        mock<Ndef> {
          on { cachedNdefMessage } doReturn message
          on { isWritable } doReturn true
          on { maxSize } doReturn 137
          on { isConnected } doReturn false
        }

    withTagTech(ndef, formatable = null) { tag ->
      val connection = openNdefConnection(tag)!!
      assertEquals(message, connection.readMessage())
      assertTrue(connection.isWritable)
      assertEquals(137, connection.maxSize)

      connection.write(message)
      connection.close()
    }

    inOrder(ndef) {
      verify(ndef).connect()
      verify(ndef).writeNdefMessage(message)
      verify(ndef).close()
    }
  }

  @Test
  fun ndefTagAlreadyConnectedIsNotConnectedAgain() {
    val message = urlMessage(TagUri.forTag(newId))
    val ndef = mock<Ndef> { on { isConnected } doReturn true }

    withTagTech(ndef, formatable = null) { tag -> openNdefConnection(tag)!!.write(message) }

    verify(ndef, never()).connect()
    verify(ndef).writeNdefMessage(message)
  }

  @Test
  fun blankFormatableTagIsFormattedWithTheMessage() {
    val message = urlMessage(TagUri.forTag(newId))
    val formatable = mock<NdefFormatable> { on { isConnected } doReturn false }

    withTagTech(ndef = null, formatable) { tag ->
      val connection = openNdefConnection(tag)!!
      assertNull(connection.readMessage())
      assertTrue(connection.isWritable)
      assertEquals(Int.MAX_VALUE, connection.maxSize)

      connection.write(message)
      connection.close()
    }

    inOrder(formatable) {
      verify(formatable).connect()
      verify(formatable).format(message)
      verify(formatable).close()
    }
  }

  @Test
  fun formatableTagAlreadyConnectedIsNotConnectedAgain() {
    val message = urlMessage(TagUri.forTag(newId))
    val formatable = mock<NdefFormatable> { on { isConnected } doReturn true }

    withTagTech(ndef = null, formatable) { tag -> openNdefConnection(tag)!!.write(message) }

    verify(formatable, never()).connect()
    verify(formatable).format(message)
  }

  @Test
  fun tagThatIsNeitherNdefNorFormatableHasNoConnection() {
    withTagTech(ndef = null, formatable = null) { tag -> assertNull(openNdefConnection(tag)) }
  }

  @Test
  fun blankNdefTagGetsTheSpottedUrlThroughTheRealConnection() {
    val ndef =
        mock<Ndef> {
          on { cachedNdefMessage } doReturn null
          on { isWritable } doReturn true
          on { maxSize } doReturn 137
        }

    val result =
        withTagTech(ndef, formatable = null) { tag ->
          writeSpottedUrl(openNdefConnection(tag), newId)
        }

    assertEquals(TagWriteOutcome.Written(newId), result.getOrThrow())
    val written = argumentCaptor<NdefMessage>().apply { verify(ndef).writeNdefMessage(capture()) }
    assertEquals(TagUri.forTag(newId), written.firstValue.records.single().toUri().toString())
    verify(ndef).close()
  }
}
