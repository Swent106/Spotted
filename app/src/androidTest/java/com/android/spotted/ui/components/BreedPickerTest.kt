// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.android.spotted.model.Pet.BreedOption
import com.android.spotted.model.Pet.MIXED_BREED_ID
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.Pet.UNKNOWN_BREED_ID
import com.android.spotted.ui.theme.SpottedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BreedPickerTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val breeds =
      listOf(
          BreedOption(MIXED_BREED_ID, "Mixed"),
          BreedOption(UNKNOWN_BREED_ID, "Unknown"),
          BreedOption("beagle", "Beagle"),
          BreedOption("labrador_retriever", "Labrador Retriever"),
          BreedOption("yorkshire_terrier", "Yorkshire Terrier"),
      )

  private fun setSheetContent(onBreedSelected: (String) -> Unit = {}) {
    composeTestRule.setContent {
      SpottedTheme {
        var query by remember { mutableStateOf("") }
        BreedPickerSheetContent(
            breeds = breeds,
            query = query,
            onQueryChange = { query = it },
            onBreedSelected = onBreedSelected,
        )
      }
    }
  }

  private fun tag(id: String) = BreedPickerTestTags.item(id)

  @Test
  fun search_filtersTheList_ignoringCase() {
    setSheetContent()

    composeTestRule.onNodeWithTag(BreedPickerTestTags.SEARCH).performTextInput("LAB")

    composeTestRule.onNodeWithTag(tag("labrador_retriever")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(tag("beagle")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(tag("yorkshire_terrier")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(tag(MIXED_BREED_ID)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(tag(UNKNOWN_BREED_ID)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BreedPickerTestTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun search_withoutMatch_showsEmptyMessage() {
    setSheetContent()

    composeTestRule.onNodeWithTag(BreedPickerTestTags.SEARCH).performTextInput("xyz")

    composeTestRule.onNodeWithTag(BreedPickerTestTags.EMPTY).assertIsDisplayed()
    composeTestRule.onNodeWithTag(tag("beagle")).assertDoesNotExist()
  }

  @Test
  fun pickingABreed_returnsItsId() {
    var picked: String? = null
    setSheetContent(onBreedSelected = { picked = it })

    composeTestRule.onNodeWithTag(BreedPickerTestTags.SEARCH).performTextInput("york")
    composeTestRule.onNodeWithTag(tag("yorkshire_terrier")).performClick()

    assertEquals("yorkshire_terrier", picked)
  }

  @Test
  fun picker_opensSheet_andShowsPickedBreed() {
    var picked: String? = null
    composeTestRule.setContent {
      SpottedTheme {
        var selected by remember { mutableStateOf<String?>(null) }
        BreedPicker(
            species = Species.DOG,
            selectedBreedId = selected,
            onBreedSelected = {
              picked = it
              selected = it
            },
        )
      }
    }

    composeTestRule.onNodeWithTag(BreedPickerTestTags.FIELD).assertTextContains("Select a breed")
    composeTestRule.onNodeWithTag(BreedPickerTestTags.FIELD).performClick()
    composeTestRule.onNodeWithTag(BreedPickerTestTags.SEARCH).performTextInput("beagle")
    composeTestRule.onNodeWithTag(tag("beagle")).performClick()

    composeTestRule.waitUntil(5_000) {
      composeTestRule
          .onAllNodes(hasTestTag(BreedPickerTestTags.SEARCH))
          .fetchSemanticsNodes()
          .isEmpty()
    }
    assertEquals("beagle", picked)
    composeTestRule.onNodeWithTag(BreedPickerTestTags.FIELD).assertTextContains("Beagle")
  }

  @Test
  fun picker_listsCatBreeds_forCats() {
    composeTestRule.setContent {
      SpottedTheme {
        BreedPicker(species = Species.CAT, selectedBreedId = null, onBreedSelected = {})
      }
    }

    composeTestRule.onNodeWithTag(BreedPickerTestTags.FIELD).performClick()
    composeTestRule
        .onNodeWithTag(BreedPickerTestTags.LIST)
        .performScrollToNode(hasTestTag(tag("maine_coon")))
    composeTestRule.onNodeWithTag(tag("maine_coon")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(tag("labrador_retriever")).assertDoesNotExist()
  }

  @Test
  fun picker_showsNothing_forOtherSpecies() {
    composeTestRule.setContent {
      SpottedTheme {
        BreedPicker(species = Species.OTHER, selectedBreedId = null, onBreedSelected = {})
      }
    }

    composeTestRule.onNodeWithTag(BreedPickerTestTags.FIELD).assertDoesNotExist()
  }
}
