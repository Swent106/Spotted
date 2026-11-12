// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.personalinfo

import com.android.spotted.model.Location
import com.android.spotted.model.auth.User
import com.android.spotted.model.fakes.FakeAddressRepository
import com.android.spotted.model.fakes.FakeLocationRepository
import com.android.spotted.model.location.LocationException
import com.android.spotted.model.photo.FakePhotoRepository
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PersonalInfoViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private lateinit var userRepository: FakeUserRepository
  private lateinit var viewModel: PersonalInfoViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    userRepository = FakeUserRepository()
    viewModel =
        PersonalInfoViewModel(
            user = User(uid = "user-1", email = "jamie@example.com"),
            userRepository = userRepository,
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState_containsIdentityAndCannotSave() {
    val state = viewModel.uiState.value

    assertEquals("jamie@example.com", state.email)
    assertFalse(state.canSave)
  }

  @Test
  fun signedOutUser_startsInSignedOutStateAndCannotSave() =
      runTest(testDispatcher) {
        val signedOutViewModel =
            PersonalInfoViewModel(
                user = null,
                userRepository = userRepository,
            )
        signedOutViewModel.onFirstNameChanged("Jamie")
        signedOutViewModel.onHomeAreaChanged("Lausanne")

        assertTrue(signedOutViewModel.uiState.value.isSignedOut)
        assertFalse(signedOutViewModel.uiState.value.canSave)

        signedOutViewModel.saveProfile()
        advanceUntilIdle()

        assertEquals(0, userRepository.saveCalls)
      }

  @Test
  fun fieldChanges_areImmediatelyReflectedInState() {
    viewModel.onUsernameChanged("jamie")
    viewModel.onFirstNameChanged("Jamie")
    viewModel.onHomeAreaChanged("Lausanne")

    val state = viewModel.uiState.value
    assertEquals("Jamie", state.name)
    assertEquals("Lausanne", state.homeArea)
    assertTrue(state.canSave)
  }

  @Test
  fun firstAndLastNameChanges_combineIntoName() {
    viewModel.onFirstNameChanged(" Rayen ")
    viewModel.onLastNameChanged("Ben Ali")

    val state = viewModel.uiState.value
    assertEquals(" Rayen ", state.firstName)
    assertEquals("Ben Ali", state.lastName)
    assertEquals("Rayen Ben Ali", state.name)
  }

  @Test
  fun firstNameOnly_isEnoughForName() {
    viewModel.onUsernameChanged("jamie")
    viewModel.onFirstNameChanged("Rayen")
    viewModel.onHomeAreaChanged("Plainpalais, Geneva")

    assertEquals("Rayen", viewModel.uiState.value.name)
    assertTrue(viewModel.uiState.value.canSave)
  }

  @Test
  fun usernameChange_isReflectedInState_andCutToTheMaximumLength() {
    viewModel.onUsernameChanged("@rayen.gva")
    assertEquals("@rayen.gva", viewModel.uiState.value.username)

    viewModel.onUsernameChanged("x".repeat(40))

    assertEquals("x".repeat(UserProfile.MAX_USERNAME_LENGTH), viewModel.uiState.value.username)
  }

  @Test
  fun saveProfile_withoutAUsername_doesNotSave_andASavedOneIsLoadedBackForEditing() =
      runTest(testDispatcher) {
        viewModel.onFirstNameChanged("Jamie")
        viewModel.onHomeAreaChanged("Lausanne")
        listOf("", "  ", "@", " @ ").forEach { blank ->
          viewModel.onUsernameChanged(blank)
          assertFalse("'$blank' is not a username", viewModel.uiState.value.canSave)
        }
        viewModel.saveProfile()
        advanceUntilIdle()
        assertEquals(0, userRepository.saveCalls)

        viewModel.onUsernameChanged(" @jamie ")
        viewModel.saveProfile()
        advanceUntilIdle()
        assertEquals("jamie", userRepository.getUser("user-1").getOrThrow().username)

        val reopened = viewModelFor(userRepository)
        advanceUntilIdle()
        assertEquals("jamie", reopened.uiState.value.username)
      }

  @Test
  fun alertRadius_defaultsToThreeKm_andAcceptsOnlyListedOptions() {
    assertEquals(3, viewModel.uiState.value.alertRadiusKm)

    viewModel.onAlertRadiusChanged(10)
    assertEquals(10, viewModel.uiState.value.alertRadiusKm)

    viewModel.onAlertRadiusChanged(7)
    assertEquals(10, viewModel.uiState.value.alertRadiusKm)
  }

  @Test
  fun saveProfile_withMissingName_doesNotSave() =
      runTest(testDispatcher) {
        viewModel.onHomeAreaChanged("Lausanne")

        viewModel.saveProfile()
        advanceUntilIdle()

        assertTrue(userRepository.getUser("user-1").isFailure)
        assertFalse(viewModel.uiState.value.isSaved)
      }

  @Test
  fun saveProfile_withMissingHomeArea_doesNotSave() =
      runTest(testDispatcher) {
        viewModel.onUsernameChanged("jamie")
        viewModel.onFirstNameChanged("Jamie")

        viewModel.saveProfile()
        advanceUntilIdle()

        assertTrue(userRepository.getUser("user-1").isFailure)
        assertFalse(viewModel.uiState.value.isSaved)
      }

  @Test
  fun saveProfile_withWhitespaceRequiredFields_doesNotSave() =
      runTest(testDispatcher) {
        viewModel.onFirstNameChanged("   ")
        viewModel.onHomeAreaChanged("\t")

        assertFalse(viewModel.uiState.value.canSave)
        viewModel.saveProfile()
        advanceUntilIdle()

        assertTrue(userRepository.getUser("user-1").isFailure)
      }

  @Test
  fun saveProfile_withValidFieldsPersistsTrimmedProfile() =
      runTest(testDispatcher) {
        viewModel.onUsernameChanged(" @jamie ")
        viewModel.onFirstNameChanged(" Jamie ")
        viewModel.onHomeAreaChanged(" Lausanne ")

        viewModel.saveProfile()
        advanceUntilIdle()

        val savedUser = userRepository.getUser("user-1").getOrThrow()
        assertEquals("jamie", savedUser.username)
        assertEquals("Jamie", savedUser.name)
        assertEquals("Lausanne", savedUser.homeArea)
        assertEquals("jamie@example.com", savedUser.email)
        assertTrue(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
      }

  @Test
  fun saveProfile_doubleTapSavesOnlyOnce() =
      runTest(testDispatcher) {
        viewModel.onUsernameChanged("jamie")
        viewModel.onFirstNameChanged("Jamie")
        viewModel.onHomeAreaChanged("Lausanne")

        viewModel.saveProfile()
        viewModel.saveProfile()
        advanceUntilIdle()

        assertEquals(1, userRepository.saveCalls)
        assertTrue(viewModel.uiState.value.isSaved)
      }

  @Test
  fun saveProfile_whenRepositoryFails_showsAFixedMessage_whateverTheException() =
      runTest(testDispatcher) {
        // Lets the profile load finish first, so only the save fails
        advanceUntilIdle()
        viewModel.onUsernameChanged("jamie")
        viewModel.onFirstNameChanged("Jamie")
        viewModel.onHomeAreaChanged("Lausanne")

        listOf(IllegalStateException("PERMISSION_DENIED: internal detail"), IllegalStateException())
            .forEach { failure ->
              userRepository.failure = failure

              viewModel.saveProfile()
              advanceUntilIdle()

              val state = viewModel.uiState.value
              assertEquals(PersonalInfoViewModel.SAVE_FAILED, state.errorMessage)
              assertFalse(state.isSaved)
              assertFalse(state.isSaving)
            }
      }

  @Test
  fun existingProfile_whenItCannotBeRead_isNotOverwritten_andTheButtonReadsItAgain() =
      runTest(testDispatcher) {
        val saved = savedProfile()
        userRepository.saveUser(saved)
        userRepository.failure = IllegalStateException("offline")
        val editViewModel = viewModelFor(userRepository)
        advanceUntilIdle()

        val failed = editViewModel.uiState.value
        assertEquals(PersonalInfoViewModel.PROFILE_LOAD_FAILED, failed.errorMessage)
        assertFalse(failed.isEditing)
        assertTrue(failed.canSave)

        // Back online, the user fills the empty form and taps the button
        userRepository.failure = null
        editViewModel.onUsernameChanged("jamie")
        editViewModel.onFirstNameChanged("Someone")
        editViewModel.onHomeAreaChanged("Elsewhere")
        editViewModel.saveProfile()
        advanceUntilIdle()

        assertEquals(saved, userRepository.getUser("user-1").getOrThrow())
        val reloaded = editViewModel.uiState.value
        assertTrue(reloaded.isEditing)
        assertEquals("Ouchy, Lausanne", reloaded.homeArea)
        assertNull(reloaded.errorMessage)
      }

  @Test
  fun saveProfile_beforeTheProfileHasLoaded_doesNotOverwriteIt() =
      runTest(testDispatcher) {
        val saved = savedProfile()
        userRepository.saveUser(saved)
        val editViewModel = viewModelFor(userRepository)

        // Tapped before the load has run: the form is still empty apart from what was typed
        editViewModel.onUsernameChanged("jamie")
        editViewModel.onFirstNameChanged("Someone")
        editViewModel.onHomeAreaChanged("Elsewhere")
        editViewModel.saveProfile()
        advanceUntilIdle()

        assertEquals(saved, userRepository.getUser("user-1").getOrThrow())
      }

  @Test
  fun homeLocation_isEmptyAtFirst_andFollowsThePlacedPin() {
    assertNull(viewModel.uiState.value.homeLocation)

    viewModel.onHomeLocationChanged(Location(46.52, 6.63))

    assertEquals(Location(46.52, 6.63), viewModel.uiState.value.homeLocation)
  }

  @Test
  fun onSavedHandled_resetsSavedFlag() =
      runTest(testDispatcher) {
        viewModel.onUsernameChanged("jamie")
        viewModel.onFirstNameChanged("Jamie")
        viewModel.onHomeAreaChanged("Lausanne")
        viewModel.saveProfile()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaved)

        viewModel.onSavedHandled()

        assertFalse(viewModel.uiState.value.isSaved)
      }

  @Test
  fun editingAfterSuccessfulSave_resetsSavedFlag() =
      runTest(testDispatcher) {
        viewModel.onUsernameChanged("jamie")
        viewModel.onFirstNameChanged("Jamie")
        viewModel.onHomeAreaChanged("Lausanne")
        viewModel.saveProfile()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaved)

        viewModel.onHomeAreaChanged("Geneva")

        assertEquals("Geneva", viewModel.uiState.value.homeArea)
        assertFalse(viewModel.uiState.value.isSaved)
      }

  @Test
  fun canSave_staysTrueWithErrorMessage_soUserCanRetry() {
    val failed =
        PersonalInfoUiState(
            username = "jamie",
            name = "Jamie",
            homeArea = "Lausanne",
            errorMessage = "Save failed",
        )

    assertTrue(failed.canSave)
  }

  private fun savedProfile() =
      UserProfile(
          uid = "user-1",
          name = "Jamie Lee Curtis",
          phone = "+41 79 123 45 67",
          email = "jamie@example.com",
          homeArea = "Ouchy, Lausanne",
          homeLocation = Location(46.507, 6.627),
          alertRadiusKm = 10,
          username = "jamie",
      )

  private fun viewModelFor(userRepository: FakeUserRepository) =
      PersonalInfoViewModel(
          user = User(uid = "user-1", email = "jamie@example.com"),
          userRepository = userRepository,
      )

  private fun viewModelWithLocation(
      locationRepository: FakeLocationRepository,
      addressRepository: FakeAddressRepository? = null,
  ) =
      PersonalInfoViewModel(
          user = User(uid = "user-1", email = "jamie@example.com"),
          userRepository = userRepository,
          locationRepository = locationRepository,
          addressRepository = addressRepository,
      )

  @Test
  fun useCurrentLocation_placesTheHomePinAtTheDevicePosition() =
      runTest(testDispatcher) {
        val gpsViewModel = viewModelWithLocation(FakeLocationRepository(Location(46.52, 6.63)))

        gpsViewModel.useCurrentLocation()
        advanceUntilIdle()

        assertEquals(Location(46.52, 6.63), gpsViewModel.uiState.value.homeLocation)
        assertNull(gpsViewModel.uiState.value.errorMessage)
      }

  @Test
  fun useCurrentLocation_writesTheAreaNameInTheHomeAreaField() =
      runTest(testDispatcher) {
        val gpsViewModel =
            viewModelWithLocation(
                FakeLocationRepository(),
                FakeAddressRepository("Ouchy, Lausanne"),
            )

        gpsViewModel.useCurrentLocation()
        advanceUntilIdle()

        assertEquals("Ouchy, Lausanne", gpsViewModel.uiState.value.homeArea)
      }

  @Test
  fun useCurrentLocation_whenNoPlaceNameIsFound_placesThePinAndKeepsTheTypedArea() =
      runTest(testDispatcher) {
        val addressRepository =
            FakeAddressRepository().apply { failure = NoSuchElementException("No place name") }
        val gpsViewModel = viewModelWithLocation(FakeLocationRepository(), addressRepository)
        gpsViewModel.onHomeAreaChanged("Flon")

        gpsViewModel.useCurrentLocation()
        advanceUntilIdle()

        assertEquals("Flon", gpsViewModel.uiState.value.homeArea)
        assertEquals(
            FakeLocationRepository.DEFAULT_LOCATION,
            gpsViewModel.uiState.value.homeLocation,
        )
      }

  @Test
  fun useCurrentLocation_whenNoPositionIsFound_keepsThePinAndShowsAnError() =
      runTest(testDispatcher) {
        val locationRepository =
            FakeLocationRepository().apply { failure = LocationException.Unavailable() }
        val gpsViewModel = viewModelWithLocation(locationRepository)

        gpsViewModel.useCurrentLocation()
        advanceUntilIdle()

        assertNull(gpsViewModel.uiState.value.homeLocation)
        assertEquals(
            PersonalInfoViewModel.LOCATION_UNAVAILABLE,
            gpsViewModel.uiState.value.errorMessage,
        )
      }

  @Test
  fun useCurrentLocation_withoutLocationRepository_doesNothing() =
      runTest(testDispatcher) {
        val before = viewModel.uiState.value

        viewModel.useCurrentLocation()
        advanceUntilIdle()

        assertEquals(before, viewModel.uiState.value)
      }

  @Test
  fun onLocationPermissionDenied_showsAnError() {
    viewModel.onLocationPermissionDenied()

    assertEquals(
        PersonalInfoViewModel.LOCATION_PERMISSION_DENIED,
        viewModel.uiState.value.errorMessage,
    )
  }

  @Test
  fun existingProfile_fillsTheFormForEditing() =
      runTest(testDispatcher) {
        userRepository.saveUser(
            UserProfile(
                uid = "user-1",
                name = "Jamie Lee Curtis",
                email = "jamie@example.com",
                homeArea = "Ouchy, Lausanne",
                homeLocation = Location(46.507, 6.627),
                alertRadiusKm = 10,
                username = "jamie",
            )
        )
        val editViewModel =
            PersonalInfoViewModel(
                user = User(uid = "user-1", email = "jamie@example.com"),
                userRepository = userRepository,
            )
        advanceUntilIdle()

        val state = editViewModel.uiState.value
        assertTrue(state.isEditing)
        assertEquals("jamie", state.username)
        assertEquals("Jamie", state.firstName)
        assertEquals("Lee Curtis", state.lastName)
        assertEquals("Ouchy, Lausanne", state.homeArea)
        assertEquals(Location(46.507, 6.627), state.homeLocation)
        assertEquals(10, state.alertRadiusKm)
        assertTrue(state.canSave)
      }

  @Test
  fun newUser_startsWithAnEmptyFormForCreating() =
      runTest(testDispatcher) {
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEditing)
        assertEquals("", viewModel.uiState.value.firstName)
      }

  @Test
  fun saveProfile_savesTheHomePinAndTheRadius() =
      runTest(testDispatcher) {
        viewModel.onUsernameChanged("jamie")
        viewModel.onFirstNameChanged("Jamie")
        viewModel.onHomeAreaChanged("Lausanne")
        viewModel.onHomeLocationChanged(Location(46.52, 6.63))
        viewModel.onAlertRadiusChanged(5)

        viewModel.saveProfile()
        advanceUntilIdle()

        val saved = userRepository.getUser("user-1").getOrThrow()
        assertEquals(Location(46.52, 6.63), saved.homeLocation)
        assertEquals(5, saved.alertRadiusKm)
      }

  @Test
  fun onPhotoSelected_showsThenRemovesThePhoto() {
    viewModel.onPhotoSelected("content://photos/1")
    assertEquals("content://photos/1", viewModel.uiState.value.photoUri)

    viewModel.onPhotoSelected(null)

    assertNull(viewModel.uiState.value.photoUri)
  }

  @Test
  fun saveProfile_uploadsANewPhotoBeforeSaving() =
      runTest(testDispatcher) {
        val photoRepository = FakePhotoRepository()
        val photoViewModel =
            PersonalInfoViewModel(
                user = User(uid = "user-1", email = "jamie@example.com"),
                userRepository = userRepository,
                photoRepository = photoRepository,
            )
        photoViewModel.onUsernameChanged("jamie")
        photoViewModel.onFirstNameChanged("Jamie")
        photoViewModel.onHomeAreaChanged("Lausanne")
        photoViewModel.onPhotoSelected("content://photos/me")

        photoViewModel.saveProfile()
        advanceUntilIdle()

        assertEquals(
            "content://photos/me",
            photoRepository.uploadedPhotos[photoRepository.profilePhotoUrl("user-1")],
        )
        assertTrue(photoViewModel.uiState.value.isSaved)
      }

  @Test
  fun saveProfile_whenThePhotoCannotBeUploaded_keepsTheFormAndSaysSo() =
      runTest(testDispatcher) {
        val photoRepository = FakePhotoRepository().apply { failure = Exception("offline") }
        val photoViewModel =
            PersonalInfoViewModel(
                user = User(uid = "user-1", email = "jamie@example.com"),
                userRepository = userRepository,
                photoRepository = photoRepository,
            )
        photoViewModel.onUsernameChanged("jamie")
        photoViewModel.onFirstNameChanged("Jamie")
        photoViewModel.onHomeAreaChanged("Lausanne")
        photoViewModel.onPhotoSelected("content://photos/me")

        photoViewModel.saveProfile()
        advanceUntilIdle()

        val state = photoViewModel.uiState.value
        assertEquals(PersonalInfoViewModel.PHOTO_UPLOAD_FAILED, state.errorMessage)
        assertFalse(state.isSaved)
        assertTrue(userRepository.getUser("user-1").isFailure)
      }

  @Test
  fun saveProfile_deletesTheSavedPhotoOnlyOnceItIsRemoved() =
      runTest(testDispatcher) {
        val (photoRepository, editing) = editingWithASavedPhoto()
        val photoUrl = photoRepository.profilePhotoUrl("user-1")

        // Saving with the photo kept leaves it in place
        editing.onHomeAreaChanged("Pully")
        editing.saveProfile()
        advanceUntilIdle()
        assertTrue(photoRepository.uploadedPhotos.containsKey(photoUrl))

        editing.onPhotoSelected(null)
        editing.saveProfile()
        advanceUntilIdle()

        assertFalse(photoRepository.uploadedPhotos.containsKey(photoUrl))
        assertTrue(editing.uiState.value.isSaved)
      }

  @Test
  fun saveProfile_whenTheRemovedPhotoCannotBeDeleted_keepsTheFormAndSaysSo() =
      runTest(testDispatcher) {
        val (photoRepository, editing) = editingWithASavedPhoto()
        val savesBefore = userRepository.saveCalls
        photoRepository.failure = Exception("offline")

        editing.onPhotoSelected(null)
        editing.saveProfile()
        advanceUntilIdle()

        val state = editing.uiState.value
        assertEquals(PersonalInfoViewModel.PHOTO_DELETE_FAILED, state.errorMessage)
        assertFalse(state.isSaved)
        assertFalse(state.isSaving)
        assertEquals("the profile is not saved", savesBefore, userRepository.saveCalls)
      }

  /** A saved profile with a profile photo, loaded in a ViewModel for editing. */
  private suspend fun TestScope.editingWithASavedPhoto():
      Pair<FakePhotoRepository, PersonalInfoViewModel> {
    val photoRepository = FakePhotoRepository()
    photoRepository.uploadProfilePhoto("user-1", "content://photos/old").getOrThrow()
    userRepository.saveUser(savedProfile()).getOrThrow()
    val editing =
        PersonalInfoViewModel(
            user = User(uid = "user-1", email = "jamie@example.com"),
            userRepository = userRepository,
            photoRepository = photoRepository,
        )
    advanceUntilIdle()
    assertTrue("the profile is loaded for editing", editing.uiState.value.isEditing)
    return photoRepository to editing
  }
}
