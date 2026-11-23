// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.sighting

import android.net.Uri
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.sighting.AnimalActivity
import com.android.spotted.model.sighting.AnimalCondition
import com.android.spotted.model.sighting.AnimalReaction
import com.android.spotted.model.sighting.CollarStatus
import com.android.spotted.model.sighting.PendingSighting
import com.android.spotted.model.sighting.PlaceType
import com.android.spotted.model.sighting.Sighting
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingSightingRepositoryFileTest {

  @get:Rule val folder = TemporaryFolder()

  private val testScope = TestScope(UnconfinedTestDispatcher())
  private val directory by lazy { File(folder.root, "pending") }

  /** Photos the user picked, by URI. Removing one is like the Photo Picker access ending. */
  private val pickedPhotos = mutableMapOf("content://picker/1" to byteArrayOf(1, 2, 3))
  private var sendsScheduled = 0

  /** A new repository on the same folder: what the app sees after it was closed and reopened. */
  private fun openRepository() =
      PendingSightingRepositoryFile(
          directory = directory,
          openPhoto = { pickedPhotos[it]?.inputStream() },
          scheduleSend = { sendsScheduled++ },
          ioDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
      )

  private val repository by lazy { openRepository() }

  @Test
  fun aQueuedSighting_survivesARestart_withAllItsDetailsAndPhoto() = testScope.runTest {
    val detailed =
        sighting("s1")
            .copy(
                alertId = "alert-1",
                headingDegrees = 225,
                activities = setOf(AnimalActivity.RUNNING, AnimalActivity.HIDING),
                conditions = setOf(AnimalCondition.LIMPING),
                reactions = setOf(AnimalReaction.RAN_AWAY),
                placeType = PlaceType.PARK,
                collar = CollarStatus.NO_COLLAR,
            )
    assertTrue(repository.add(detailed, photoUri = "content://picker/1").isSuccess)
    assertTrue(repository.add(sighting("s2")).isSuccess)
    // The Photo Picker access ends when the app is closed
    pickedPhotos.clear()

    val queued = openRepository().list().getOrThrow()

    assertEquals(listOf(detailed, sighting("s2")), queued.map { it.sighting })
    assertArrayEquals(byteArrayOf(1, 2, 3), readPhoto(queued[0].photoUri!!))
    assertNull(queued[1].photoUri)
  }

  @Test
  fun add_withTheSameId_replacesItInPlace_andMovingToNoPhotoDeletesTheCopy() = testScope.runTest {
    repository.add(sighting("s1"), photoUri = "content://picker/1")
    val copy = repository.list().getOrThrow().single().photoUri!!
    repository.add(sighting("s2"))
    val updated = sighting("s1").copy(headingDegrees = 90)

    repository.add(updated, photoUri = null)

    assertEquals(
        listOf(PendingSighting(updated, null), PendingSighting(sighting("s2"), null)),
        repository.list().getOrThrow(),
    )
    assertFalse(File(Uri.parse(copy).path!!).exists())
  }

  @Test
  fun add_asksForASendEachTime() = testScope.runTest {
    repository.add(sighting("s1"))
    repository.add(sighting("s2"))

    assertEquals(2, sendsScheduled)
  }

  @Test
  fun add_whenThePhotoCantBeOpened_failsAndQueuesNothing() = testScope.runTest {
    val result = repository.add(sighting("s1"), photoUri = "content://picker/gone")

    assertTrue(result.exceptionOrNull() is FileNotFoundException)
    assertEquals(emptyList<PendingSighting>(), repository.list().getOrThrow())
    assertEquals(0, sendsScheduled)
  }

  @Test
  fun add_whenTheQueueCantBeWritten_failsAndLeavesNoPhotoCopy() = testScope.runTest {
    // A folder where the temporary queue file goes makes every write fail
    File(directory, "queue.json.tmp").mkdirs()

    val result = repository.add(sighting("s1"), photoUri = "content://picker/1")

    assertTrue(result.isFailure)
    assertEquals(emptyList<String>(), File(directory, "photos").list()!!.toList())
    // Scheduled before the write: that send finds nothing to send, which is harmless
    assertEquals(1, sendsScheduled)
  }

  @Test
  fun delete_removesTheSightingAndItsPhoto_andAMissingIdSucceeds() = testScope.runTest {
    repository.add(sighting("s1"), photoUri = "content://picker/1")
    repository.add(sighting("s2"))
    val copy = repository.list().getOrThrow().first().photoUri!!

    assertTrue(repository.delete("s1").isSuccess)
    assertTrue(repository.delete("missing").isSuccess)

    assertEquals(listOf("s2"), openRepository().list().getOrThrow().map { it.sighting.id })
    assertFalse(File(Uri.parse(copy).path!!).exists())
  }

  @Test
  fun observeCount_startsFromWhatIsOnThePhone_thenFollowsChanges() = testScope.runTest {
    repository.add(sighting("s1"))
    val reopened = openRepository()
    val counts = mutableListOf<Result<Int>>()
    backgroundScope.launch { reopened.observeCount().toList(counts) }

    reopened.add(sighting("s2"))
    reopened.delete("s1")
    reopened.delete("s2")

    assertEquals(listOf(1, 2, 1, 0).map { Result.success(it) }, counts)
  }

  @Test
  fun aDamagedQueueFile_isReturnedAsAFailure_everywhere() = testScope.runTest {
    directory.mkdirs()
    File(directory, "queue.json").writeText("{not json")

    assertTrue(repository.list().isFailure)
    assertTrue(repository.add(sighting("s1")).isFailure)
    assertTrue(repository.delete("s1").isFailure)
    assertTrue(repository.observeCount().first().isFailure)
  }

  private fun readPhoto(uri: String): ByteArray = File(Uri.parse(uri).path!!).readBytes()

  private fun sighting(id: String) =
      Sighting(
          id = id,
          reporterId = "reporter",
          location = Location(46.5191, 6.5668),
          seenAtMillis = 1_700_000_000_000L,
          species = Species.DOG,
      )
}
