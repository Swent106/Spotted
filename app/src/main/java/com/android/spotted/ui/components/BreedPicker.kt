// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.spotted.R
import com.android.spotted.model.Pet.BreedOption
import com.android.spotted.model.Pet.MIXED_BREED_ID
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.Pet.UNKNOWN_BREED_ID
import com.android.spotted.model.Pet.filterBreeds
import com.android.spotted.ui.theme.SpottedTheme

/** Test tags used by UI tests to find the elements of the [BreedPicker]. */
object BreedPickerTestTags {
  const val FIELD = "breedPicker_field"
  const val SEARCH = "breedPicker_search"
  const val LIST = "breedPicker_list"
  const val EMPTY = "breedPicker_empty"

  fun item(breedId: String) = "breedPicker_item_$breedId"
}

/** Breeds of [species] from `res/values/breeds.xml`; empty for [Species.OTHER] (free text). */
@Composable
fun breedOptions(species: Species): List<BreedOption> {
  val (idsRes, namesRes) =
      when (species) {
        Species.DOG -> R.array.dog_breed_ids to R.array.dog_breed_names
        Species.CAT -> R.array.cat_breed_ids to R.array.cat_breed_names
        Species.OTHER -> return emptyList()
      }
  val ids = stringArrayResource(idsRes)
  val names = stringArrayResource(namesRes)
  return remember(ids, names) { ids.zip(names) { id, name -> BreedOption(id, name) } }
}

/**
 * Breed field that opens a bottom sheet to search and pick a breed of [species]. The picked id goes
 * to [onBreedSelected]; [selectedBreedId] comes from the caller. Shows nothing for [Species.OTHER].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreedPicker(
    species: Species,
    selectedBreedId: String?,
    onBreedSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val breeds = breedOptions(species)
  if (breeds.isEmpty()) return
  val colors = SpottedTheme.colors
  val shape = RoundedCornerShape(10.dp)
  var showSheet by rememberSaveable { mutableStateOf(false) }
  var query by rememberSaveable { mutableStateOf("") }
  val selectedName = breeds.firstOrNull { it.id == selectedBreedId }?.name

  Column(modifier = modifier) {
    Text(
        text = stringResource(R.string.breed_picker_label),
        style = MaterialTheme.typography.labelMedium,
        color = colors.textLabel,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(colors.inputBackground, shape)
                .border(1.dp, colors.buttonBorder, shape)
                .clickable {
                  query = ""
                  showSheet = true
                }
                .padding(horizontal = 12.dp)
                .testTag(BreedPickerTestTags.FIELD),
    ) {
      Text(
          text = selectedName ?: stringResource(R.string.breed_picker_placeholder),
          color = if (selectedName != null) colors.ink else colors.textLabel,
          modifier = Modifier.weight(1f),
      )
      // The only chevron drawable points left; turned to point down.
      Icon(
          painterResource(R.drawable.ic_chevron_left),
          contentDescription = null,
          tint = colors.textLabel,
          modifier = Modifier.rotate(-90f),
      )
    }
  }

  if (showSheet) {
    ModalBottomSheet(
        onDismissRequest = { showSheet = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
      BreedPickerSheetContent(breeds, query, { query = it }) {
        showSheet = false
        onBreedSelected(it)
      }
    }
  }
}

/** Search field and the breeds matching [query], "Mixed" and "Unknown" first. Stateless. */
@Composable
fun BreedPickerSheetContent(
    breeds: List<BreedOption>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBreedSelected: (String) -> Unit,
) {
  val colors = SpottedTheme.colors
  val shown = filterBreeds(breeds, query)
  val noMatch = query.isNotBlank() && shown.all { it.id in PINNED_IDS }

  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    Text(
        text = stringResource(R.string.breed_picker_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.breed_picker_search_placeholder)) },
        modifier = Modifier.fillMaxWidth().testTag(BreedPickerTestTags.SEARCH),
    )
    LazyColumn(modifier = Modifier.fillMaxWidth().testTag(BreedPickerTestTags.LIST)) {
      items(shown, key = { it.id }) { breed ->
        Text(
            text = breed.name,
            color = if (breed.id in PINNED_IDS) colors.textSecondary else colors.ink,
            modifier =
                Modifier.fillMaxWidth()
                    .clickable { onBreedSelected(breed.id) }
                    .padding(vertical = 14.dp, horizontal = 4.dp)
                    .testTag(BreedPickerTestTags.item(breed.id)),
        )
      }
      if (noMatch) {
        item {
          Text(
              text = stringResource(R.string.breed_picker_no_match, query.trim()),
              color = colors.textSecondary,
              modifier = Modifier.padding(vertical = 16.dp).testTag(BreedPickerTestTags.EMPTY),
          )
        }
      }
    }
  }
}

private val PINNED_IDS = setOf(MIXED_BREED_ID, UNKNOWN_BREED_ID)
