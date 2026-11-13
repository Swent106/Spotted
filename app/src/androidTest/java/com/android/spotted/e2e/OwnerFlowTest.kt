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
import com.android.spotted.model.Pet.Species
import com.android.spotted.ui.addpets.AddPetsScreenTestTags
import com.android.spotted.ui.authentification.SignInScreenTestTags
import com.android.spotted.ui.components.MapPickerTestTags
import com.android.spotted.ui.home.HomeScreenTestTags
import com.android.spotted.ui.myalert.MyAlertScreenTestTags
import com.android.spotted.ui.personalinfo.PersonalInfoScreenTestTags
import com.android.spotted.ui.profile.HelperProfileTestTags
import com.android.spotted.ui.report.ReportMissingScreenTestTags
import com.android.spotted.ui.sighting.ReportSightingScreenTestTags
import com.android.spotted.utils.EndToEndTest
import com.android.spotted.utils.TestAccount
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end test of the owner's side, with two users on the Firebase emulators: the owner reports
 * their pet missing, a passerby sees the alert on Home and reports a sighting of it, the owner
 * confirms the sighting in My alert and marks the pet as home, and the passerby no longer sees the
 * alert.
 *
 * Users take turns on the device: one signs out, the next signs in with another Google account. The
 * sighting has no photo, since the system photo picker can't be driven by a test.
 */
@RunWith(AndroidJUnit4::class)
class OwnerFlowTest : EndToEndTest() {

  /** Makes this run's pet name unique, on top of the emulators being cleared. */
  private val petName = "Rex ${System.currentTimeMillis().toString(36).takeLast(5)}"

  @Test
  fun ownerReports_passerbySees_ownerConfirms_petHome_alertGone() {
    signInAsNewUser(OWNER, firstName = "Owner")
    addPet()
    val ownerId = currentUid()
    val alertId = reportPetMissing(ownerId)
    signOut()

    signInAsNewUser(PASSERBY, firstName = "Passerby")
    node(AddPetsScreenTestTags.SKIP_BUTTON).scrollToAndClick()
    waitFor(HomeScreenTestTags.SCREEN, "Home should open when the passerby skips adding pets")
    val sightingId = reportSightingOf(alertId, reporterId = currentUid())
    signOut()

    signInAgain(OWNER)
    confirmSightingAndCloseAlert(alertId, sightingId, ownerId)
    signOut()

    signInAgain(PASSERBY)
    waitFor(HomeScreenTestTags.ALERTS_EMPTY, "The passerby should no longer see the closed alert")
    assertFalse(exists(HomeScreenTestTags.alertCard(alertId)))
  }

  // ---------------------------------------- Steps ----------------------------------------

  /** Signs in with [account], which has no profile yet, and creates it. Ends on Add pets. */
  private fun signInAsNewUser(account: TestAccount, firstName: String) {
    signIn(account)
    waitFor(PersonalInfoScreenTestTags.SCREEN, "Personal info should open for a new account")
    node(PersonalInfoScreenTestTags.FIRST_NAME_INPUT).performScrollTo().performTextInput(firstName)
    node(PersonalInfoScreenTestTags.LAST_NAME_INPUT).performScrollTo().performTextInput("Tester")
    node(PersonalInfoScreenTestTags.HOME_AREA_INPUT).performScrollTo().performTextInput("Lausanne")
    // Home searches alerts around the home pin. Without one it geocodes the home area, which
    // fails on emulators. A tap in the middle of the map pins the home where the alert will be
    node(MapPickerTestTags.MAP).performScrollTo().performTouchInput { click(center) }
    waitFor(MapPickerTestTags.RADIUS_LABEL, "Tapping the map should place the home pin")
    node(PersonalInfoScreenTestTags.CREATE_PROFILE_BUTTON).assertIsEnabled().scrollToAndClick()
    waitFor(AddPetsScreenTestTags.SCREEN, "Add pets should open once the profile is saved")
  }

  /** Signs in with [account], which already has a profile: the app goes straight to Home. */
  private fun signInAgain(account: TestAccount) {
    signIn(account)
    waitFor(HomeScreenTestTags.SCREEN, "Home should open for an account with a profile")
  }

  private fun signIn(account: TestAccount) {
    credentials.account = account
    waitFor(SignInScreenTestTags.SCREEN, "The Sign in screen should show")
    node(SignInScreenTestTags.GOOGLE_BUTTON).scrollToAndClick()
    waitUntil("${account.email} should be signed in") {
      FirebaseAuth.getInstance().currentUser?.email == account.email
    }
  }

  private fun addPet() {
    node(AddPetsScreenTestTags.NAME_INPUT).performScrollTo().performTextInput(petName)
    node(AddPetsScreenTestTags.speciesChip(Species.DOG)).performScrollTo().performClick()
    node(AddPetsScreenTestTags.SAVE_AND_CONTINUE_BUTTON).assertIsEnabled().scrollToAndClick()
    waitFor(HomeScreenTestTags.SCREEN, "Home should open once the pet is saved")
  }

  /** Publishes an alert for the owner's pet and returns its id. */
  private fun reportPetMissing(ownerId: String): String {
    node(HomeScreenTestTags.BODY)
        .performScrollToNode(hasTestTag(HomeScreenTestTags.REPORT_MISSING_BUTTON))
    node(HomeScreenTestTags.REPORT_MISSING_BUTTON).scrollToAndClick()
    waitFor(ReportMissingScreenTestTags.SCREEN, "Report missing should open")
    waitUntil("\"$petName\" should be offered as the missing pet") {
      composeTestRule.onAllNodes(hasText(petName)).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNode(hasText(petName)).performScrollTo().performClick()
    // Where the pet was last seen: a tap in the middle of the map places the pin
    node(MapPickerTestTags.MAP).performScrollTo().performTouchInput { click(center) }
    waitFor(MapPickerTestTags.RADIUS_LABEL, "Tapping the map should place the last seen pin")
    node(ReportMissingScreenTestTags.PUBLISH_BUTTON)
        .performScrollTo()
        .assertIsEnabled()
        .performClick()
    waitFor(HomeScreenTestTags.SCREEN, "Home should show again once the alert is published")

    val alert = documents("alerts", "ownerId", ownerId).single()
    val alertId = alert[DOCUMENT_ID] as String
    waitFor(HomeScreenTestTags.missingBanner(alertId), "The owner should see their pet as missing")
    return alertId
  }

  /** The passerby finds the alert on Home and reports seeing the pet. Returns the sighting's id. */
  private fun reportSightingOf(alertId: String, reporterId: String): String {
    val card = HomeScreenTestTags.alertCard(alertId)
    waitUntil("The passerby should see the alert in \"Lost near you\"") {
      runCatching {
            node(HomeScreenTestTags.BODY).performScrollToNode(hasTestTag(card))
            true
          }
          .getOrDefault(false)
    }
    node(HomeScreenTestTags.seenButton(alertId)).scrollToAndClick()
    waitFor(ReportSightingScreenTestTags.SCREEN, "\"I have seen this pet\" should open the form")
    // The alert comes preselected: its pet is listed in the form
    waitFor(
        "${ReportSightingScreenTestTags.PET_OPTION_PREFIX}$alertId",
        "The form should list the pet of the alert",
    )
    node(ReportSightingScreenTestTags.LOCATION_CARD).performScrollTo().performClick()
    waitUntil("The GPS position should fill the sighting's location") {
      composeTestRule.onAllNodes(hasText("Taken from your GPS")).fetchSemanticsNodes().isNotEmpty()
    }
    node(ReportSightingScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .assertIsEnabled()
        .performClick()
    waitFor(HomeScreenTestTags.SCREEN, "Home should show again once the sighting is sent")

    val sighting = documents("sightings", "reporterId", reporterId).single()
    assertEquals("The sighting should be for the alert", alertId, sighting["alertId"])
    return sighting[DOCUMENT_ID] as String
  }

  /** The owner opens My alert from Home, confirms the sighting and says the pet is home. */
  private fun confirmSightingAndCloseAlert(alertId: String, sightingId: String, ownerId: String) {
    val banner = HomeScreenTestTags.missingBanner(alertId)
    waitUntil("The owner should see their missing pet on Home") {
      runCatching {
            node(HomeScreenTestTags.BODY).performScrollToNode(hasTestTag(banner))
            true
          }
          .getOrDefault(false)
    }
    node(banner).scrollToAndClick()
    waitFor(MyAlertScreenTestTags.SCREEN, "The missing pet banner should open My alert")

    val status = MyAlertScreenTestTags.status(sightingId)
    waitFor(MyAlertScreenTestTags.sighting(sightingId), "My alert should list the sighting")
    assertStatus(status, "To review")
    node(MyAlertScreenTestTags.confirm(sightingId)).scrollToAndClick()
    waitUntil("The sighting should show as confirmed") { hasStatus(status, "Confirmed") }

    node(MyAlertScreenTestTags.PET_HOME_BUTTON).scrollToAndClick()
    waitFor(MyAlertScreenTestTags.PET_HOME_DIALOG, "\"My pet is home\" should ask to confirm")
    node(MyAlertScreenTestTags.PET_HOME_CONFIRM).performClick()
    waitFor(HomeScreenTestTags.SCREEN, "Home should show again once the alert is closed")
    waitUntil("The missing pet banner should disappear") { !exists(banner) }

    val alert = documents("alerts", "ownerId", ownerId).single()
    assertEquals("The alert should be closed", "CLOSED", alert["status"])
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

  // ---------------------------------------- Helpers ----------------------------------------

  private fun currentUid(): String =
      checkNotNull(FirebaseAuth.getInstance().currentUser?.uid) { "A user should be signed in" }

  private fun hasStatus(tag: String, label: String): Boolean =
      composeTestRule
          .onAllNodes(hasTestTag(tag) and hasText(label))
          .fetchSemanticsNodes()
          .isNotEmpty()

  private fun assertStatus(tag: String, label: String) =
      assertEquals("The sighting's status should be \"$label\"", true, hasStatus(tag, label))

  private companion object {
    val OWNER = TestAccount(name = "Owner Tester", email = "owner@spotted.test", sub = "e2e-owner")
    val PASSERBY =
        TestAccount(name = "Passerby Tester", email = "passerby@spotted.test", sub = "e2e-passerby")
  }
}
