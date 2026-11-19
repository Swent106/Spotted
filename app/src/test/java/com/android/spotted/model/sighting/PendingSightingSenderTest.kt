// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.fakes.FakePendingSightingRepository
import com.android.spotted.model.fakes.FakeSightingRepository
import com.android.spotted.model.photo.FakePhotoRepository
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSightingSenderTest {
  private val pending = FakePendingSightingRepository()
  private val sightings = FakeSightingRepository()
  private val photos = FakePhotoRepository()

  private var signedInUser: String? = "reporter"

  /** In these tests, the server refuses with [IllegalStateException]; [IOException] is offline. */
  private val sender =
      PendingSightingSender(
          pending,
          sightings,
          photos,
          currentUserId = { signedInUser },
          isRefusal = { it is IllegalStateException },
      )

  @Test
  fun sendAll_sendsInQueueOrder_andEmptiesTheQueue() = runTest {
    queue(sighting("s1"), sighting("s2"), sighting("s3"))

    val report = sender.sendAll()

    assertEquals(SendReport(sent = listOf("s1", "s2", "s3")), report)
    assertEquals(listOf("s1", "s2", "s3"), sightings.reportedSightings.map { it.id })
    assertEquals(emptyList<PendingSighting>(), pending.pending)
  }

  @Test
  fun sendAll_keepsTheTimeTheSightingWasSeen_notTheTimeItIsSent() = runTest {
    val seenAt = 1_700_000_000_000L
    queue(sighting("s1", seenAtMillis = seenAt))

    sender.sendAll()

    assertEquals(seenAt, sightings.reportedSightings.single().seenAtMillis)
  }

  @Test
  fun sendAll_sendsEverySightingOnce_evenWhenRunAgain() = runTest {
    queue(sighting("s1"))

    sender.sendAll()
    val secondRun = sender.sendAll()

    assertEquals(SendReport(), secondRun)
    assertEquals(listOf("s1"), sightings.reportedSightings.map { it.id })
  }

  @Test
  fun sendAll_whenTheSentSightingCantBeRemoved_sendsTheSameDocumentNextTime() = runTest {
    queue(sighting("s1"))
    pending.deleteFailure = IOException("disk full")

    val firstRun = sender.sendAll()
    pending.deleteFailure = null
    val secondRun = sender.sendAll()

    assertEquals(SendReport(sent = listOf("s1"), retryLater = true), firstRun)
    assertEquals(SendReport(sent = listOf("s1")), secondRun)
    // Same id, so the second send replaced the first one instead of adding a sighting
    assertEquals(listOf(sighting("s1")), sightings.reportedSightings)
    assertEquals(emptyList<PendingSighting>(), pending.pending)
  }

  @Test
  fun sendAll_withAPhoto_uploadsTheCopyOnThePhone_andSendsItsUrl() = runTest {
    pending.add(sighting("s1"), photoUri = "file:///pending/s1.jpg")

    sender.sendAll()

    val sent = sightings.reportedSightings.single()
    assertEquals("${FakePhotoRepository.FAKE_URL_PREFIX}/pets/reporter/s1/photo.jpg", sent.photoUrl)
    assertEquals("file:///pending/s1.jpg", photos.uploadedPhotos[sent.photoUrl])
  }

  @Test
  fun sendAll_whenTheServerRefusesASighting_dropsItWithTheReason_andSendsTheNextOne() = runTest {
    // The owner already reviewed a sighting with this id, so the server refuses to replace it
    sightings.reportSighting(sighting("s1").copy(status = SightingStatus.CONFIRMED))
    queue(sighting("s1"), sighting("s2"))

    val report = sender.sendAll()

    assertEquals(
        SendReport(
            sent = listOf("s2"),
            dropped = listOf(DroppedSighting("s1", "Sighting s1 cannot be changed")),
        ),
        report,
    )
    assertEquals(emptyList<PendingSighting>(), pending.pending)
    assertEquals(SightingStatus.CONFIRMED, sightings.reportedSightings.first().status)
  }

  @Test
  fun sendAll_whenThePhotoIsRefused_dropsTheSightingWithoutSendingIt() = runTest {
    pending.add(sighting("s1"), photoUri = "file:///pending/s1.jpg")
    photos.failure = IllegalStateException("Photo s1 belongs to another owner")

    val report = sender.sendAll()

    assertEquals(
        SendReport(dropped = listOf(DroppedSighting("s1", "Photo s1 belongs to another owner"))),
        report,
    )
    assertEquals(emptyList<Sighting>(), sightings.reportedSightings)
    assertEquals(emptyList<PendingSighting>(), pending.pending)
  }

  @Test
  fun sendAll_whenARefusalHasNoMessage_givesAGenericReason() = runTest {
    queue(sighting("s1"))
    sightings.reportFailure = IllegalStateException()

    val report = sender.sendAll()

    assertEquals(listOf(DroppedSighting("s1", PendingSightingSender.REFUSED)), report.dropped)
  }

  @Test
  fun sendAll_whenOffline_sendsNothing_keepsTheQueue_andAsksToRetry() = runTest {
    queue(sighting("s1"), sighting("s2"))
    sightings.reportFailure = IOException("offline")

    val report = sender.sendAll()

    assertEquals(SendReport(retryLater = true), report)
    assertEquals(listOf("s1", "s2"), pending.pending.map { it.sighting.id })
  }

  @Test
  fun sendAll_whenThePhotoUploadFailsOffline_stopsThere_andKeepsTheOrder() = runTest {
    queue(sighting("s1"))
    pending.add(sighting("s2"), photoUri = "file:///pending/s2.jpg")
    queue(sighting("s3"))
    photos.failure = IOException("offline")

    val report = sender.sendAll()

    assertEquals(SendReport(sent = listOf("s1"), retryLater = true), report)
    assertEquals(listOf("s2", "s3"), pending.pending.map { it.sighting.id })
    assertEquals(listOf("s1"), sightings.reportedSightings.map { it.id })

    // Back online: the rest is sent, in order
    photos.failure = null
    assertEquals(SendReport(sent = listOf("s2", "s3")), sender.sendAll())
  }

  @Test
  fun sendAll_whenNobodyIsSignedIn_sendsAndDropsNothing_andAsksToRetry() = runTest {
    queue(sighting("s1"))
    signedInUser = null

    val report = sender.sendAll()

    assertEquals(SendReport(retryLater = true), report)
    assertEquals(emptyList<Sighting>(), sightings.reportedSightings)
    assertEquals(listOf("s1"), pending.pending.map { it.sighting.id })
  }

  @Test
  fun sendAll_keepsTheSightingsOfAnotherAccount_andSendsTheSignedInUsers() = runTest {
    queue(sighting("s1").copy(reporterId = "previous-user"), sighting("s2"))

    val report = sender.sendAll()

    assertEquals(SendReport(sent = listOf("s2")), report)
    assertEquals(listOf("s1"), pending.pending.map { it.sighting.id })

    // Once the other account signs in again, its sighting is sent
    signedInUser = "previous-user"
    assertEquals(SendReport(sent = listOf("s1")), sender.sendAll())
  }

  @Test
  fun sendAll_whenAnotherAccountSignsInDuringTheRun_stopsThere_andKeepsTheRest() = runTest {
    queue(sighting("s1"), sighting("s2"))
    // The account changes right after s1 is sent
    val switchingSender =
        PendingSightingSender(
            pending,
            sightings,
            photos,
            currentUserId = { if (sightings.reportedSightings.isEmpty()) "reporter" else "other" },
            isRefusal = { true },
        )

    val report = switchingSender.sendAll()

    // s2 is neither sent as the other account nor dropped: it waits for its reporter
    assertEquals(SendReport(sent = listOf("s1"), retryLater = true), report)
    assertEquals(listOf("s2"), pending.pending.map { it.sighting.id })
  }

  @Test
  fun sendAll_whenARefusedSightingCantBeRemoved_doesNotReportItAsDropped() = runTest {
    queue(sighting("s1"))
    sightings.reportFailure = IllegalStateException("refused")
    pending.deleteFailure = IOException("disk full")

    val report = sender.sendAll()

    assertEquals(SendReport(retryLater = true), report)
    assertEquals(listOf("s1"), pending.pending.map { it.sighting.id })
  }

  @Test
  fun sendAll_whenThePhotoIsUploadedButTheReportFailsOffline_sendsItNextTime() = runTest {
    pending.add(sighting("s1"), photoUri = "file:///pending/s1.jpg")
    sightings.reportFailure = IOException("offline")

    val firstRun = sender.sendAll()
    sightings.reportFailure = null
    val secondRun = sender.sendAll()

    assertEquals(SendReport(retryLater = true), firstRun)
    assertEquals(SendReport(sent = listOf("s1")), secondRun)
    // Uploaded twice to the same path, so the sent sighting points to the one photo
    assertEquals(
        "${FakePhotoRepository.FAKE_URL_PREFIX}/pets/reporter/s1/photo.jpg",
        sightings.reportedSightings.single().photoUrl,
    )
    assertEquals(emptyList<PendingSighting>(), pending.pending)
  }

  @Test
  fun sendAll_whenTheQueueCantBeRead_asksToRetry() = runTest {
    pending.listFailure = IOException("unreadable")

    val report = sender.sendAll()

    assertTrue(report.retryLater)
    assertTrue(report.sent.isEmpty())
  }

  @Test
  fun sendAll_withAnEmptyQueue_hasNothingToDo() = runTest {
    val report = sender.sendAll()

    assertEquals(SendReport(), report)
    assertFalse(report.retryLater)
  }

  private suspend fun queue(vararg queued: Sighting) {
    queued.forEach { pending.add(it) }
  }

  private fun sighting(id: String, seenAtMillis: Long = 1_000L) =
      Sighting(
          id = id,
          reporterId = "reporter",
          location = Location(46.5, 6.6),
          seenAtMillis = seenAtMillis,
          species = Species.CAT,
          alertId = "alert-1",
      )
}
