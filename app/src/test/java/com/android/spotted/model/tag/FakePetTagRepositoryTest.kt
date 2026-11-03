// Portions of this code were generated with the help of Copilot.
package com.android.spotted.model.tag

import com.android.spotted.model.fakes.FakePetTagRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePetTagRepositoryTest {
  @Test
  fun linkTag_savesTagAndMovesItBetweenMyPets() = runTest {
    var linkedAtMillis = 1234L
    val repository =
        FakePetTagRepository(
            ownerId = "owner-1",
            now = { linkedAtMillis },
        )

    assertTrue(repository.linkTag("A1B2C3D4", "pet-1").isSuccess)
    linkedAtMillis = 200L
    assertTrue(repository.linkTag("A1B2C3D4", "pet-2").isSuccess)
    assertEquals(PetTag("A1B2C3D4", "pet-2", "owner-1", 200L), repository.linkedTags.single())
  }

  @Test
  fun linkTag_failsWhenTagBelongsToAnotherOwner() = runTest {
    val tag = PetTag("A1B2C3D4", "other-pet", "owner-2", 100L)
    val repository =
        FakePetTagRepository(
            ownerId = "owner-1",
            initialTags = listOf(tag),
        )

    val result = repository.linkTag(tag.tagId, "pet-1")

    assertTrue(result.exceptionOrNull() is TagAlreadyLinkedException)
    assertEquals(listOf(tag), repository.linkedTags)
  }

  @Test
  fun unlinkTag_removesTagAndUnknownLookupsReturnNull() = runTest {
    val tag = PetTag("A1B2C3D4", "pet-1", "owner-1", 100L)
    val repository = FakePetTagRepository(ownerId = "owner-1", initialTags = listOf(tag))

    assertTrue(repository.unlinkTag(tag.tagId).isSuccess)
    assertTrue(repository.linkedTags.isEmpty())
    assertEquals(null, repository.getTag("missing").getOrThrow())
  }

  @Test
  fun configuredFailure_isReturnedWithoutChangingTags() = runTest {
    val tag = PetTag("A1B2C3D4", "pet-1", "owner-1", 100L)
    val repository =
        FakePetTagRepository(
            ownerId = "owner-1",
            initialTags = listOf(tag),
        )
    val failure = IllegalStateException("configured failure")
    repository.failure = failure

    assertSame(failure, repository.getTag(tag.tagId).exceptionOrNull())
    assertEquals(listOf(tag), repository.linkedTags)
  }
}
