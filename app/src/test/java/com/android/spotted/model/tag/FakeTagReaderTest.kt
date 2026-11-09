// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.model.tag.ScannedTag
import com.android.spotted.model.tag.TagIdSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeTagReaderTest {

  private val tag = ScannedTag("abcd1234", TagIdSource.SPOTTED_URL)

  @Test
  fun availabilityStartsAvailableAndFollowsSetAvailability() = runTest {
    val reader = FakeTagReader()
    assertEquals(NfcAvailability.AVAILABLE, reader.availability.first())

    reader.setAvailability(NfcAvailability.DISABLED)

    assertEquals(NfcAvailability.DISABLED, reader.availability.first())
  }

  @Test
  fun scanReachesAnActiveCollector() = runTest {
    val reader = FakeTagReader()
    val scans = mutableListOf<ScannedTag>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { reader.scans.toList(scans) }
    assertEquals(1, reader.activeScanners)

    reader.emitScan(tag)

    assertEquals(listOf(tag), scans)
    job.cancel()
  }

  @Test
  fun scanWithNobodyReadingIsDroppedLikeAReaderThatIsOff() = runTest {
    val reader = FakeTagReader()
    reader.emitScan(tag) // nobody collecting yet
    assertEquals(0, reader.activeScanners)

    val scans = mutableListOf<ScannedTag>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { reader.scans.toList(scans) }

    assertEquals(emptyList<ScannedTag>(), scans)
    job.cancel()
  }
}
