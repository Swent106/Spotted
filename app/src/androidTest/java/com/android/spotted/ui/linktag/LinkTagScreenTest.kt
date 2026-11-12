// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.linktag

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Pet.FakePetRepository
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.model.tag.PetTag
import com.android.spotted.model.tag.PetTagRepository
import com.android.spotted.model.tag.TagWriteError
import com.android.spotted.model.tag.TagWriteException
import com.android.spotted.model.tag.TagWriteOutcome
import com.android.spotted.model.tag.TagWriter
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserProfile
import com.android.spotted.ui.components.UnsupportedTagWriter
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [LinkTagScreen] wired to a real [LinkTagViewModel] over fakes. The tag fakes of the unit tests
 * are not visible here, so small ones are defined at the bottom.
 */
@RunWith(AndroidJUnit4::class)
class LinkTagScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val tags = InMemoryPetTagRepository()
  private val writer = ScriptedTagWriter()
  private lateinit var viewModel: LinkTagViewModel
  private var done = 0
  private var cancelled = 0

  @Before
  fun setUp() {
    val auth = FakeAuthRepository()
    val users = FakeUserRepository()
    val pets = FakePetRepository()
    runBlocking {
      val uid = auth.signInWithGoogle("token").getOrThrow().uid
      users.saveUser(
          UserProfile(uid = uid, name = "Rayen", phone = "+41790000012", email = "r@x.ch")
      )
      pets.addPet(Pet(id = PET_ID, ownerId = uid, name = "Luna", species = Species.CAT))
    }
    viewModel = LinkTagViewModel(PET_ID, tags, pets, users, auth)
  }

  private fun setScreen() {
    composeTestRule.setContent {
      LinkTagScreen(
          viewModel = viewModel,
          onDone = { done++ },
          onCancel = { cancelled++ },
          writer = writer,
          nfcAvailability = NfcAvailability.AVAILABLE,
      )
    }
    waitFor(hasText("Luna's tag"))
  }

  private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) =
      composeTestRule.waitUntil(5_000) {
        composeTestRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
      }

  private fun waitForStep(step: LinkTagStep) =
      composeTestRule.waitUntil(5_000) { viewModel.uiState.value.step == step }

  private fun click(tag: String) = composeTestRule.onNodeWithTag(tag).performClick()

  @Test
  fun linksTheTagWithTheChosenContactsThenFinishes() {
    setScreen()

    click(LinkTagScreenTestTags.EMAIL_SWITCH)
    click(LinkTagScreenTestTags.START_BUTTON)
    waitForStep(LinkTagStep.Success)
    waitFor(hasTestTag(LinkTagScreenTestTags.DONE_BUTTON))
    click(LinkTagScreenTestTags.DONE_BUTTON)

    val tag = tags.tags.values.single()
    assertEquals(PET_ID, tag.petId)
    assertTrue(tag.showPhone)
    assertFalse(tag.showEmail)
    assertEquals(1, done)
  }

  @Test
  fun retryAfterAFailedWriteLinksTheTag() {
    writer.next = { Result.failure(TagWriteException(TagWriteError.TAG_LOST)) }
    setScreen()

    click(LinkTagScreenTestTags.START_BUTTON)
    waitFor(hasTestTag(LinkTagScreenTestTags.RETRY_BUTTON))
    writer.next = { Result.success(TagWriteOutcome.Written(it)) }
    click(LinkTagScreenTestTags.RETRY_BUTTON)
    waitForStep(LinkTagStep.Success)

    assertEquals(1, tags.tags.size)
  }

  @Test
  fun cancelWhileWaitingStopsAndLeaves() {
    writer.next = { awaitCancellation() }
    setScreen()

    click(LinkTagScreenTestTags.START_BUTTON)
    waitForStep(LinkTagStep.WaitingForTag)
    click(LinkTagScreenTestTags.CANCEL_BUTTON)

    assertEquals(LinkTagStep.Idle, viewModel.uiState.value.step)
    assertEquals(1, cancelled)
    assertTrue(tags.tags.isEmpty())
  }

  @Test
  fun pausingTheScreenStopsWaiting() {
    writer.next = { awaitCancellation() }
    setScreen()

    click(LinkTagScreenTestTags.START_BUTTON)
    waitForStep(LinkTagStep.WaitingForTag)
    composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)

    waitForStep(LinkTagStep.Idle)
    assertEquals(0, cancelled)
  }

  @Test
  fun defaultsUseThePhonesNfc() {
    composeTestRule.setContent { LinkTagScreen(viewModel = viewModel) }

    waitFor(hasText("Luna's tag"))
    composeTestRule.onNodeWithText("Luna's tag").assertIsDisplayed()
  }

  @Test
  fun nfcSettingsOpenTheNfcScreen() {
    val started = mutableListOf<String?>()

    openNfcSettings { started += it.action }

    assertEquals(listOf(Settings.ACTION_NFC_SETTINGS), started)
  }

  @Test
  fun nfcSettingsFallBackToWirelessSettings() {
    val started = mutableListOf<String?>()

    openNfcSettings { intent: Intent ->
      started += intent.action
      if (intent.action == Settings.ACTION_NFC_SETTINGS) throw ActivityNotFoundException()
    }

    assertEquals(listOf(Settings.ACTION_NFC_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS), started)
  }

  @Test
  fun withoutAnActivityTheWriterReportsNoNfc() = runBlocking {
    val error = UnsupportedTagWriter.writeOnNextTap("0000000000000000000000000000ABCD")

    assertEquals(
        TagWriteError.NFC_UNAVAILABLE,
        (error.exceptionOrNull() as TagWriteException).error,
    )
  }

  /** Each tap runs [next], which writes the new id by default. */
  private class ScriptedTagWriter : TagWriter {
    var next: suspend (String) -> Result<TagWriteOutcome> = {
      Result.success(TagWriteOutcome.Written(it))
    }

    override suspend fun writeOnNextTap(newTagId: String) = next(newTagId)
  }

  /** Just enough of the tag repository for the link flow. */
  private class InMemoryPetTagRepository : PetTagRepository {
    val tags = mutableMapOf<String, PetTag>()
    private var lastId = 0

    override fun getNewId() = "%032X".format(++lastId)

    override suspend fun linkTag(tagId: String, petId: String): Result<Unit> {
      tags[tagId] = PetTag(tagId, petId, ownerId = "owner", linkedAtMillis = 0L)
      return Result.success(Unit)
    }

    override suspend fun unlinkTag(tagId: String): Result<Unit> {
      tags.remove(tagId)
      return Result.success(Unit)
    }

    override suspend fun getTag(tagId: String) = Result.success(tags[tagId])

    override suspend fun getTagForPet(petId: String) =
        Result.success(tags.values.firstOrNull { it.petId == petId })

    override suspend fun setVisibleContacts(
        tagId: String,
        showPhone: Boolean,
        showEmail: Boolean,
        phone: String?,
        email: String?,
    ): Result<Unit> {
      val tag = tags[tagId] ?: return Result.failure(NoSuchElementException(tagId))
      tags[tagId] = tag.copy(showPhone = showPhone, showEmail = showEmail)
      return Result.success(Unit)
    }
  }

  private companion object {
    const val PET_ID = "pet-1"
  }
}
