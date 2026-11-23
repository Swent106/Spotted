// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.User
import com.android.spotted.model.fakes.FakeAddressRepository
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.fakes.FakePendingSightingRepository
import com.android.spotted.model.sighting.PendingSighting
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.user.FakeUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
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

/**
 * Unit tests for the sightings waiting to be sent on Home: the banner's list, the "Waiting to send"
 * sheet, deleting one with a confirmation, and the "was sent" message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeWaitingSightingsTest {

  private val testDispatcher = StandardTestDispatcher()
  private val auth = TestAuthRepository()
  private lateinit var alertRepository: FakeAlertRepository
  private lateinit var addressRepository: FakeAddressRepository
  private lateinit var queue: FakePendingSightingRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    alertRepository = FakeAlertRepository()
    addressRepository = FakeAddressRepository(name = "Plainpalais, Geneva")
    queue = FakePendingSightingRepository()
    auth.currentUser.value = ALICE
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private class TestAuthRepository : AuthRepository {
    override val currentUser = MutableStateFlow<User?>(null)

    override suspend fun signInWithGoogle(idToken: String): Result<User> =
        Result.failure(UnsupportedOperationException())

    override suspend fun signOut(): Result<Unit> {
      currentUser.value = null
      return Result.success(Unit)
    }

    override suspend fun deleteAccount(): Result<Unit> = signOut()
  }

  private fun viewModel(withQueue: Boolean = true) =
      HomeViewModel(
          authRepository = auth,
          userRepository = FakeUserRepository(),
          petRepository = PetRepositoryLocal(),
          alertRepository = alertRepository,
          addressRepository = addressRepository,
          pendingSightingRepository = if (withQueue) queue else null,
      )

  private fun enqueue(vararg pending: PendingSighting) = runBlocking {
    pending.forEach { queue.add(it.sighting, it.photoUri) }
  }

  private fun publishMilo() = runBlocking {
    alertRepository.publishAlert(
        Alert(
            id = MILO_ALERT,
            petId = "milo",
            ownerId = "owner",
            lastKnownLocation = PLACE,
            lostAtMillis = 0L,
            petName = "Milo",
            petSpecies = Species.CAT,
        )
    )
  }

  // ===================== Listing =====================

  @Test
  fun withoutAQueue_nothingIsWaiting() =
      runTest(testDispatcher) {
        val vm = viewModel(withQueue = false)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.waitingSightings.isEmpty())
        vm.openWaitingSheet()
        vm.askToDeleteWaiting("s1")
        vm.confirmDeleteWaiting()
        assertFalse(vm.uiState.value.isWaitingSheetOpen)
      }

  @Test
  fun queuedSighting_isListedWithItsPetNamePlaceTimeAndPhoto() =
      runTest(testDispatcher) {
        publishMilo()
        enqueue(pending("s1", alertId = MILO_ALERT, photoUri = "file:///photo.jpg"))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            listOf(
                WaitingSighting(
                    id = "s1",
                    petName = "Milo",
                    species = Species.CAT,
                    photoUri = "file:///photo.jpg",
                    savedAtMillis = SEEN_AT,
                    place = "Plainpalais, Geneva",
                )
            ),
            vm.uiState.value.waitingSightings,
        )
      }

  @Test
  fun withoutAlertOrAddress_theRowShowsNoNameAndTheCoordinates() =
      runTest(testDispatcher) {
        addressRepository.failure = IllegalStateException("offline")
        enqueue(pending("s1"), pending("s2", alertId = "unknown-alert"))

        val vm = viewModel()
        advanceUntilIdle()

        val rows = vm.uiState.value.waitingSightings
        assertEquals(listOf("s1", "s2"), rows.map { it.id })
        assertTrue(rows.all { it.petName == null })
        assertEquals("46.2044, 6.1432", rows.first().place)
      }

  @Test
  fun aRowWhosePlaceWasNotFound_isLookedUpAgainOnTheNextChange() =
      runTest(testDispatcher) {
        addressRepository.failure = IllegalStateException("offline")
        enqueue(pending("s1"))
        val vm = viewModel()
        advanceUntilIdle()

        addressRepository.failure = null
        enqueue(pending("s2"))
        advanceUntilIdle()

        assertEquals(
            listOf("Plainpalais, Geneva", "Plainpalais, Geneva"),
            vm.uiState.value.waitingSightings.map { it.place },
        )
      }

  @Test
  fun aFoundRow_isNotLookedUpAgain() =
      runTest(testDispatcher) {
        enqueue(pending("s1"))
        val vm = viewModel()
        advanceUntilIdle()

        addressRepository.name = "Elsewhere"
        enqueue(pending("s2"))
        advanceUntilIdle()

        assertEquals(
            listOf("Plainpalais, Geneva", "Elsewhere"),
            vm.uiState.value.waitingSightings.map { it.place },
        )
      }

  @Test
  fun onlyTheSignedInUsersSightingsAreListed() =
      runTest(testDispatcher) {
        enqueue(pending("mine"), pending("bobs", reporterId = BOB.uid))

        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf("mine"), vm.uiState.value.waitingSightings.map { it.id })

        auth.currentUser.value = BOB
        advanceUntilIdle()
        assertEquals(listOf("bobs"), vm.uiState.value.waitingSightings.map { it.id })
      }

  @Test
  fun aQueueThatCantBeRead_listsNothing() =
      runTest(testDispatcher) {
        enqueue(pending("s1"))
        queue.queueFailure = IllegalStateException("queue")
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.waitingSightings.isEmpty())

        // Readable again: the next change lists it
        queue.queueFailure = null
        enqueue(pending("s2"))
        advanceUntilIdle()
        assertEquals(listOf("s1", "s2"), vm.uiState.value.waitingSightings.map { it.id })
      }

  // ===================== Sheet =====================

  @Test
  fun sheet_opensOnlyWhenASightingIsWaiting_andCloses() =
      runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.openWaitingSheet()
        assertFalse(vm.uiState.value.isWaitingSheetOpen)

        enqueue(pending("s1"))
        advanceUntilIdle()
        vm.openWaitingSheet()
        assertTrue(vm.uiState.value.isWaitingSheetOpen)

        vm.askToDeleteWaiting("s1")
        vm.closeWaitingSheet()
        assertFalse(vm.uiState.value.isWaitingSheetOpen)
        assertNull(vm.uiState.value.waitingDeleteId)
      }

  // ===================== Delete =====================

  @Test
  fun askToDelete_anUnknownSighting_isIgnored() =
      runTest(testDispatcher) {
        enqueue(pending("s1"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.askToDeleteWaiting("unknown")

        assertNull(vm.uiState.value.waitingDeleteId)
      }

  @Test
  fun deleteCancelled_keepsTheSighting() =
      runTest(testDispatcher) {
        enqueue(pending("s1"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.askToDeleteWaiting("s1")
        assertEquals("s1", vm.uiState.value.waitingDeleteId)
        vm.cancelDeleteWaiting()
        vm.confirmDeleteWaiting()
        advanceUntilIdle()

        assertNull(vm.uiState.value.waitingDeleteId)
        assertEquals(listOf("s1"), queue.pending.map { it.sighting.id })
        assertEquals(1, vm.uiState.value.waitingSightings.size)
      }

  @Test
  fun deleteConfirmed_removesTheSighting_closesTheSheet_andSaysNothingWasSent() =
      runTest(testDispatcher) {
        enqueue(pending("s1"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.openWaitingSheet()

        vm.askToDeleteWaiting("s1")
        vm.confirmDeleteWaiting()
        advanceUntilIdle()

        assertTrue(queue.pending.isEmpty())
        val state = vm.uiState.value
        assertTrue(state.waitingSightings.isEmpty())
        assertFalse(state.isWaitingSheetOpen)
        assertNull(state.waitingDeleteId)
        assertNull(state.sentSighting)
      }

  @Test
  fun deleteFailing_keepsTheSightingAndShowsAnError() =
      runTest(testDispatcher) {
        enqueue(pending("s1"))
        val vm = viewModel()
        advanceUntilIdle()
        queue.deleteFailure = IllegalStateException("disk")

        vm.askToDeleteWaiting("s1")
        vm.confirmDeleteWaiting()
        advanceUntilIdle()

        assertEquals(HomeViewModel.WAITING_DELETE_FAILED, vm.uiState.value.errorMessage)
        assertEquals(listOf("s1"), vm.uiState.value.waitingSightings.map { it.id })

        // Sent later: it is now told as sent, since the user's delete did not happen
        queue.deleteFailure = null
        queue.delete("s1")
        advanceUntilIdle()
        assertEquals("s1", vm.uiState.value.sentSighting?.id)
      }

  // ===================== Sent =====================

  @Test
  fun aSightingThatLeavesTheQueue_isToldAsSent_untilShown() =
      runTest(testDispatcher) {
        publishMilo()
        enqueue(pending("s1", alertId = MILO_ALERT))
        val vm = viewModel()
        advanceUntilIdle()
        assertNull(vm.uiState.value.sentSighting)

        // What the sender does once the sighting is sent
        queue.delete("s1")
        advanceUntilIdle()

        assertEquals("Milo", vm.uiState.value.sentSighting?.petName)
        assertTrue(vm.uiState.value.waitingSightings.isEmpty())
        vm.sentSightingShown()
        assertNull(vm.uiState.value.sentSighting)
      }

  @Test
  fun aSightingReplaced_withTheSameSize_updatesItsRow() =
      runTest(testDispatcher) {
        publishMilo()
        enqueue(pending("s1"))
        val vm = viewModel()
        advanceUntilIdle()
        assertNull(vm.uiState.value.waitingSightings.single().petName)

        enqueue(pending("s1", alertId = MILO_ALERT))
        advanceUntilIdle()

        assertEquals("Milo", vm.uiState.value.waitingSightings.single().petName)
      }

  @Test
  fun oneSent_andAnotherAdded_withTheSameSize_isToldAsSent_andListsTheNewOne() =
      runTest(testDispatcher) {
        publishMilo()
        enqueue(pending("s1", alertId = MILO_ALERT))
        val vm = viewModel()
        advanceUntilIdle()

        // Both before Home sees any of them: the size goes back to 1
        queue.delete("s1")
        enqueue(pending("s2"))
        advanceUntilIdle()

        assertEquals("s1", vm.uiState.value.sentSighting?.id)
        assertEquals(listOf("s2"), vm.uiState.value.waitingSightings.map { it.id })
      }

  @Test
  fun anotherUsersSightingSent_isNotTold() =
      runTest(testDispatcher) {
        enqueue(pending("bobs", reporterId = BOB.uid))
        val vm = viewModel()
        advanceUntilIdle()

        queue.delete("bobs")
        advanceUntilIdle()

        assertNull(vm.uiState.value.sentSighting)
      }

  private companion object {
    val ALICE = User(uid = "alice", email = "alice@example.com")
    val BOB = User(uid = "bob", email = "bob@example.com")
    val PLACE = Location(latitude = 46.2044, longitude = 6.1432)
    const val SEEN_AT = 1_700_000_000_000L
    const val MILO_ALERT = "alert-milo"

    fun pending(
        id: String,
        reporterId: String = ALICE.uid,
        alertId: String? = null,
        photoUri: String? = null,
    ) =
        PendingSighting(
            Sighting(
                id = id,
                reporterId = reporterId,
                location = PLACE,
                seenAtMillis = SEEN_AT,
                species = Species.CAT,
                alertId = alertId,
            ),
            photoUri,
        )
  }
}
