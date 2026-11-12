// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.tag

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.model.tag.ScannedTag
import com.android.spotted.model.tag.TagIdSource
import com.android.spotted.model.tag.TagUri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NfcTagReaderTest {

  private lateinit var activity: ComponentActivity
  private val adapter: NfcAdapter = mock()

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
  }

  private fun mockTag(uid: ByteArray): Tag = mock { on { id } doReturn uid }

  private fun captureCallback(): NfcAdapter.ReaderCallback =
      argumentCaptor<NfcAdapter.ReaderCallback>()
          .apply { verify(adapter).enableReaderMode(any(), capture(), any(), any()) }
          .firstValue

  @Test
  fun availabilityWithoutHardwareIsUnsupportedAndCompletes() = runTest {
    val values = NfcTagReader(activity, adapter = null).availability.toList()

    assertEquals(listOf(NfcAvailability.UNSUPPORTED), values)
  }

  @Test
  fun availabilityStartsWithTheAdapterState() = runTest {
    whenever(adapter.isEnabled).thenReturn(true)

    assertEquals(NfcAvailability.AVAILABLE, NfcTagReader(activity, adapter).availability.first())
  }

  @Test
  fun availabilityFollowsNfcBeingTurnedOn() = runTest {
    whenever(adapter.isEnabled).thenReturn(false)
    val values = mutableListOf<NfcAvailability>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      NfcTagReader(activity, adapter).availability.toList(values)
    }

    whenever(adapter.isEnabled).thenReturn(true)
    activity.sendBroadcast(
        Intent(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED).setPackage(activity.packageName)
    )
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(listOf(NfcAvailability.DISABLED, NfcAvailability.AVAILABLE), values)
  }

  @Test
  fun readerModeIsOnOnlyWhileScansAreCollected() = runTest {
    val reader = NfcTagReader(activity, adapter)
    val job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reader.scans.collect {} }

    verify(adapter).enableReaderMode(eq(activity), any(), eq(NfcTagReader.READER_FLAGS), any())
    verify(adapter, never()).disableReaderMode(any())

    job.cancel()
    runCurrent()

    verify(adapter).disableReaderMode(activity)
  }

  @Test
  fun tapIsEmittedWithTheUidAndTheTagIgnoredUntilRemoved() = runTest {
    val reader = NfcTagReader(activity, adapter)
    val scans = mutableListOf<ScannedTag>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reader.scans.toList(scans) }
    val tag = mockTag(byteArrayOf(0x04, 0xA2.toByte(), 0x1F))

    captureCallback().onTagDiscovered(tag)

    assertEquals(listOf(ScannedTag("04A21F", TagIdSource.HARDWARE_UID)), scans)
    verify(adapter).ignore(eq(tag), eq(NfcTagReader.REMOVAL_DEBOUNCE_MILLIS), isNull(), isNull())
  }

  @Test
  fun tagWithNothingUsableIsIgnored() = runTest {
    val reader = NfcTagReader(activity, adapter)
    val scans = mutableListOf<ScannedTag>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reader.scans.toList(scans) }

    captureCallback().onTagDiscovered(mockTag(ByteArray(0)))

    assertTrue(scans.isEmpty())
    verify(adapter, never()).ignore(any(), any(), anyOrNull(), anyOrNull())
  }

  @Test
  fun scansWithoutHardwareCompleteEmpty() = runTest {
    assertTrue(NfcTagReader(activity, adapter = null).scans.toList().isEmpty())
  }

  @Test
  fun unsupportedReaderHasNoNfcAndNoScans() = runTest {
    assertEquals(listOf(NfcAvailability.UNSUPPORTED), UnsupportedTagReader.availability.toList())
    assertTrue(UnsupportedTagReader.scans.toList().isEmpty())
  }

  @Test
  fun tagWithASpottedUrlIsEmittedWithTheUrlIdNotTheUid() = runTest {
    val message = NdefMessage(arrayOf(NdefRecord.createUri(TagUri.forTag("abcd1234"))))
    val reader = NfcTagReader(activity, adapter, readNdefMessage = { message })
    val scans = mutableListOf<ScannedTag>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reader.scans.toList(scans) }

    captureCallback().onTagDiscovered(mockTag(byteArrayOf(0x04, 0x11)))

    assertEquals(listOf(ScannedTag("abcd1234", TagIdSource.SPOTTED_URL)), scans)
  }

  @Test
  fun detectionAfterAndroidLiftsTheIgnoreIsANewScan() = runTest {
    val reader = NfcTagReader(activity, adapter)
    val scans = mutableListOf<ScannedTag>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reader.scans.toList(scans) }
    val callback = captureCallback()
    val tag = mockTag(byteArrayOf(0x04, 0xA2.toByte(), 0x1F))

    callback.onTagDiscovered(tag) // first tap
    callback.onTagDiscovered(tag) // Android only calls again after the tag was removed

    assertEquals(2, scans.size)
    verify(adapter, times(2))
        .ignore(eq(tag), eq(NfcTagReader.REMOVAL_DEBOUNCE_MILLIS), isNull(), isNull())
  }

  @Test
  fun readerFlagsReadEveryTagTypeAndKeepTheNdefCheck() {
    val flags = NfcTagReader.READER_FLAGS
    listOf(
            NfcAdapter.FLAG_READER_NFC_A,
            NfcAdapter.FLAG_READER_NFC_B,
            NfcAdapter.FLAG_READER_NFC_F,
            NfcAdapter.FLAG_READER_NFC_V,
        )
        .forEach { assertEquals(it, flags and it) }
    assertEquals(0, flags and NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK)
  }

  @Test
  fun readerModeUsesThePresenceCheckDelay() = runTest {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      NfcTagReader(activity, adapter).scans.collect {}
    }

    val options =
        argumentCaptor<Bundle>()
            .apply { verify(adapter).enableReaderMode(any(), any(), any(), capture()) }
            .firstValue
    assertEquals(
        NfcTagReader.PRESENCE_CHECK_DELAY_MILLIS,
        options.getInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY),
    )
  }
}
