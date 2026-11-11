// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.linktag

import androidx.lifecycle.viewModelScope
import com.android.spotted.R
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.fakes.FakePetTagRepository
import com.android.spotted.model.fakes.FakeTagWriter
import com.android.spotted.model.tag.PetTag
import com.android.spotted.model.tag.PetTagRepository
import com.android.spotted.model.tag.TagWriteError
import com.android.spotted.model.tag.TagWriteOutcome
import com.android.spotted.model.tag.TagWriter
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkTagViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private val auth = FakeAuthRepository()
  private val users = FakeUserRepository()
  private val pets = PetRepositoryLocal()
  private val writer = FakeTagWriter()
  private lateinit var ownerId: String
  private lateinit var tags: FakePetTagRepository

  private val owner
    get() =
        UserProfile(
            uid = ownerId,
            name = "Alice",
            phone = "+41 79 000 00 00",
            email = "alice@example.com",
            homeArea = "Rue du Lac 1, Lausanne",
        )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    runTest { signInWithAPetAndProfile() }
  }

  private suspend fun signInWithAPetAndProfile() {
    ownerId = auth.signInWithGoogle("token").getOrThrow().uid
    users.saveUser(owner)
    pets.addPet(Pet(id = PET_ID, ownerId = ownerId, name = "Rex", species = Species.DOG))
    tags = FakePetTagRepository(ownerId = ownerId)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun TestScope.loadedViewModel(
      tagRepository: PetTagRepository = tags,
      petId: String = PET_ID,
  ): LinkTagViewModel {
    val viewModel = LinkTagViewModel(petId, tagRepository, pets, users, auth)
    advanceUntilIdle()
    return viewModel
  }

  private fun TestScope.linkWith(viewModel: LinkTagViewModel, tagWriter: TagWriter = writer) {
    viewModel.start(tagWriter)
    advanceUntilIdle()
  }

  private fun LinkTagViewModel.assertErrorWith(message: Int) {
    assertEquals(LinkTagStep.Error, uiState.value.step)
    assertEquals(message, uiState.value.errorMessage)
    assertTrue(uiState.value.canStart)
  }

  @Test
  fun loadsThePetNameAndCanStart() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel()

        assertEquals("Rex", viewModel.uiState.value.petName)
        assertEquals(LinkTagStep.Idle, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.canStart)
      }

  @Test
  fun petThatCannotBeLoadedShowsAnErrorAndCannotStart() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(petId = "missing")

        assertEquals(LinkTagStep.Error, viewModel.uiState.value.step)
        assertEquals(R.string.link_tag_load_error, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.canStart)
        viewModel.start(writer)
        advanceUntilIdle()
        assertTrue(writer.writtenIds.isEmpty())
      }

  @Test
  fun successfulScanLinksTheTagWithTheChosenContacts() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onShowEmailChanged(false)

        linkWith(viewModel)

        assertEquals(LinkTagStep.Success, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.errorMessage)
        val tag = tags.linkedTags.single()
        assertEquals(writer.writtenIds.single(), tag.tagId)
        assertEquals(PET_ID, tag.petId)
        assertTrue(tag.showPhone)
        assertFalse(tag.showEmail)
        assertEquals(owner.phone, tag.contactPhone)
        assertNull(tag.contactEmail)
      }

  @Test
  fun theAddressIsNeverSavedOnTheTag() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel()

        linkWith(viewModel)

        val tag = tags.linkedTags.single()
        assertEquals(owner.phone, tag.contactPhone)
        assertEquals(owner.email, tag.contactEmail)
        assertFalse(owner.homeArea in listOf(tag.contactPhone, tag.contactEmail))
      }

  @Test
  fun eachWriteFailureEndsInErrorWithItsMessageAndLinksNothing() =
      runTest(testDispatcher) {
        val messages =
            mapOf(
                TagWriteError.TAG_LOST to R.string.link_tag_lost_error,
                TagWriteError.READ_ONLY to R.string.link_tag_read_only_error,
                TagWriteError.NOT_ENOUGH_SPACE to R.string.link_tag_not_enough_space_error,
                TagWriteError.UNSUPPORTED to R.string.link_tag_unsupported_error,
                TagWriteError.NFC_UNAVAILABLE to R.string.link_tag_nfc_unavailable_error,
            )
        assertEquals(TagWriteError.entries.toSet(), messages.keys)
        val viewModel = loadedViewModel()

        messages.forEach { (error, message) ->
          writer.error = error
          linkWith(viewModel)

          viewModel.assertErrorWith(message)
          assertTrue(tags.linkedTags.isEmpty())
        }
      }

  @Test
  fun tagLinkedToAnotherPetEndsInErrorAndIsLeftAsIs() =
      runTest(testDispatcher) {
        val existing =
            PetTag(
                tagId = "0000000000000000000000000000ABCD",
                petId = "other-pet",
                ownerId = ownerId,
                linkedAtMillis = 0L,
            )
        tags = FakePetTagRepository(ownerId = ownerId, initialTags = listOf(existing))
        writer.existingTagId = existing.tagId
        val viewModel = loadedViewModel()

        linkWith(viewModel)

        viewModel.assertErrorWith(R.string.link_tag_already_linked_error)
        assertEquals(listOf(existing), tags.linkedTags)
      }

  @Test
  fun linkFailureEndsInErrorAndLinksNothing() =
      runTest(testDispatcher) {
        tags.failure = IllegalStateException("offline")
        val viewModel = loadedViewModel()

        linkWith(viewModel)

        viewModel.assertErrorWith(R.string.link_tag_save_error)
        assertTrue(tags.linkedTags.isEmpty())
      }

  @Test
  fun contactsFailureEndsInErrorAndUnlinksTheTag() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(tagRepository = FailingContactsRepository(tags))

        linkWith(viewModel)

        viewModel.assertErrorWith(R.string.link_tag_save_error)
        assertTrue(tags.linkedTags.isEmpty())
      }

  @Test
  fun missingOwnerProfileEndsInErrorBeforeTheTagIsWritten() =
      runTest(testDispatcher) {
        users.deleteUser(ownerId)
        val viewModel = loadedViewModel()

        linkWith(viewModel)

        viewModel.assertErrorWith(R.string.link_tag_save_error)
        assertTrue(writer.writtenIds.isEmpty())
        assertTrue(tags.linkedTags.isEmpty())
      }

  @Test
  fun retryAfterAnErrorLinksTheTag() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel()
        writer.error = TagWriteError.TAG_LOST
        linkWith(viewModel)

        writer.error = null
        linkWith(viewModel)

        assertEquals(LinkTagStep.Success, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(PET_ID, tags.linkedTags.single().petId)
      }

  @Test
  fun waitsForTheTagThenSavesItsLink() =
      runTest(testDispatcher) {
        val tap = TapOnDemandWriter()
        val viewModel = loadedViewModel()

        linkWith(viewModel, tap)
        assertEquals(LinkTagStep.WaitingForTag, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.canStart)

        tap.outcome.complete(Result.success(TagWriteOutcome.Written(tap.newTagId!!)))
        advanceUntilIdle()

        assertEquals(LinkTagStep.Success, viewModel.uiState.value.step)
        assertEquals(tap.newTagId, tags.linkedTags.single().tagId)
      }

  @Test
  fun cancelWhileWaitingGoesBackToIdleAndLinksNothing() =
      runTest(testDispatcher) {
        val tap = TapOnDemandWriter()
        val viewModel = loadedViewModel()
        linkWith(viewModel, tap)

        viewModel.cancel()
        advanceUntilIdle()

        assertEquals(LinkTagStep.Idle, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.canStart)
        // A tap after the cancel is ignored.
        tap.outcome.complete(Result.success(TagWriteOutcome.Written(tap.newTagId!!)))
        advanceUntilIdle()
        assertEquals(LinkTagStep.Idle, viewModel.uiState.value.step)
        assertTrue(tags.linkedTags.isEmpty())
      }

  @Test
  fun cancelDoesNothingOnceLinked() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel()
        linkWith(viewModel)

        viewModel.cancel()

        assertEquals(LinkTagStep.Success, viewModel.uiState.value.step)
      }

  @Test
  fun cannotStartOrChangeContactsWhileWaiting() =
      runTest(testDispatcher) {
        val tap = TapOnDemandWriter()
        val viewModel = loadedViewModel()
        linkWith(viewModel, tap)

        viewModel.onShowPhoneChanged(false)
        linkWith(viewModel)

        assertTrue(viewModel.uiState.value.showPhone)
        assertTrue(writer.writtenIds.isEmpty())
        assertEquals(LinkTagStep.WaitingForTag, viewModel.uiState.value.step)
      }

  @Test
  fun cannotStartWithNoContactShown() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onShowPhoneChanged(false)
        viewModel.onShowEmailChanged(false)

        linkWith(viewModel)

        assertEquals(LinkTagStep.Idle, viewModel.uiState.value.step)
        assertTrue(writer.writtenIds.isEmpty())
      }

  @Test
  fun cancellationReturnedByARepositoryEndsInErrorInsteadOfStayingInWriting() =
      runTest(testDispatcher) {
        val cancelling =
            object : PetTagRepository by tags {
              override suspend fun linkTag(tagId: String, petId: String): Result<Unit> =
                  Result.failure(CancellationException("task cancelled"))
            }
        val viewModel = loadedViewModel(tagRepository = cancelling)

        linkWith(viewModel)

        viewModel.assertErrorWith(R.string.link_tag_save_error)
        assertTrue(tags.linkedTags.isEmpty())
      }

  @Test
  fun cancellationThrownByARepositoryEndsInErrorInsteadOfStayingInWriting() =
      runTest(testDispatcher) {
        // Like PetTagRepositoryFirestore when Tasks.await throws for a task Firebase cancelled.
        val cancelling =
            object : PetTagRepository by tags {
              override suspend fun linkTag(tagId: String, petId: String): Result<Unit> =
                  throw CancellationException("task cancelled")
            }
        val viewModel = loadedViewModel(tagRepository = cancelling)

        linkWith(viewModel)

        viewModel.assertErrorWith(R.string.link_tag_save_error)
        assertTrue(tags.linkedTags.isEmpty())
      }

  @Test
  fun failedUndoAfterAContactsFailureSaysTheTagStayedLinked() =
      runTest(testDispatcher) {
        val stuck =
            object : PetTagRepository by FailingContactsRepository(tags) {
              override suspend fun unlinkTag(tagId: String): Result<Unit> =
                  Result.failure(IllegalStateException("offline"))
            }
        val viewModel = loadedViewModel(tagRepository = stuck)

        linkWith(viewModel)

        viewModel.assertErrorWith(R.string.link_tag_contacts_not_saved_error)
        // Known gap: the tag stays linked with the default contacts.
        assertEquals(PET_ID, tags.linkedTags.single().petId)
      }

  @Test
  fun contactsThatThrowAreRolledBackLikeAFailure() =
      runTest(testDispatcher) {
        val throwing =
            object : PetTagRepository by tags {
              override suspend fun setVisibleContacts(
                  tagId: String,
                  showPhone: Boolean,
                  showEmail: Boolean,
                  phone: String?,
                  email: String?,
              ): Result<Unit> = throw IllegalStateException("offline")
            }
        val viewModel = loadedViewModel(tagRepository = throwing)

        linkWith(viewModel)

        viewModel.assertErrorWith(R.string.link_tag_save_error)
        assertTrue(tags.linkedTags.isEmpty())
      }

  @Test
  fun undoThatThrowsSaysTheTagStayedLinked() =
      runTest(testDispatcher) {
        val stuck =
            object : PetTagRepository by FailingContactsRepository(tags) {
              override suspend fun unlinkTag(tagId: String): Result<Unit> =
                  throw IllegalStateException("offline")
            }
        val viewModel = loadedViewModel(tagRepository = stuck)

        linkWith(viewModel)

        viewModel.assertErrorWith(R.string.link_tag_contacts_not_saved_error)
        assertEquals(PET_ID, tags.linkedTags.single().petId)
      }

  @Test
  fun closingTheScreenAfterTheTapStillSavesTheChosenContacts() =
      runTest(testDispatcher) {
        val contactsGate = CompletableDeferred<Unit>()
        val slowContacts =
            object : PetTagRepository by tags {
              override suspend fun setVisibleContacts(
                  tagId: String,
                  showPhone: Boolean,
                  showEmail: Boolean,
                  phone: String?,
                  email: String?,
              ): Result<Unit> {
                contactsGate.await()
                return tags.setVisibleContacts(tagId, showPhone, showEmail, phone, email)
              }
            }
        val viewModel = loadedViewModel(tagRepository = slowContacts)
        viewModel.onShowEmailChanged(false)
        linkWith(viewModel)
        assertEquals(LinkTagStep.Writing, viewModel.uiState.value.step)

        viewModel.viewModelScope.cancel() // The screen closes while the contacts are saved.
        contactsGate.complete(Unit)
        advanceUntilIdle()

        val tag = tags.linkedTags.single()
        assertFalse(tag.showEmail)
        assertNull(tag.contactEmail)
      }

  @Test
  fun closingTheScreenWhileWaitingLinksNothing() =
      runTest(testDispatcher) {
        val tap = TapOnDemandWriter()
        val viewModel = loadedViewModel()
        linkWith(viewModel, tap)

        viewModel.viewModelScope.cancel()
        tap.outcome.complete(Result.success(TagWriteOutcome.Written(tap.newTagId!!)))
        advanceUntilIdle()

        assertTrue(tags.linkedTags.isEmpty())
      }

  /** Waits until the test completes [outcome], like a user who taps the tag later. */
  private class TapOnDemandWriter : TagWriter {
    val outcome = CompletableDeferred<Result<TagWriteOutcome>>()
    var newTagId: String? = null

    override suspend fun writeOnNextTap(newTagId: String): Result<TagWriteOutcome> {
      this.newTagId = newTagId
      return outcome.await()
    }
  }

  /** Links tags normally, but cannot save which contacts are shown. */
  private class FailingContactsRepository(private val delegate: PetTagRepository) :
      PetTagRepository by delegate {
    override suspend fun setVisibleContacts(
        tagId: String,
        showPhone: Boolean,
        showEmail: Boolean,
        phone: String?,
        email: String?,
    ): Result<Unit> = Result.failure(IllegalStateException("offline"))
  }

  private companion object {
    const val PET_ID = "pet-1"
  }
}
