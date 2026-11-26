// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.Pet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.spotted.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks the breed lists of `res/values/breeds.xml`, whose ids and names are parallel arrays. */
@RunWith(RobolectricTestRunner::class)
class BreedResourcesTest {

  private val resources = ApplicationProvider.getApplicationContext<Context>().resources

  private fun checkList(idsRes: Int, namesRes: Int) {
    val ids = resources.getStringArray(idsRes).toList()
    val names = resources.getStringArray(namesRes).toList()

    assertEquals("ids and names must have the same size", ids.size, names.size)
    assertEquals("ids must be unique", ids.size, ids.toSet().size)
    assertTrue("ids must be lowercase snake_case", ids.all { it.matches(Regex("[a-z0-9_]+")) })
    assertTrue("names must not be blank", names.none { it.isBlank() })
    assertTrue(MIXED_BREED_ID in ids)
    assertTrue(UNKNOWN_BREED_ID in ids)
  }

  @Test fun dogBreeds_areConsistent() = checkList(R.array.dog_breed_ids, R.array.dog_breed_names)

  @Test fun catBreeds_areConsistent() = checkList(R.array.cat_breed_ids, R.array.cat_breed_names)
}
