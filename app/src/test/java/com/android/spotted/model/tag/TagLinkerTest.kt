// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.tag

import com.android.spotted.model.fakes.FakePetTagRepository
import com.android.spotted.model.fakes.FakeTagWriter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagLinkerTest {

  private val repository = FakePetTagRepository(ownerId = "owner-1")
  private val writer = FakeTagWriter()
  private val linker = TagLinker(repository)

  private suspend fun tapAndLink(petId: String): Result<Unit> =
      linker.link(writer.writeOnNextTap(linker.newTagId()), petId)

  private fun linkedTag(tagId: String, petId: String, ownerId: String = "owner-1") =
      PetTag(tagId = tagId, petId = petId, ownerId = ownerId, linkedAtMillis = 0L)

  @Test
  fun newTagIdComesFromTheRepositoryInTheRulesFormat() {
    val id = linker.newTagId()

    assertTrue(id.matches(Regex("[0-9A-F]{32}")))
    assertTrue(TagUri.isValidTagId(id))
  }

  @Test
  fun blankTagIsWrittenWithANewIdThenLinked() = runTest {
    assertTrue(tapAndLink("pet-1").isSuccess)

    assertEquals(1, writer.writtenIds.size)
    val tag = repository.linkedTags.single()
    assertEquals(writer.writtenIds.single(), tag.tagId)
    assertEquals("pet-1", tag.petId)
  }

  @Test
  fun tagWhoseUrlPointsToNothingIsReusedWithItsIdWithoutWriting() = runTest {
    writer.existingTagId = "0000000000000000000000000000ABCD"

    assertTrue(tapAndLink("pet-1").isSuccess)

    assertTrue(writer.writtenIds.isEmpty())
    assertEquals("0000000000000000000000000000ABCD", repository.linkedTags.single().tagId)
  }

  @Test
  fun tagAlreadyLinkedToAnotherOfMyPetsIsRefused() = runTest {
    val repository =
        FakePetTagRepository(
            ownerId = "owner-1",
            initialTags = listOf(linkedTag("0000000000000000000000000000ABCD", "pet-1")),
        )
    writer.existingTagId = "0000000000000000000000000000ABCD"

    val result = TagLinker(repository).link(writer.writeOnNextTap("UNUSED00"), "pet-2")

    assertTrue(result.exceptionOrNull() is TagAlreadyLinkedException)
    assertEquals("pet-1", repository.linkedTags.single().petId)
  }

  @Test
  fun tagLinkedByAnotherOwnerIsRefused() = runTest {
    val repository =
        FakePetTagRepository(
            ownerId = "owner-1",
            initialTags =
                listOf(linkedTag("0000000000000000000000000000ABCD", "their-pet", "owner-2")),
        )
    writer.existingTagId = "0000000000000000000000000000ABCD"

    val result = TagLinker(repository).link(writer.writeOnNextTap("UNUSED00"), "pet-1")

    assertTrue(result.exceptionOrNull() is TagAlreadyLinkedException)
    assertEquals("owner-2", repository.linkedTags.single().ownerId)
  }

  @Test
  fun writeFailureLinksNothingAndKeepsTheReasonForTheRetryMessage() = runTest {
    TagWriteError.entries
        .filter { it != TagWriteError.NFC_UNAVAILABLE }
        .forEach { error ->
          writer.error = error

          val result = tapAndLink("pet-1")

          assertEquals(error, (result.exceptionOrNull() as TagWriteException).error)
          assertTrue(repository.linkedTags.isEmpty())
        }
  }

  @Test
  fun retryAfterAFailedLinkReusesTheTagWrittenTheFirstTime() = runTest {
    repository.failure = IllegalStateException("offline")
    assertTrue(tapAndLink("pet-1").isFailure)
    val writtenId = writer.writtenIds.single()

    // The tag now holds the URL of an id that points to nothing: the second tap reuses it.
    repository.failure = null
    writer.existingTagId = writtenId
    assertTrue(tapAndLink("pet-1").isSuccess)

    assertEquals(listOf(writtenId), writer.writtenIds)
    assertEquals(writtenId, repository.linkedTags.single().tagId)
  }

  @Test
  fun repositoryFailureWhileCheckingTheTagIsReturnedAndLinksNothing() = runTest {
    writer.existingTagId = "0000000000000000000000000000ABCD"
    repository.failure = IllegalStateException("offline")

    val result = tapAndLink("pet-1")

    assertEquals("offline", result.exceptionOrNull()?.message)
    assertTrue(repository.linkedTags.isEmpty())
  }
}
