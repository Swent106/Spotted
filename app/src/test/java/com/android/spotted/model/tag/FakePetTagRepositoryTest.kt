// Portions of this code were generated with the help of Copilot.
package com.android.spotted.model.tag

import com.android.spotted.model.fakes.FakePetTagRepository
import com.android.spotted.model.user.UserProfile
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
            ownerProfile =
                UserProfile(
                    uid = "owner-1",
                    name = "Jamie",
                    phone = "+41 79 123 45 67",
                    email = "jamie@example.com",
                ),
        )

    assertTrue(repository.linkTag("A1B2C3D4", "pet-1").isSuccess)
    linkedAtMillis = 200L
    assertTrue(repository.linkTag("A1B2C3D4", "pet-2").isSuccess)
    assertEquals(
        PetTag(
            "A1B2C3D4",
            "pet-2",
            "owner-1",
            200L,
            contactPhone = "+41 79 123 45 67",
            contactEmail = "jamie@example.com",
        ),
        repository.linkedTags.single(),
    )
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

  @Test
  fun setVisibleContacts_hidesPhoneAndDropsSuppliedValue() = runTest {
    val tag =
        PetTag(
            "A1B2C3D4",
            "pet-1",
            "owner-1",
            100L,
            contactPhone = "+41 79 123 45 67",
            contactEmail = "jamie@example.com",
        )
    val repository = FakePetTagRepository(ownerId = "owner-1", initialTags = listOf(tag))

    repository
        .setVisibleContacts(
            tag.tagId,
            showPhone = false,
            showEmail = true,
            phone = "+41 79 999 99 99",
            email = "shown@example.com",
        )
        .getOrThrow()
    assertEquals(
        tag.copy(showPhone = false, contactPhone = null, contactEmail = "shown@example.com"),
        repository.linkedTags.single(),
    )
  }

  @Test
  fun setVisibleContacts_hidesEmailAndDropsSuppliedValue() = runTest {
    val tag =
        PetTag(
            "A1B2C3D4",
            "pet-1",
            "owner-1",
            100L,
            contactPhone = "+41 79 123 45 67",
            contactEmail = "jamie@example.com",
        )
    val repository = FakePetTagRepository(ownerId = "owner-1", initialTags = listOf(tag))

    repository
        .setVisibleContacts(
            tag.tagId,
            showPhone = true,
            showEmail = false,
            phone = "+41 79 123 45 67",
            email = "hidden@example.com",
        )
        .getOrThrow()
    assertEquals(
        tag.copy(showEmail = false, contactEmail = null),
        repository.linkedTags.single(),
    )
  }

  @Test
  fun setVisibleContacts_refusesToHideBothContactsWithoutChangingTag() = runTest {
    val tag = PetTag("A1B2C3D4", "pet-1", "owner-1", 100L)
    val repository = FakePetTagRepository(ownerId = "owner-1", initialTags = listOf(tag))

    val result =
        repository.setVisibleContacts(
            tag.tagId,
            showPhone = false,
            showEmail = false,
            phone = "+41 79 123 45 67",
            email = "jamie@example.com",
        )

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals(listOf(tag), repository.linkedTags)
  }

  @Test
  fun setVisibleContacts_returnsFailuresForMissingTagsAndConfiguredErrors() = runTest {
    val repository = FakePetTagRepository(ownerId = "owner-1")

    assertTrue(
        repository
            .setVisibleContacts(
                "missing",
                showPhone = true,
                showEmail = false,
                phone = "shown",
                email = null,
            )
            .exceptionOrNull() is NoSuchElementException
    )

    val failure = IllegalStateException("configured failure")
    repository.failure = failure
    assertSame(
        failure,
        repository
            .setVisibleContacts(
                "missing",
                showPhone = true,
                showEmail = false,
                phone = "shown",
                email = null,
            )
            .exceptionOrNull(),
    )
  }

  @Test
  fun setVisibleContacts_refusesTagsOwnedByAnotherUser() = runTest {
    val tag = PetTag("A1B2C3D4", "pet-2", "owner-2", 100L)
    val repository = FakePetTagRepository(ownerId = "owner-1", initialTags = listOf(tag))

    val result =
        repository.setVisibleContacts(
            tag.tagId,
            showPhone = false,
            showEmail = true,
            phone = null,
            email = "visible@example.com",
        )

    assertTrue(result.exceptionOrNull() is SecurityException)
    assertEquals(listOf(tag), repository.linkedTags)
  }
}
