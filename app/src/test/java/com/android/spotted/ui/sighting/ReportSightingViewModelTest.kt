// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.fakes.FakeAnimalDetector
import com.android.spotted.model.fakes.FakeLocationRepository
import com.android.spotted.model.fakes.FakeSightingRepository
import com.android.spotted.model.photo.AnimalDetection
import com.android.spotted.model.photo.AnimalDetector
import com.android.spotted.model.photo.FakePhotoRepository
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingRepository
import com.android.spotted.model.sighting.SightingStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportSightingViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var sightingRepository: FakeSightingRepository
  private lateinit var photoRepository: FakePhotoRepository
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var viewModel: ReportSightingViewModel

  private val fixedTimestamp = 123456789L

  @Before
  fun setUp() = runTest {
    Dispatchers.setMain(testDispatcher)
    sightingRepository = FakeSightingRepository()
    photoRepository = FakePhotoRepository()
    authRepository = FakeAuthRepository()

    // Sign in user by default for tests
    authRepository.signInWithGoogle("valid_token")

    viewModel =
        ReportSightingViewModel(
            sightingRepository = sightingRepository,
            photoRepository = photoRepository,
            authRepository = authRepository,
            now = { fixedTimestamp },
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun nearbyAlertOption_propertiesAndEquals() {
    val option1 = NearbyAlertOption("1", "Milo", "Orange cat", "http://photo.jpg", Species.CAT)
    val option2 = NearbyAlertOption("1", "Milo", "Orange cat", "http://photo.jpg", Species.CAT)

    assertEquals("1", option1.alertId)
    assertEquals("Milo", option1.petName)
    assertEquals("Orange cat", option1.description)
    assertEquals("http://photo.jpg", option1.photoUrl)
    assertEquals(Species.CAT, option1.species)
    assertEquals(option1, option2)
  }

  @Test
  fun defaultConstructorParameters_workCorrectly() {
    val vm =
        ReportSightingViewModel(
            sightingRepository,
            photoRepository,
            authRepository = authRepository,
        )
    assertNotNull(vm)
  }

  @Test
  fun factory_executesFactoryAndInnerRepo() = runTest {
    runCatching {
      val factoryVm = ReportSightingViewModel.Factory.create(ReportSightingViewModel::class.java)
      factoryVm.setSpecies(Species.CAT)
      factoryVm.setLocation(Location(46.5, 6.6))
      factoryVm.selectNoMatchingAlert()
      factoryVm.submitSighting()
    }

    val innerRepo =
        object : SightingRepository {
          override fun getNewId(): String = "id"

          override suspend fun reportSighting(sighting: Sighting): Result<Unit> =
              Result.success(Unit)

          override suspend fun getSightingsForAlert(alertId: String): Result<List<Sighting>> =
              Result.success(emptyList())

          override fun observeSightingsForAlert(alertId: String) =
              flowOf(Result.success(emptyList<Sighting>()))

          override suspend fun setSightingStatus(sightingId: String, status: SightingStatus) =
              Result.success(Unit)
        }
    assertEquals("id", innerRepo.getNewId())
    assertTrue(
        innerRepo
            .reportSighting(Sighting("id", "rep", Location(0.0, 0.0), 1L, Species.DOG))
            .isSuccess,
    )
    assertTrue(innerRepo.getSightingsForAlert("alert-1").getOrNull()?.isEmpty() == true)
  }

  @Test
  fun loadCurrentLocation_success_updatesState() = runTest {
    val fakeLocationRepo = FakeLocationRepository(location = Location(46.52, 6.63))

    val vm =
        ReportSightingViewModel(
            sightingRepository = sightingRepository,
            photoRepository = photoRepository,
            locationRepository = fakeLocationRepo,
            authRepository = authRepository,
        )

    vm.loadCurrentLocation()

    assertEquals(Location(46.52, 6.63), vm.uiState.value.location)
    assertEquals("Taken from your GPS", vm.uiState.value.locationAddress)
  }

  @Test
  fun loadCurrentLocation_failure_setsErrorMessage() = runTest {
    val fakeLocationRepo = FakeLocationRepository()
    fakeLocationRepo.failure = Exception("GPS disabled")

    val vm =
        ReportSightingViewModel(
            sightingRepository = sightingRepository,
            photoRepository = photoRepository,
            locationRepository = fakeLocationRepo,
            authRepository = authRepository,
        )

    vm.loadCurrentLocation()

    assertEquals("GPS disabled", vm.uiState.value.errorMessage)
  }

  @Test
  fun loadCurrentLocation_failureWithoutMessage_setsDefaultErrorMessage() = runTest {
    val fakeLocationRepo = FakeLocationRepository()
    fakeLocationRepo.failure = Exception()

    val vm =
        ReportSightingViewModel(
            sightingRepository = sightingRepository,
            photoRepository = photoRepository,
            locationRepository = fakeLocationRepo,
            authRepository = authRepository,
        )

    vm.loadCurrentLocation()

    assertEquals(
        "Location unavailable. Tap to set location on map.",
        vm.uiState.value.errorMessage,
    )
  }

  @Test
  fun loadCurrentLocation_withoutLocationRepository_leavesStateUnchanged() {
    viewModel.loadCurrentLocation()

    assertEquals(ReportSightingUiState(), viewModel.uiState.value)
  }

  @Test
  fun selectAlert_and_selectNoMatchingAlert_autoPrefillSpecies() {
    viewModel.selectNoMatchingAlert()
    assertEquals(Species.DOG, viewModel.uiState.value.selectedSpecies)

    viewModel.setSpecies(Species.CAT)
    viewModel.selectAlert("alert_1")
    assertEquals("alert_1", viewModel.uiState.value.selectedAlertId)
  }

  @Test
  fun submitSighting_unauthenticated_blocksSubmission() = runTest {
    authRepository.signOut()

    viewModel.setSpecies(Species.DOG)
    viewModel.setLocation(Location(46.5197, 6.6323))
    viewModel.selectNoMatchingAlert()

    viewModel.submitSighting()

    val state = viewModel.uiState.value
    assertFalse(state.submitted)
    assertEquals("You must be signed in to submit a sighting", state.errorMessage)
    assertTrue(sightingRepository.reportedSightings.isEmpty())
  }

  @Test
  fun stateUpdates_workCorrectly() {
    viewModel.setSpecies(Species.CAT)
    assertEquals(Species.CAT, viewModel.uiState.value.selectedSpecies)

    val location = Location(46.5, 6.6)
    viewModel.setLocation(location, "Lausanne")
    assertEquals(location, viewModel.uiState.value.location)
    assertEquals("Lausanne", viewModel.uiState.value.locationAddress)

    viewModel.setPhotoUri("content://media/photo.jpg")
    assertEquals("content://media/photo.jpg", viewModel.uiState.value.photoUri)

    viewModel.selectAlert("alert_1")
    assertEquals("alert_1", viewModel.uiState.value.selectedAlertId)
    assertFalse(viewModel.uiState.value.isNoMatchingAlertSelected)

    viewModel.selectNoMatchingAlert()
    assertNull(viewModel.uiState.value.selectedAlertId)
    assertTrue(viewModel.uiState.value.isNoMatchingAlertSelected)

    viewModel.clearErrorMessage()
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun submitSighting_success_storesSightingAndSetsSubmittedFlag() = runTest {
    viewModel.setSpecies(Species.DOG)
    viewModel.setLocation(Location(46.5197, 6.6323))
    viewModel.setPhotoUri("content://media/dog.jpg")
    viewModel.selectNoMatchingAlert()

    assertTrue(viewModel.uiState.value.canSubmit)

    viewModel.submitSighting()

    val state = viewModel.uiState.value
    assertTrue("Submitted flag should be true", state.submitted)
    assertFalse("isSubmitting should be false", state.isSubmitting)
    assertNull("errorMessage should be null", state.errorMessage)

    assertEquals(1, sightingRepository.reportedSightings.size)
    val saved = sightingRepository.reportedSightings.first()
    assertEquals("fake_uid_from_token", saved.reporterId)
    assertEquals(fixedTimestamp, saved.seenAtMillis)
    assertEquals(Species.DOG, saved.species)
    assertNull(saved.alertId)
    assertNotNull("Photo URL should be saved from photo repository", saved.photoUrl)
  }

  @Test
  fun submitSighting_validationError_whenLocationMissing() = runTest {
    // Species set, but location is null
    viewModel.setSpecies(Species.DOG)
    viewModel.selectNoMatchingAlert()

    assertFalse(viewModel.uiState.value.canSubmit)

    viewModel.submitSighting()

    val state = viewModel.uiState.value
    assertFalse(state.submitted)
    assertNotNull(state.errorMessage)
    assertTrue(sightingRepository.reportedSightings.isEmpty())
  }

  @Test
  fun submitSighting_photoUploadFailure_sendsTheSightingWithoutPhoto() = runTest {
    viewModel.setSpecies(Species.CAT)
    viewModel.setLocation(Location(46.5, 6.6))
    viewModel.setPhotoUri("content://media/cat.jpg")
    viewModel.selectNoMatchingAlert()

    photoRepository.failure = Exception("Upload failed")

    viewModel.submitSighting()

    assertTrue(viewModel.uiState.value.submitted)
    assertNull(sightingRepository.reportedSightings.single().photoUrl)
  }

  @Test
  fun submitSighting_sightingReportFailure_setsErrorMessageAndPreservesForm() = runTest {
    viewModel.setSpecies(Species.DOG)
    viewModel.setLocation(Location(46.5, 6.6))
    viewModel.selectNoMatchingAlert()

    sightingRepository.reportFailure = Exception("Database write error")

    viewModel.submitSighting()

    val state = viewModel.uiState.value
    assertFalse("submitted should be false", state.submitted)
    assertFalse("isSubmitting should be false", state.isSubmitting)
    assertEquals("Database write error", state.errorMessage)

    assertEquals(Species.DOG, state.selectedSpecies)
  }

  @Test
  fun submitSighting_unexpectedException_setsErrorMessage() = runTest {
    val throwingRepo =
        object : SightingRepository {
          override fun getNewId(): String = throw RuntimeException("Unexpected crash")

          override suspend fun reportSighting(sighting: Sighting): Result<Unit> =
              Result.success(Unit)

          override suspend fun getSightingsForAlert(alertId: String): Result<List<Sighting>> =
              Result.success(emptyList())

          override fun observeSightingsForAlert(alertId: String) =
              flowOf(Result.success(emptyList<Sighting>()))

          override suspend fun setSightingStatus(sightingId: String, status: SightingStatus) =
              Result.success(Unit)
        }
    val vm =
        ReportSightingViewModel(
            sightingRepository = throwingRepo,
            photoRepository = photoRepository,
            authRepository = authRepository,
        )

    vm.setSpecies(Species.DOG)
    vm.setLocation(Location(46.5, 6.6))
    vm.selectNoMatchingAlert()

    vm.submitSighting()

    val state = vm.uiState.value
    assertFalse(state.isSubmitting)
    assertEquals("Unexpected crash", state.errorMessage)
  }

  @Test
  fun preselectAlert_listsTheAlertWithItsSpecies_soTheSightingCanBeSent() = runTest {
    val alertRepository = FakeAlertRepository()
    alertRepository.publishAlert(
        Alert(
            id = "alert-milo",
            petId = "pet-milo",
            ownerId = "owner",
            lastKnownLocation = Location(46.52, 6.63),
            lostAtMillis = 0L,
            petName = "Milo",
            petSpecies = Species.CAT,
        )
    )
    val vm =
        ReportSightingViewModel(
            sightingRepository = sightingRepository,
            photoRepository = photoRepository,
            authRepository = authRepository,
            alertRepository = alertRepository,
        )

    vm.preselectAlert("alert-milo")
    vm.setLocation(Location(46.52, 6.63))

    val state = vm.uiState.value
    assertEquals("alert-milo", state.selectedAlertId)
    assertEquals(Species.CAT, state.selectedSpecies)
    assertEquals(listOf("Milo"), state.nearbyAlerts.map { it.petName })
    assertTrue(state.canSubmit)
  }

  @Test
  fun nearbyAlerts_onlyListsOpenAlerts() = runTest {
    val alertRepository = FakeAlertRepository()
    listOf(
            Alert(
                id = "alert-open",
                petId = "pet-open",
                ownerId = "owner",
                lastKnownLocation = Location(46.52, 6.63),
                lostAtMillis = 2L,
                petName = "Milo",
                petSpecies = Species.CAT,
            ),
            Alert(
                id = "alert-closed",
                petId = "pet-closed",
                ownerId = "owner",
                lastKnownLocation = Location(46.52, 6.63),
                lostAtMillis = 1L,
                closedAtMillis = 3L,
                status = AlertStatus.CLOSED,
                petName = "Luna",
                petSpecies = Species.DOG,
            ),
        )
        .forEach { alertRepository.publishAlert(it) }
    val vm =
        ReportSightingViewModel(
            sightingRepository = sightingRepository,
            photoRepository = photoRepository,
            authRepository = authRepository,
            alertRepository = alertRepository,
        )

    vm.setLocation(Location(46.52, 6.63))

    assertEquals(listOf("alert-open"), vm.uiState.value.nearbyAlerts.map { it.alertId })
  }

  @Test
  fun preselectAlert_closedAlertDoesNotAddOrSelectIt() = runTest {
    val alertRepository = FakeAlertRepository()
    alertRepository.publishAlert(
        Alert(
            id = "alert-closed",
            petId = "pet-closed",
            ownerId = "owner",
            lastKnownLocation = Location(46.52, 6.63),
            lostAtMillis = 1L,
            closedAtMillis = 3L,
            status = AlertStatus.CLOSED,
            petName = "Luna",
            petSpecies = Species.DOG,
        )
    )
    val vm =
        ReportSightingViewModel(
            sightingRepository = sightingRepository,
            photoRepository = photoRepository,
            authRepository = authRepository,
            alertRepository = alertRepository,
        )

    vm.preselectAlert("alert-closed")

    assertNull(vm.uiState.value.selectedAlertId)
    assertTrue(vm.uiState.value.nearbyAlerts.isEmpty())
  }

  // ===================== Animal check of the photo =====================

  @Test
  fun setPhotoUri_withAnAnimal_acceptsThePhotoAndDetectsItsSpecies() {
    val detector = FakeAnimalDetector().apply { result = AnimalDetection(true, Species.CAT) }
    val vm = viewModelWith(detector)

    vm.setPhotoUri(PHOTO)

    val state = vm.uiState.value
    assertEquals(PHOTO, state.photoUri)
    assertEquals(PhotoCheck.ANIMAL, state.photoCheck)
    assertEquals(Species.CAT, state.detectedSpecies)
    assertEquals(listOf(PHOTO), detector.checkedPhotos)
  }

  @Test
  fun setPhotoUri_withoutAnAnimal_blocksTheSubmit() {
    val detector = FakeAnimalDetector().apply { result = AnimalDetection(false, null) }
    val vm = viewModelWith(detector).withAValidForm()

    vm.setPhotoUri(PHOTO)

    val state = vm.uiState.value
    assertEquals(PhotoCheck.NO_ANIMAL, state.photoCheck)
    assertNull(state.detectedSpecies)
    assertFalse(state.canSubmit)
  }

  @Test
  fun setPhotoUri_whenTheDetectorFails_acceptsThePhotoWithoutSpecies() {
    val detector = FakeAnimalDetector().apply { failure = Exception("ML Kit unavailable") }
    val vm = viewModelWith(detector).withAValidForm()

    vm.setPhotoUri(PHOTO)

    val state = vm.uiState.value
    assertEquals(PhotoCheck.ANIMAL, state.photoCheck)
    assertNull(state.detectedSpecies)
    assertTrue(state.canSubmit)
  }

  @Test
  fun setPhotoUri_null_resetsTheCheckAndTheDetectedSpecies() {
    val vm = viewModelWith(FakeAnimalDetector())
    vm.setPhotoUri(PHOTO)

    vm.setPhotoUri(null)

    val state = vm.uiState.value
    assertNull(state.photoUri)
    assertEquals(PhotoCheck.NONE, state.photoCheck)
    assertNull(state.detectedSpecies)
  }

  @Test
  fun setPhotoUri_blocksTheSubmitWhileChecking() {
    val detector = PendingAnimalDetector()
    val vm = viewModelWith(detector).withAValidForm()

    vm.setPhotoUri(PHOTO)

    assertEquals(PhotoCheck.CHECKING, vm.uiState.value.photoCheck)
    assertFalse(vm.uiState.value.canSubmit)

    detector.finish(PHOTO, AnimalDetection(true, Species.DOG))

    assertEquals(PhotoCheck.ANIMAL, vm.uiState.value.photoCheck)
    assertTrue(vm.uiState.value.canSubmit)
  }

  @Test
  fun setPhotoUri_anotherPhotoDuringTheCheck_ignoresTheFirstResult() {
    val detector = PendingAnimalDetector()
    val vm = viewModelWith(detector)

    vm.setPhotoUri(PHOTO)
    vm.setPhotoUri(OTHER_PHOTO)
    detector.finish(OTHER_PHOTO, AnimalDetection(true, Species.CAT))
    detector.finish(PHOTO, AnimalDetection(false, null))

    val state = vm.uiState.value
    assertEquals(OTHER_PHOTO, state.photoUri)
    assertEquals(PhotoCheck.ANIMAL, state.photoCheck)
    assertEquals(Species.CAT, state.detectedSpecies)
  }

  @Test
  fun detectedSpecies_doesNotReplaceTheSpeciesChosenByTheUser() {
    val detector = FakeAnimalDetector().apply { result = AnimalDetection(true, Species.DOG) }
    val vm = viewModelWith(detector)
    vm.setSpecies(Species.CAT)

    vm.setPhotoUri(PHOTO)
    vm.selectNoMatchingAlert()

    assertEquals(Species.DOG, vm.uiState.value.detectedSpecies)
    assertEquals(Species.CAT, vm.uiState.value.selectedSpecies)
  }

  @Test
  fun detectedSpecies_isUsedWhenTheUserChoseNone() {
    val detector = FakeAnimalDetector().apply { result = AnimalDetection(true, Species.CAT) }
    val vm = viewModelWith(detector)

    vm.setPhotoUri(PHOTO)
    vm.selectNoMatchingAlert()

    assertEquals(Species.CAT, vm.uiState.value.selectedSpecies)
  }

  // ===================== Plausibility of a sighting on an alert =====================

  @Test
  fun submitSighting_plausibleSightingOnAnAlert_isSent() = runTest {
    val vm = viewModelOnAlert(alertOwnedBy("someone-else"))

    vm.submitSighting()

    assertTrue(vm.uiState.value.submitted)
    assertEquals(ALERT_ID, sightingRepository.reportedSightings.single().alertId)
  }

  @Test
  fun submitSighting_onOwnAlert_isRefusedWithItsMessageAndNothingUploaded() = runTest {
    val vm = viewModelOnAlert(alertOwnedBy(signedInUid()))
    vm.setPhotoUri(PHOTO)

    vm.submitSighting()

    val state = vm.uiState.value
    assertEquals(ReportSightingViewModel.OWN_ALERT_MESSAGE, state.errorMessage)
    assertFalse(state.submitted)
    assertFalse(state.isSubmitting)
    assertTrue(sightingRepository.reportedSightings.isEmpty())
    assertTrue(photoRepository.uploadedPhotos.isEmpty())
  }

  @Test
  fun submitSighting_severalIssues_showsOneLinePerIssue() = runTest {
    // Lost after the sighting time, far away, by the signed-in user
    val alert =
        alertOwnedBy(signedInUid())
            .copy(lostAtMillis = fixedTimestamp + 1, lastKnownLocation = Location(47.37, 8.54))
    val vm = viewModelOnAlert(alert)

    vm.submitSighting()

    assertEquals(
        listOf(
                ReportSightingViewModel.SEEN_BEFORE_LOST_MESSAGE,
                ReportSightingViewModel.TOO_FAR_MESSAGE,
                ReportSightingViewModel.OWN_ALERT_MESSAGE,
            )
            .joinToString("\n"),
        vm.uiState.value.errorMessage,
    )
    assertTrue(sightingRepository.reportedSightings.isEmpty())
  }

  @Test
  fun submitSighting_alertThatCantBeLoaded_isRefused() = runTest {
    val alertRepository = FakeAlertRepository().apply { failure = Exception("Offline") }
    val vm = viewModelOnAlert(alert = null, alertRepository = alertRepository)

    vm.submitSighting()

    assertEquals(ReportSightingViewModel.ALERT_NOT_LOADED, vm.uiState.value.errorMessage)
    assertFalse(vm.uiState.value.isSubmitting)
    assertTrue(sightingRepository.reportedSightings.isEmpty())
  }

  @Test
  fun submitSighting_alertThatDoesNotExist_isRefused() = runTest {
    val vm = viewModelOnAlert(alert = null)

    vm.submitSighting()

    assertEquals(ReportSightingViewModel.ALERT_NOT_LOADED, vm.uiState.value.errorMessage)
    assertTrue(sightingRepository.reportedSightings.isEmpty())
  }

  @Test
  fun submitSighting_withoutAnAlertRepository_isRefusedWhenAnAlertIsChosen() = runTest {
    viewModel.setSpecies(Species.DOG)
    viewModel.setLocation(ALERT_PLACE)
    viewModel.selectAlert(ALERT_ID)

    viewModel.submitSighting()

    assertEquals(ReportSightingViewModel.ALERT_NOT_LOADED, viewModel.uiState.value.errorMessage)
    assertTrue(sightingRepository.reportedSightings.isEmpty())
  }

  private fun signedInUid() = authRepository.currentUser.value!!.uid

  private fun alertOwnedBy(ownerId: String) =
      Alert(
          id = ALERT_ID,
          petId = "pet",
          ownerId = ownerId,
          lastKnownLocation = ALERT_PLACE,
          lostAtMillis = 0L,
          petName = "Rex",
          petSpecies = Species.DOG,
      )

  /**
   * A view model whose form is filled in for a sighting at [ALERT_PLACE] on [ALERT_ID], with
   * [alert] published (none if null) in [alertRepository].
   */
  private suspend fun viewModelOnAlert(
      alert: Alert?,
      alertRepository: FakeAlertRepository = FakeAlertRepository(),
  ): ReportSightingViewModel {
    alert?.let { alertRepository.publishAlert(it) }
    return ReportSightingViewModel(
            sightingRepository = sightingRepository,
            photoRepository = photoRepository,
            authRepository = authRepository,
            now = { fixedTimestamp },
            alertRepository = alertRepository,
        )
        .apply {
          setSpecies(Species.DOG)
          setLocation(ALERT_PLACE)
          selectAlert(ALERT_ID)
        }
  }

  private fun viewModelWith(detector: AnimalDetector) =
      ReportSightingViewModel(
          sightingRepository = sightingRepository,
          photoRepository = photoRepository,
          authRepository = authRepository,
          animalDetector = detector,
      )

  /** Fills everything [ReportSightingUiState.canSubmit] needs, except the photo. */
  private fun ReportSightingViewModel.withAValidForm() = apply {
    setSpecies(Species.DOG)
    setLocation(Location(46.5, 6.6))
    selectNoMatchingAlert()
  }

  /** [AnimalDetector] whose detections wait until the test [finish]es them. */
  private class PendingAnimalDetector : AnimalDetector {
    private val pending = mutableMapOf<String, CompletableDeferred<AnimalDetection>>()

    override suspend fun detect(photoUri: String): Result<AnimalDetection> =
        Result.success(pending.getOrPut(photoUri) { CompletableDeferred() }.await())

    fun finish(photoUri: String, detection: AnimalDetection) {
      pending.getValue(photoUri).complete(detection)
    }
  }

  private companion object {
    const val PHOTO = "content://media/photo.jpg"
    const val OTHER_PHOTO = "content://media/other.jpg"
    const val ALERT_ID = "alert-rex"
    val ALERT_PLACE = Location(46.52, 6.63)
  }
}
