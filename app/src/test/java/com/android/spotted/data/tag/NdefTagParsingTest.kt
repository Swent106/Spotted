// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.tag

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.model.tag.ScannedTag
import com.android.spotted.model.tag.TagIdSource
import com.android.spotted.model.tag.TagUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class NdefTagParsingTest {

  private val uid = byteArrayOf(0x04, 0x7F, 0x00, 0xFF.toByte())

  private fun message(vararg records: NdefRecord) = NdefMessage(arrayOf(*records))

  @Test
  fun spottedUrlGivesTheTagIdEvenWithAUid() {
    val scanned = scannedTagFrom(uid, message(NdefRecord.createUri(TagUri.forTag("abcd1234"))))

    assertEquals(ScannedTag("abcd1234", TagIdSource.SPOTTED_URL), scanned)
  }

  @Test
  fun spottedUrlIsFoundAfterOtherRecords() {
    val scanned =
        scannedTagFrom(
            uid,
            message(
                NdefRecord.createTextRecord("en", "Hello"),
                NdefRecord.createUri("https://example.com/t/abcd1234"),
                NdefRecord.createUri(TagUri.forTag("efgh5678")),
            ),
        )

    assertEquals(ScannedTag("efgh5678", TagIdSource.SPOTTED_URL), scanned)
  }

  @Test
  fun otherUrlFallsBackToTheUidInHex() {
    val scanned = scannedTagFrom(uid, message(NdefRecord.createUri("https://example.com/x")))

    assertEquals(ScannedTag("047F00FF", TagIdSource.HARDWARE_UID), scanned)
  }

  @Test
  fun blankTagUsesTheUid() {
    assertEquals(ScannedTag("047F00FF", TagIdSource.HARDWARE_UID), scannedTagFrom(uid, null))
  }

  @Test
  fun tagWithNothingUsableGivesNull() {
    assertNull(scannedTagFrom(null, null))
    assertNull(scannedTagFrom(ByteArray(0), null))
    assertNull(scannedTagFrom(null, message(NdefRecord.createTextRecord("en", "Hello"))))
  }

  @Test
  fun noAdapterMeansUnsupported() {
    assertEquals(NfcAvailability.UNSUPPORTED, availabilityOf(null))
  }

  @Test
  fun adapterStateGivesAvailableOrDisabled() {
    val on = mock<NfcAdapter> { on { isEnabled } doReturn true }
    val off = mock<NfcAdapter> { on { isEnabled } doReturn false }

    assertEquals(NfcAvailability.AVAILABLE, availabilityOf(on))
    assertEquals(NfcAvailability.DISABLED, availabilityOf(off))
  }
}
