// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.fakes.FakePendingSightingRepository
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks that the fake follows the [PendingSightingRepository] contract the tests rely on. */
class FakePendingSightingRepositoryTest {
  private val repository = FakePendingSightingRepository()

  @Test
  fun add_keepsTheOrder_andThePhoto() = runTest {
    repository.add(sighting("s1"), photoUri = "file://photo-1.jpg")
    repository.add(sighting("s2"))

    assertEquals(
        listOf(
            PendingSighting(sighting("s1"), "file://photo-1.jpg"),
            PendingSighting(sighting("s2"), null),
        ),
        repository.list().getOrThrow(),
    )
    assertEquals(2, repository.addCount)
  }

  @Test
  fun add_withSameId_replacesItInPlace() = runTest {
    repository.add(sighting("s1"))
    repository.add(sighting("s2"))
    val updated = sighting("s1").copy(headingDegrees = 90)

    repository.add(updated)

    assertEquals(
        listOf(updated, sighting("s2")),
        repository.list().getOrThrow().map { it.sighting },
    )
  }

  @Test
  fun delete_removesOnlyThatSighting_andAMissingIdSucceeds() = runTest {
    repository.add(sighting("s1"))
    repository.add(sighting("s2"))

    assertTrue(repository.delete("s1").isSuccess)
    assertTrue(repository.delete("missing").isSuccess)

    assertEquals(listOf("s2"), repository.pending.map { it.sighting.id })
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun observeCount_emitsTheCountAfterEachChange() = runTest {
    val counts = mutableListOf<Result<Int>>()
    val job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          repository.observeCount().toList(counts)
        }

    repository.add(sighting("s1"))
    repository.add(sighting("s2"))
    repository.delete("s1")

    assertEquals(listOf(0, 1, 2, 1).map { Result.success(it) }, counts)
    job.cancel()
  }

  @Test
  fun failures_areReturned_andChangeNothing() = runTest {
    val error = IOException("disk full")
    repository.addFailure = error
    repository.listFailure = error
    repository.deleteFailure = error
    repository.countFailure = error

    assertSame(error, repository.add(sighting("s1")).exceptionOrNull())
    assertSame(error, repository.list().exceptionOrNull())
    assertSame(error, repository.delete("s1").exceptionOrNull())
    assertSame(error, repository.observeCount().first().exceptionOrNull())
    assertEquals(emptyList<PendingSighting>(), repository.pending)
    assertEquals(0, repository.addCount)
  }

  private fun sighting(id: String) =
      Sighting(
          id = id,
          reporterId = "reporter",
          location = Location(46.5, 6.6),
          seenAtMillis = 1_000L,
          species = Species.DOG,
      )
}
