// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Pet.PetSize
import com.android.spotted.model.Pet.Sex
import com.android.spotted.ui.theme.SpottedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetDetailsChipsTest {

  @get:Rule val composeTestRule = createComposeRule()

  private data class Details(val ageYears: Int?, val sex: Sex, val size: PetSize)

  private val allKnown = Details(ageYears = 5, sex = Sex.MALE, size = PetSize.MEDIUM)
  private val allUnknown = Details(ageYears = null, sex = Sex.UNKNOWN, size = PetSize.UNKNOWN)

  /** What the chips show; tests change it to see the chips update without a new setContent. */
  private var details by mutableStateOf(allKnown)

  private fun setChips(initial: Details) {
    details = initial
    composeTestRule.setContent {
      SpottedTheme {
        PetDetailsChips(details.ageYears, details.sex, details.size, testTagPrefix = PREFIX)
      }
    }
  }

  private fun age() = composeTestRule.onNodeWithTag(PetDetailsChipsTestTags.age(PREFIX))

  private fun sex() = composeTestRule.onNodeWithTag(PetDetailsChipsTestTags.sex(PREFIX))

  private fun size() = composeTestRule.onNodeWithTag(PetDetailsChipsTestTags.size(PREFIX))

  @Test
  fun knownValues_showOneChipEach() {
    setChips(allKnown)

    age().assertTextEquals("5 years")
    sex().assertTextEquals("Male")
    size().assertTextEquals("Medium")

    details = Details(ageYears = 1, sex = Sex.FEMALE, size = PetSize.LARGE)
    age().assertTextEquals("1 year")
    sex().assertTextEquals("Female")
    size().assertTextEquals("Large")

    details = details.copy(ageYears = 0, size = PetSize.SMALL)
    age().assertTextEquals("Under 1 year")
    size().assertTextEquals("Small")
  }

  @Test
  fun eachUnknownValue_hidesOnlyItsChip() {
    setChips(allKnown.copy(ageYears = null))
    age().assertDoesNotExist()
    sex().assertTextEquals("Male")

    details = allKnown.copy(sex = Sex.UNKNOWN)
    sex().assertDoesNotExist()
    age().assertTextEquals("5 years")

    details = allKnown.copy(size = PetSize.UNKNOWN)
    size().assertDoesNotExist()
    sex().assertTextEquals("Male")
  }

  @Test
  fun allUnknown_showsNothing() {
    setChips(allUnknown)

    age().assertDoesNotExist()
    sex().assertDoesNotExist()
    size().assertDoesNotExist()
  }

  private companion object {
    const val PREFIX = "test"
  }
}
