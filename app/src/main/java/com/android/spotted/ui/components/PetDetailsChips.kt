// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.android.spotted.R
import com.android.spotted.model.Pet.PetSize
import com.android.spotted.model.Pet.Sex
import com.android.spotted.ui.addpets.ChipFlow

/** Test tags of the [PetDetailsChips], built from the prefix of the screen that shows them. */
object PetDetailsChipsTestTags {
  fun age(prefix: String) = "${prefix}_age"

  fun sex(prefix: String) = "${prefix}_sex"

  fun size(prefix: String) = "${prefix}_size"
}

/** Label of a known sex; [Sex.UNKNOWN] has none, since it is never shown. */
@StringRes
internal fun Sex.labelRes(): Int? =
    when (this) {
      Sex.MALE -> R.string.pet_sex_male
      Sex.FEMALE -> R.string.pet_sex_female
      Sex.UNKNOWN -> null
    }

/** Label of a known size; [PetSize.UNKNOWN] has none, since it is never shown. */
@StringRes
internal fun PetSize.labelRes(): Int? =
    when (this) {
      PetSize.SMALL -> R.string.pet_size_small
      PetSize.MEDIUM -> R.string.pet_size_medium
      PetSize.LARGE -> R.string.pet_size_large
      PetSize.UNKNOWN -> null
    }

/**
 * The pet's age, sex and size as chips, shown under its name on Pet profile and Alert detail. Each
 * chip is hidden when its value is unknown, and nothing is shown when all three are. Test tags are
 * `<testTagPrefix>_age`, `_sex` and `_size`.
 */
@Composable
fun PetDetailsChips(ageYears: Int?, sex: Sex, size: PetSize, testTagPrefix: String) {
  val chips = buildList {
    if (ageYears != null) add(PetDetailsChipsTestTags.age(testTagPrefix) to ageLabel(ageYears))
    sex.labelRes()?.let { add(PetDetailsChipsTestTags.sex(testTagPrefix) to stringResource(it)) }
    size.labelRes()?.let { add(PetDetailsChipsTestTags.size(testTagPrefix) to stringResource(it)) }
  }
  if (chips.isEmpty()) return

  ChipFlow { chips.forEach { (tag, label) -> InfoChip(label, Modifier.testTag(tag)) } }
}

@Composable
private fun ageLabel(years: Int): String =
    if (years == 0) stringResource(R.string.pet_age_under_one_year)
    else pluralStringResource(R.plurals.pet_age_years, years, years)
