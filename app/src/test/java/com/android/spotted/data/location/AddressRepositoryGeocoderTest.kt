// Portions of this code were generated with the help of Copilot.
// Co-authored with Copilot.
package com.android.spotted.data.location

import android.location.Address
import android.location.Geocoder
import com.android.spotted.model.Location
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Suppress("DEPRECATION")
class AddressRepositoryGeocoderTest {

  private lateinit var geocoder: Geocoder
  private lateinit var repository: AddressRepositoryGeocoder

  @Before
  fun setUp() {
    geocoder = mock()
    repository = AddressRepositoryGeocoder(geocoder)
  }

  @Test
  fun areaName_combinesNeighbourhoodAndCity() = runTest {
    whenever(geocoder.getFromLocation(46.5, 6.6, 1))
        .thenReturn(listOf(address(subLocality = "Ouchy", locality = "Lausanne")))

    assertEquals("Ouchy, Lausanne", repository.areaName(Location(46.5, 6.6)).getOrThrow())
  }

  @Test
  fun areaName_fallsBackToAdministrativeAreaAndCountry() = runTest {
    whenever(geocoder.getFromLocation(46.5, 6.6, 1))
        .thenReturn(listOf(address(subLocality = "Centre", adminArea = "Vaud")))
    assertEquals("Centre, Vaud", repository.areaName(Location(46.5, 6.6)).getOrThrow())

    whenever(geocoder.getFromLocation(46.5, 6.6, 1))
        .thenReturn(listOf(address(subAdminArea = "Lausanne district")))
    assertEquals("Lausanne district", repository.areaName(Location(46.5, 6.6)).getOrThrow())

    whenever(geocoder.getFromLocation(46.5, 6.6, 1))
        .thenReturn(listOf(address(countryName = "Switzerland")))
    assertEquals("Switzerland", repository.areaName(Location(46.5, 6.6)).getOrThrow())
  }

  @Test
  fun areaName_failsWhenNoAddressOrPlaceNameIsAvailable() = runTest {
    whenever(geocoder.getFromLocation(46.5, 6.6, 1)).thenReturn(null)

    assertTrue(repository.areaName(Location(46.5, 6.6)).exceptionOrNull() is NoSuchElementException)

    whenever(geocoder.getFromLocation(46.5, 6.6, 1)).thenReturn(emptyList())
    assertTrue(repository.areaName(Location(46.5, 6.6)).exceptionOrNull() is NoSuchElementException)

    whenever(geocoder.getFromLocation(46.5, 6.6, 1)).thenReturn(listOf(address()))
    assertTrue(repository.areaName(Location(46.5, 6.6)).exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun locate_returnsCoordinatesAndFailsWhenNoAddressIsFound() = runTest {
    whenever(geocoder.getFromLocationName("Ouchy, Lausanne", 1))
        .thenReturn(listOf(address(latitude = 46.5, longitude = 6.6)))

    assertEquals(
        Location(46.5, 6.6),
        repository.locate("Ouchy, Lausanne").getOrThrow(),
    )

    whenever(geocoder.getFromLocationName("Unknown", 1)).thenReturn(emptyList())
    assertTrue(repository.locate("Unknown").exceptionOrNull() is NoSuchElementException)

    whenever(geocoder.getFromLocationName("Missing", 1)).thenReturn(null)
    assertTrue(repository.locate("Missing").exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun geocoderFailuresBecomeResultsAndCancellationIsRethrown() = runTest {
    val error = IOException("Geocoder unavailable")
    whenever(geocoder.getFromLocation(46.5, 6.6, 1)).thenThrow(error)
    val failure = repository.areaName(Location(46.5, 6.6)).exceptionOrNull()
    assertTrue(failure is IOException)
    assertEquals(error.message, failure?.message)

    val cancellation = CancellationException("Cancelled")
    doThrow(cancellation).whenever(geocoder).getFromLocation(46.5, 6.6, 1)
    try {
      repository.areaName(Location(46.5, 6.6))
      throw AssertionError("Expected cancellation to be rethrown")
    } catch (actual: CancellationException) {
      assertEquals(cancellation.message, actual.message)
    }

    doThrow(cancellation).whenever(geocoder).getFromLocationName("Ouchy", 1)
    try {
      repository.locate("Ouchy")
      throw AssertionError("Expected cancellation to be rethrown")
    } catch (actual: CancellationException) {
      assertEquals(cancellation.message, actual.message)
    }
  }

  private fun address(
      subLocality: String? = null,
      locality: String? = null,
      subAdminArea: String? = null,
      adminArea: String? = null,
      countryName: String? = null,
      latitude: Double = 0.0,
      longitude: Double = 0.0,
  ) =
      Address(Locale.US).apply {
        this.subLocality = subLocality
        this.locality = locality
        this.subAdminArea = subAdminArea
        this.adminArea = adminArea
        this.countryName = countryName
        this.latitude = latitude
        this.longitude = longitude
      }
}
