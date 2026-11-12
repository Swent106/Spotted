// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.location

import android.location.Address
import android.location.Geocoder
import com.android.spotted.model.Location
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AddressRepositoryGeocoderTest {

  private val lausanne = Location(46.507, 6.627)

  @Test
  fun areaName_isTheNeighbourhoodAndCity_withAFallbackForEachMissingField() {
    // subLocality, locality, subAdminArea, adminArea, country -> expected
    val cases =
        listOf(
            listOf("Ouchy", "Lausanne", "Lausanne District", "Vaud", "Switzerland") to
                "Ouchy, Lausanne",
            listOf(null, "Lausanne", null, "Vaud", "Switzerland") to "Lausanne",
            listOf("Ouchy", null, "Lausanne District", "Vaud", null) to "Ouchy, Lausanne District",
            listOf("Ouchy", null, null, "Vaud", null) to "Ouchy, Vaud",
            listOf(null, null, null, null, "Switzerland") to "Switzerland",
            listOf("Lausanne", "Lausanne", null, null, null) to "Lausanne",
            // Some geocoders return "" rather than null
            listOf("", " Lausanne ", null, null, null) to "Lausanne",
            listOf(null, null, null, null, null) to "",
        )

    cases.forEach { (fields, expected) ->
      assertEquals(
          fields.toString(),
          expected,
          areaName(fields[0], fields[1], fields[2], fields[3], fields[4]),
      )
    }
  }

  @Test
  @Config(sdk = [33])
  fun fromAndroid13_usesTheListenerLookup_andReportsItsErrors() = runTest {
    val found = geocoderAnswering { listener -> listener.onGeocode(mutableListOf(ouchy())) }
    assertEquals(
        "Ouchy, Lausanne",
        AddressRepositoryGeocoder(found).areaName(lausanne).getOrThrow(),
    )

    val failing = geocoderAnswering { listener -> listener.onError("service not available") }
    val failure = AddressRepositoryGeocoder(failing).areaName(lausanne).exceptionOrNull()
    assertTrue(failure.toString(), failure is IOException)

    val nothing = geocoderAnswering { listener -> listener.onGeocode(mutableListOf()) }
    val missing = AddressRepositoryGeocoder(nothing).areaName(lausanne).exceptionOrNull()
    assertTrue(missing.toString(), missing is NoSuchElementException)
  }

  @Test
  @Config(sdk = [32])
  fun beforeAndroid13_usesTheBlockingLookup() = runTest {
    @Suppress("DEPRECATION")
    val geocoder =
        mock<Geocoder> {
          on { getFromLocation(eq(lausanne.latitude), eq(lausanne.longitude), eq(1)) } doReturn
              listOf(ouchy())
        }

    assertEquals(
        "Ouchy, Lausanne",
        AddressRepositoryGeocoder(geocoder).areaName(lausanne).getOrThrow(),
    )
  }

  /** A [Geocoder] whose listener lookup is answered by [answer]. */
  private fun geocoderAnswering(answer: (Geocoder.GeocodeListener) -> Unit): Geocoder = mock {
    on { getFromLocation(any(), any(), any(), anyOrNull<Geocoder.GeocodeListener>()) } doAnswer
        {
          answer(it.getArgument(3))
        }
  }

  private fun ouchy() =
      Address(Locale.ROOT).apply {
        subLocality = "Ouchy"
        locality = "Lausanne"
      }
}
