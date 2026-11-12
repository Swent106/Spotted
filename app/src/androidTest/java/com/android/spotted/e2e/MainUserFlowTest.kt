package com.android.spotted.e2e

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.spotted.model.Pet.Species
import com.android.spotted.ui.addpets.AddPetsScreenTestTags
import com.android.spotted.ui.authentification.SignInScreenTestTags
import com.android.spotted.ui.components.MapPickerTestTags
import com.android.spotted.ui.home.HomeScreenTestTags
import com.android.spotted.ui.personalinfo.PersonalInfoScreenTestTags
import com.android.spotted.ui.profile.HelperProfileTestTags
import com.android.spotted.ui.report.ReportMissingScreenTestTags
import com.android.spotted.ui.sighting.ReportSightingScreenTestTags
import com.android.spotted.utils.EndToEndTest
import com.android.spotted.utils.TestAccount
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end test of the main user journey, against the Firebase Auth and Firestore emulators
 * (`firebase emulators:start --only auth,firestore`): sign in, create the profile, add pets, see
 * them on Home, report one missing, report a sighting, then sign out.
 *
 * Everything the user does goes through the UI. [EndToEndTest] replaces what a test can't drive:
 * the system Google account chooser and the GPS.
 *
 * The number of pets comes from the `e2ePetCount` instrumentation argument (3 by default), e.g.
 * `-Pandroid.testInstrumentationRunnerArguments.e2ePetCount=5`.
 */
@RunWith(AndroidJUnit4::class)
class MainUserFlowTest : EndToEndTest() {

  /** Makes this run's pet names unique, on top of the emulators being cleared. */
  private val runId = System.currentTimeMillis().toString(36).takeLast(5)

  private val petCount =
      InstrumentationRegistry.getArguments().getString(PET_COUNT_ARGUMENT)?.toInt()
          ?: DEFAULT_PET_COUNT

  private val pets = (1..petCount).map { E2ePet(name = "Pet$it $runId", species = speciesFor(it)) }

  @Before
  fun checkPetCount() {
    assertTrue("$PET_COUNT_ARGUMENT must be at least 1 to report a pet missing", petCount >= 1)
  }

  @Test
  fun signIn_profile_pets_reportMissing_sighting_signOut() {
    signIn()
    completeProfile()
    addPets()
    assertPetsOnHome()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    checkNotNull(uid) { "A user should be signed in after the onboarding" }
    reportFirstPetMissing(uid)
    reportSighting(uid)
    signOut()
  }

  // ---------------------------------------- Steps ----------------------------------------

  private fun signIn() {
    waitFor(SignInScreenTestTags.SCREEN, "the Sign in screen should show at start")
    node(SignInScreenTestTags.GOOGLE_BUTTON).scrollToAndClick()
    // A new account has no profile: the app asks for one
    waitFor(PersonalInfoScreenTestTags.SCREEN, "Personal info should open after signing in")
    assertEquals(
        "The signed-in user should be the test account",
        TestAccount.DEFAULT.email,
        FirebaseAuth.getInstance().currentUser?.email,
    )
  }

  private fun completeProfile() {
    node(PersonalInfoScreenTestTags.FIRST_NAME_INPUT).performScrollTo().performTextInput("E2E")
    node(PersonalInfoScreenTestTags.LAST_NAME_INPUT).performScrollTo().performTextInput("Tester")
    node(PersonalInfoScreenTestTags.HOME_AREA_INPUT).performScrollTo().performTextInput("Lausanne")
    node(PersonalInfoScreenTestTags.CREATE_PROFILE_BUTTON).assertIsEnabled().scrollToAndClick()
    waitFor(AddPetsScreenTestTags.SCREEN, "Add pets should open once the profile is saved")
  }

  private fun addPets() {
    pets.forEachIndexed { index, pet ->
      node(AddPetsScreenTestTags.NAME_INPUT).performScrollTo().performTextInput(pet.name)
      node(AddPetsScreenTestTags.speciesChip(pet.species)).performScrollTo().performClick()
      val isLast = index == pets.lastIndex
      if (isLast) {
        node(AddPetsScreenTestTags.SAVE_AND_CONTINUE_BUTTON).assertIsEnabled().scrollToAndClick()
      } else {
        node(AddPetsScreenTestTags.ADD_ANOTHER_BUTTON).scrollToAndClick()
        waitUntil("\"${pet.name}\" should be listed as added") {
          composeTestRule
              .onAllNodes(hasTestTag(AddPetsScreenTestTags.ADDED_PETS) and hasText(pet.name, true))
              .fetchSemanticsNodes()
              .isNotEmpty()
        }
      }
    }
    waitFor(HomeScreenTestTags.SCREEN, "Home should open after the last pet is saved")
  }

  private fun assertPetsOnHome() {
    pets.forEach { pet ->
      waitUntil("\"${pet.name}\" should be in \"Your pets\" on Home") {
        runCatching {
              node(HomeScreenTestTags.BODY).performScrollToNode(hasText(pet.name))
              true
            }
            .getOrDefault(false)
      }
    }
  }

  private fun reportFirstPetMissing(uid: String) {
    val missing = pets.first()
    node(HomeScreenTestTags.BODY)
        .performScrollToNode(hasTestTag(HomeScreenTestTags.REPORT_MISSING_BUTTON))
    node(HomeScreenTestTags.REPORT_MISSING_BUTTON).scrollToAndClick()
    waitFor(ReportMissingScreenTestTags.SCREEN, "Report missing should open")
    waitUntil("\"${missing.name}\" should be offered as the missing pet") {
      composeTestRule.onAllNodes(hasText(missing.name)).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNode(hasText(missing.name)).performScrollTo().performClick()
    // Where the pet was last seen: a tap in the middle of the map places the pin
    node(MapPickerTestTags.MAP).performScrollTo().performTouchInput { click(center) }
    waitFor(MapPickerTestTags.RADIUS_LABEL, "Tapping the map should place the last seen pin")
    node(ReportMissingScreenTestTags.PUBLISH_BUTTON)
        .performScrollTo()
        .assertIsEnabled()
        .performClick()
    waitFor(HomeScreenTestTags.SCREEN, "Home should show again once the alert is published")

    val alerts = documents("alerts", "ownerId", uid)
    assertEquals("Exactly one alert should be published", 1, alerts.size)
    assertEquals("The alert should be for the chosen pet", missing.name, alerts.single()["petName"])
    assertEquals("The alert should be open", "OPEN", alerts.single()["status"])
  }

  private fun reportSighting(uid: String) {
    node(HomeScreenTestTags.TAB_CAMERA).performClick()
    waitFor(ReportSightingScreenTestTags.SCREEN, "The Camera tab should open Report sighting")
    node(ReportSightingScreenTestTags.LOCATION_CARD).performScrollTo().performClick()
    waitUntil("The GPS position should fill the sighting's location") {
      composeTestRule.onAllNodes(hasText("Taken from your GPS")).fetchSemanticsNodes().isNotEmpty()
    }
    node(ReportSightingScreenTestTags.NO_MATCH_OPTION).performScrollTo().performClick()
    node(ReportSightingScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .assertIsEnabled()
        .performClick()
    waitFor(HomeScreenTestTags.SCREEN, "Home should show again once the sighting is sent")

    val sightings = documents("sightings", "reporterId", uid)
    assertEquals("Exactly one sighting should be reported", 1, sightings.size)
    val location = sightings.single()["location"] as Map<*, *>
    assertEquals("The sighting should be at the GPS position", GPS.latitude, location["latitude"])
    assertEquals("The sighting should be at the GPS position", GPS.longitude, location["longitude"])
  }

  private fun signOut() {
    node(HomeScreenTestTags.TAB_PROFILE).performClick()
    waitFor(HelperProfileTestTags.SCREEN, "The Profile tab should open the Helper profile")
    node(HelperProfileTestTags.ACCOUNT_BUTTON).scrollToAndClick()
    waitFor(HelperProfileTestTags.SIGN_OUT, "The account button should offer to sign out")
    node(HelperProfileTestTags.SIGN_OUT).scrollToAndClick()
    waitFor(SignInScreenTestTags.SCREEN, "The Sign in screen should show after signing out")
    waitUntil("Firebase should have no signed-in user after signing out") {
      FirebaseAuth.getInstance().currentUser == null
    }
  }

  private data class E2ePet(val name: String, val species: Species)

  private companion object {
    const val PET_COUNT_ARGUMENT = "e2ePetCount"
    const val DEFAULT_PET_COUNT = 3

    fun speciesFor(index: Int) = Species.entries[(index - 1) % Species.entries.size]
  }
}
