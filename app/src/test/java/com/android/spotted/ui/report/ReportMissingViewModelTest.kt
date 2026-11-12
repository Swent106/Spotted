// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.report

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.fakes.FakeAlertRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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

/**
 * Unit tests for [ReportMissingViewModel], using fakes for every repository and a fixed clock.
 *
 * Covers loading the owner's pets, the required pet and location, the automatic timestamp, the
 * content of the published alert, and publish failures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportMissingViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var alertRepository: FakeAlertRepository
  private lateinit var petRepository: PetRepositoryLocal
  private lateinit var authRepository: FakeAuthRepository

  @Before
  fun setUp() {
    // viewModelScope runs on Dispatchers.Main, which does not exist in unit tests
    Dispatchers.setMain(testDispatcher)
    alertRepository = FakeAlertRepository()
    petRepository = PetRepositoryLocal()
    authRepository = FakeAuthRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ===================== Helpers =====================

  private fun signIn() = runBlocking { authRepository.signInWithGoogle("fake-token").getOrThrow() }

  private fun addPets(vararg pets: Pet) = runBlocking { pets.forEach { petRepository.addPet(it) } }

  private fun viewModel(petRepository: PetRepository = this.petRepository) =
      ReportMissingViewModel(
          alertRepository = alertRepository,
          petRepository = petRepository,
          authRepository = authRepository,
          now = { NOW },
      )

  /** Signs in, gives the owner [pets], and returns a ViewModel whose pets are loaded. */
  private fun TestScope.loadedViewModel(vararg pets: Pet): ReportMissingViewModel {
    signIn()
    addPets(*pets)
    val viewModel = viewModel()
    advanceUntilIdle()
    return viewModel
  }

  private class FailingPetRepository : PetRepository {
    override fun getNewId() = "unused"

    override suspend fun addPet(pet: Pet) = Result.failure<Unit>(Exception("offline"))

    override suspend fun updatePet(pet: Pet) = Result.failure<Unit>(Exception("offline"))

    override suspend fun getPet(id: String) = Result.failure<Pet>(Exception("offline"))

    override suspend fun getPetsByOwner(ownerId: String) =
        Result.failure<List<Pet>>(Exception("offline"))

    override suspend fun deletePet(id: String) = Result.failure<Unit>(Exception("offline"))
  }

  // ===================== Loading pets =====================

  @Test
  fun init_signedIn_loadsOnlyTheOwnersPets() =
      runTest(testDispatcher) {
        val otherOwnersPet = pet(id = "other", ownerId = "someone-else")
        val viewModel = loadedViewModel(LUNA, MILO, otherOwnersPet)

        val state = viewModel.uiState.value
        assertEquals(setOf(LUNA, MILO), state.pets.toSet())
        assertFalse(state.isLoadingPets)
        assertNull(state.errorMessage)
      }

  @Test
  fun init_isLoadingPets_untilThePetsArrive() =
      runTest(testDispatcher) {
        signIn()
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.isLoadingPets)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoadingPets)
      }

  @Test
  fun init_singlePet_isPreselected() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA)

        assertEquals(LUNA, viewModel.uiState.value.selectedPet)
      }

  @Test
  fun init_severalPets_noneIsPreselected() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA, MILO)

        assertNull(viewModel.uiState.value.selectedPet)
      }

  @Test
  fun init_notSignedIn_showsErrorAndLoadsNothing() =
      runTest(testDispatcher) {
        addPets(LUNA)
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReportMissingViewModel.NOT_SIGNED_IN, state.errorMessage)
        assertTrue(state.pets.isEmpty())
        assertFalse(state.isLoadingPets)
      }

  @Test
  fun init_petsFailToLoad_showsError() =
      runTest(testDispatcher) {
        signIn()
        val viewModel = viewModel(petRepository = FailingPetRepository())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReportMissingViewModel.LOAD_PETS_FAILED, state.errorMessage)
        assertTrue(state.pets.isEmpty())
        assertFalse(state.isLoadingPets)
      }

  @Test
  fun loadPets_calledAgain_picksUpNewPets() =
      runTest(testDispatcher) {
        signIn()
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.pets.isEmpty())

        addPets(LUNA)
        viewModel.loadPets()
        advanceUntilIdle()

        assertEquals(listOf(LUNA), viewModel.uiState.value.pets)
      }

  // ===================== Pet and location are required =====================

  @Test
  fun selectPet_ownPet_isSelected() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA, MILO)

        viewModel.selectPet(MILO.id)

        assertEquals(MILO, viewModel.uiState.value.selectedPet)
      }

  @Test
  fun selectPet_unknownId_isIgnored() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA, MILO)
        viewModel.selectPet(MILO.id)

        viewModel.selectPet("not-my-pet")

        assertEquals(MILO, viewModel.uiState.value.selectedPet)
      }

  @Test
  fun canPublish_requiresBothAPetAndALocation() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA, MILO)
        assertFalse(viewModel.uiState.value.canPublish)

        viewModel.selectPet(LUNA.id)
        assertFalse(viewModel.uiState.value.canPublish)

        viewModel.setLocation(PARC_BERTRAND)
        assertTrue(viewModel.uiState.value.canPublish)
      }

  @Test
  fun publish_withoutLocation_publishesNothing() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA)

        viewModel.publish()
        advanceUntilIdle()

        assertTrue(alertRepository.publishedAlerts.isEmpty())
        assertFalse(viewModel.uiState.value.published)
      }

  @Test
  fun publish_withoutPet_publishesNothing() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA, MILO)
        viewModel.setLocation(PARC_BERTRAND)

        viewModel.publish()
        advanceUntilIdle()

        assertTrue(alertRepository.publishedAlerts.isEmpty())
        assertFalse(viewModel.uiState.value.published)
      }

  // ===================== Publishing =====================

  @Test
  fun publish_buildsTheAlertFromThePetTheLocationAndTheClock() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA)
        viewModel.setLocation(PARC_BERTRAND)

        viewModel.publish()
        advanceUntilIdle()

        val expected =
            Alert(
                id = "alert-1",
                petId = LUNA.id,
                ownerId = OWNER_ID,
                lastKnownLocation = PARC_BERTRAND,
                lostAtMillis = NOW,
                status = AlertStatus.OPEN,
                petName = LUNA.name,
                petSpecies = LUNA.species,
                petPhotoUrl = LUNA.photoUrl,
                petAllergies = LUNA.allergies,
                petBehaviors = LUNA.behaviors,
            )
        assertEquals(listOf(expected), alertRepository.publishedAlerts)
        val state = viewModel.uiState.value
        assertTrue(state.published)
        assertFalse(state.isPublishing)
        assertFalse(state.canPublish)
      }

  @Test
  fun publish_isPublishing_untilTheRepositoryAnswers() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA)
        viewModel.setLocation(PARC_BERTRAND)

        viewModel.publish()

        assertTrue(viewModel.uiState.value.isPublishing)
        assertFalse(viewModel.uiState.value.canPublish)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isPublishing)
      }

  @Test
  fun publish_tappedTwice_publishesOneAlert() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA)
        viewModel.setLocation(PARC_BERTRAND)

        viewModel.publish()
        viewModel.publish()
        advanceUntilIdle()
        viewModel.publish()
        advanceUntilIdle()

        assertEquals(1, alertRepository.publishedAlerts.size)
      }

  @Test
  fun publish_failure_keepsTheFormAndShowsAnError_thenRetrySucceeds() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA)
        viewModel.setLocation(PARC_BERTRAND)
        alertRepository.failure = Exception("offline")

        viewModel.publish()
        advanceUntilIdle()

        val failed = viewModel.uiState.value
        assertEquals(ReportMissingViewModel.PUBLISH_FAILED, failed.errorMessage)
        assertFalse(failed.published)
        assertEquals(LUNA, failed.selectedPet)
        assertEquals(PARC_BERTRAND, failed.location)
        assertTrue(failed.canPublish)

        alertRepository.failure = null
        viewModel.publish()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.published)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, alertRepository.publishedAlerts.size)
      }

  @Test
  fun publish_afterSigningOut_showsErrorAndPublishesNothing() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA)
        viewModel.setLocation(PARC_BERTRAND)
        authRepository.signOut()

        viewModel.publish()
        advanceUntilIdle()

        assertEquals(ReportMissingViewModel.NOT_SIGNED_IN, viewModel.uiState.value.errorMessage)
        assertTrue(alertRepository.publishedAlerts.isEmpty())
      }

  @Test
  fun clearErrorMsg_removesTheError() =
      runTest(testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(ReportMissingViewModel.NOT_SIGNED_IN, viewModel.uiState.value.errorMessage)

        viewModel.clearErrorMsg()

        assertNull(viewModel.uiState.value.errorMessage)
      }

  // ===================== Pets already missing =====================

  /** Publishes an open alert for [pet] directly in the repository, and returns it. */
  private fun openAlertFor(pet: Pet, id: String = "open-${pet.id}"): Alert = runBlocking {
    val alert =
        Alert(
            id = id,
            petId = pet.id,
            ownerId = pet.ownerId,
            lastKnownLocation = PARC_BERTRAND,
            lostAtMillis = NOW - 1,
            petName = pet.name,
            petSpecies = pet.species,
        )
    alertRepository.publishAlert(alert).getOrThrow()
    alert
  }

  @Test
  fun init_petAlreadyMissing_isMarkedAndCannotBeSelected() =
      runTest(testDispatcher) {
        openAlertFor(LUNA)
        val viewModel = loadedViewModel(LUNA)
        viewModel.setLocation(PARC_BERTRAND)

        viewModel.selectPet(LUNA.id)

        val state = viewModel.uiState.value
        assertTrue(state.isAlreadyMissing(LUNA))
        assertNull(state.selectedPet)
        assertFalse(state.canPublish)
      }

  @Test
  fun selectPet_alreadyMissing_offersToOpenItsAlert() =
      runTest(testDispatcher) {
        val alert = openAlertFor(LUNA)
        val viewModel = loadedViewModel(LUNA, MILO)

        viewModel.selectPet(LUNA.id)
        assertEquals(LUNA, viewModel.uiState.value.alreadyMissingPet)

        viewModel.openExistingAlert()

        assertEquals(alert.id, viewModel.uiState.value.openAlertId)
        assertNull(viewModel.uiState.value.alreadyMissingPet)
      }

  @Test
  fun dismissAlreadyMissing_hidesTheOfferWithoutOpeningTheAlert() =
      runTest(testDispatcher) {
        openAlertFor(LUNA)
        val viewModel = loadedViewModel(LUNA, MILO)
        viewModel.selectPet(LUNA.id)

        viewModel.dismissAlreadyMissing()

        assertNull(viewModel.uiState.value.alreadyMissingPet)
        assertNull(viewModel.uiState.value.openAlertId)
      }

  @Test
  fun init_allPetsMissing_saysSoAndPublishStaysDisabled() =
      runTest(testDispatcher) {
        openAlertFor(LUNA)
        openAlertFor(MILO)
        val viewModel = loadedViewModel(LUNA, MILO)
        viewModel.setLocation(PARC_BERTRAND)
        viewModel.selectPet(MILO.id)

        val state = viewModel.uiState.value
        assertTrue(state.allPetsMissing)
        assertFalse(state.canPublish)
      }

  @Test
  fun alertClosed_petCanBeReportedMissingAgain() =
      runTest(testDispatcher) {
        val alert = openAlertFor(LUNA)
        runBlocking { alertRepository.closeAlert(alert.id).getOrThrow() }
        val viewModel = loadedViewModel(LUNA)
        viewModel.setLocation(PARC_BERTRAND)

        viewModel.publish()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.published)
        assertEquals(2, alertRepository.publishedAlerts.size)
      }

  @Test
  fun publish_refusedBecauseReportedMeanwhile_showsMessageAndMarksThePet() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(LUNA)
        viewModel.setLocation(PARC_BERTRAND)
        // Reported from another device after the screen was loaded
        val other = openAlertFor(LUNA)

        viewModel.publish()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReportMissingViewModel.ALREADY_MISSING, state.errorMessage)
        assertFalse(state.published)
        assertEquals(other.id, state.openAlertIdByPetId[LUNA.id])
        assertFalse(state.canPublish)
        assertEquals(listOf(other), alertRepository.publishedAlerts)
      }

  @Test
  fun init_openAlertsFailToLoad_petsStaySelectable() =
      runTest(testDispatcher) {
        alertRepository.failure = Exception("offline")
        val viewModel = loadedViewModel(LUNA)

        val state = viewModel.uiState.value
        assertEquals(LUNA, state.selectedPet)
        assertNull(state.errorMessage)
      }

  private companion object {
    /** The uid that [FakeAuthRepository] gives to every signed-in user. */
    const val OWNER_ID = "fake_uid_from_token"
    const val NOW = 1_700_000_000_000L
    val PARC_BERTRAND = Location(latitude = 46.1925, longitude = 6.1580)

    fun pet(id: String, ownerId: String = OWNER_ID, name: String = id) =
        Pet(id = id, ownerId = ownerId, name = name, species = Species.CAT)

    val LUNA =
        Pet(
            id = "luna",
            ownerId = OWNER_ID,
            name = "Luna",
            species = Species.CAT,
            photoUrl = "https://example.com/luna.jpg",
            allergies = listOf("chicken"),
            behaviors = setOf(Behavior.FEARFUL_OF_STRANGERS),
        )
    val MILO = pet(id = "milo", name = "Milo")
  }
}
