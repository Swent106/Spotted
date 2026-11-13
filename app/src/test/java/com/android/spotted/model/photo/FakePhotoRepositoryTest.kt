// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.photo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePhotoRepositoryTest {
  private val repository = FakePhotoRepository()

  @Test
  fun uploadPetPhoto_returnsUrlAndRecordsUpload() = runTest {
    val url = repository.uploadPetPhoto("owner-1", "pet-1", "content://photos/1").getOrThrow()

    assertTrue(url.startsWith(FakePhotoRepository.FAKE_URL_PREFIX))
    assertEquals(mapOf(url to "content://photos/1"), repository.uploadedPhotos)
  }

  @Test
  fun uploadPetPhoto_returnsDifferentUrlsForDifferentPets() = runTest {
    val firstUrl = repository.uploadPetPhoto("owner-1", "pet-1", "content://photos/1").getOrThrow()
    val secondUrl = repository.uploadPetPhoto("owner-1", "pet-2", "content://photos/2").getOrThrow()

    assertNotEquals(firstUrl, secondUrl)
    assertEquals(2, repository.uploadedPhotos.size)
  }

  @Test
  fun uploadPetPhoto_replacesPreviousPhotoOfSamePet() = runTest {
    repository.uploadPetPhoto("owner-1", "pet-1", "content://photos/old")

    val url = repository.uploadPetPhoto("owner-1", "pet-1", "content://photos/new").getOrThrow()

    assertEquals(mapOf(url to "content://photos/new"), repository.uploadedPhotos)
  }

  @Test
  fun uploadPetPhoto_whenFailureIsSet_returnsFailureAndStoresNothing() = runTest {
    val error = IllegalStateException("upload failed")
    repository.failure = error

    val result = repository.uploadPetPhoto("owner-1", "pet-1", "content://photos/1")

    assertTrue(result.isFailure)
    assertSame(error, result.exceptionOrNull())
    assertTrue(repository.uploadedPhotos.isEmpty())
  }

  @Test
  fun deletePetPhoto_removesOnlyTheRequestedPhotoAndMissingPhotoIsSuccessful() = runTest {
    val firstUrl = repository.uploadPetPhoto("owner-1", "pet-1", "content://photos/1").getOrThrow()
    val secondUrl = repository.uploadPetPhoto("owner-1", "pet-2", "content://photos/2").getOrThrow()

    assertTrue(repository.deletePetPhoto("owner-1", "pet-1").isSuccess)
    assertEquals(mapOf(secondUrl to "content://photos/2"), repository.uploadedPhotos)
    assertTrue(repository.deletePetPhoto("owner-1", "pet-1").isSuccess)
    assertEquals(mapOf(secondUrl to "content://photos/2"), repository.uploadedPhotos)
    assertTrue(repository.uploadedPhotos.containsKey(firstUrl).not())
  }

  @Test
  fun deleteProfilePhoto_removesOnlyThatUsersPhoto_andFailsWhenFailureIsSet() = runTest {
    repository.uploadProfilePhoto("user-1", "content://photos/1").getOrThrow()
    val otherUrl = repository.uploadProfilePhoto("user-2", "content://photos/2").getOrThrow()

    assertTrue(repository.deleteProfilePhoto("user-1").isSuccess)
    assertTrue("a missing photo succeeds", repository.deleteProfilePhoto("user-1").isSuccess)
    assertEquals(mapOf(otherUrl to "content://photos/2"), repository.uploadedPhotos)

    repository.failure = IllegalStateException("delete failed")
    assertTrue(repository.deleteProfilePhoto("user-2").isFailure)
    assertEquals(mapOf(otherUrl to "content://photos/2"), repository.uploadedPhotos)
  }

  @Test
  fun deletePetPhoto_whenFailureIsSet_returnsFailureAndKeepsThePhoto() = runTest {
    val url = repository.uploadPetPhoto("owner-1", "pet-1", "content://photos/1").getOrThrow()
    val error = IllegalStateException("delete failed")
    repository.failure = error

    val result = repository.deletePetPhoto("owner-1", "pet-1")

    assertTrue(result.isFailure)
    assertSame(error, result.exceptionOrNull())
    assertEquals(mapOf(url to "content://photos/1"), repository.uploadedPhotos)
  }
}
