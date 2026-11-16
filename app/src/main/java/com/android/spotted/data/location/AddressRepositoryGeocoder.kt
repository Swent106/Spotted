// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.android.spotted.model.Location
import com.android.spotted.model.distanceKm
import com.android.spotted.model.location.AddressRepository
import com.android.spotted.model.location.AddressSuggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [AddressRepository] backed by the device's [Geocoder]; needs a network connection. */
class AddressRepositoryGeocoder(private val geocoder: Geocoder) : AddressRepository {

  constructor(context: Context) : this(Geocoder(context.applicationContext))

  override suspend fun areaName(location: Location): Result<String> =
      try {
        withContext(Dispatchers.IO) {
          // The listener version needs API 33; this one blocks, so it runs on the IO dispatcher
          @Suppress("DEPRECATION")
          val address =
              geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
          val name = address?.let(::areaName)
          if (name.isNullOrBlank()) Result.failure(NoSuchElementException("No place name found"))
          else Result.success(name)
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  override suspend fun locate(area: String): Result<Location> =
      try {
        withContext(Dispatchers.IO) {
          @Suppress("DEPRECATION")
          val address = geocoder.getFromLocationName(area, 1)?.firstOrNull()
          if (address == null) Result.failure(NoSuchElementException("No place named $area"))
          else Result.success(Location(address.latitude, address.longitude))
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  /**
   * Looks first in a box around [near], so local places come before same-named ones far away, then
   * anywhere when the box has none. The box only limits the search: results are sorted by distance.
   */
  override suspend fun suggest(query: String, near: Location?): Result<List<AddressSuggestion>> =
      try {
        withContext(Dispatchers.IO) {
          val nearby = near?.let { search(query, around = it) }.orEmpty()
          val addresses = nearby.ifEmpty { search(query, around = null) }
          val suggestions =
              addresses
                  .mapNotNull(::suggestion)
                  .distinctBy { it.label }
                  .let { found ->
                    if (near == null) found else found.sortedBy { near.distanceKm(it.location) }
                  }
                  .take(AddressRepository.MAX_SUGGESTIONS)
          Result.success(suggestions)
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  @Suppress("DEPRECATION")
  private fun search(query: String, around: Location?): List<Address> =
      if (around == null) {
            geocoder.getFromLocationName(query, AddressRepository.MAX_SUGGESTIONS)
          } else {
            geocoder.getFromLocationName(
                query,
                AddressRepository.MAX_SUGGESTIONS,
                // The geocoder rejects a box that goes past the poles or the antimeridian
                (around.latitude - NEARBY_BOX_DEGREES).coerceAtLeast(-90.0),
                (around.longitude - NEARBY_BOX_DEGREES).coerceAtLeast(-180.0),
                (around.latitude + NEARBY_BOX_DEGREES).coerceAtMost(90.0),
                (around.longitude + NEARBY_BOX_DEGREES).coerceAtMost(180.0),
            )
          }
          .orEmpty()

  /** The full address line when there is one, else "Neighbourhood, City"; null if neither. */
  private fun suggestion(address: Address): AddressSuggestion? {
    val line = if (address.maxAddressLineIndex >= 0) address.getAddressLine(0) else null
    val label = line?.takeIf { it.isNotBlank() } ?: areaName(address).takeIf { it.isNotBlank() }
    return label?.let { AddressSuggestion(it, Location(address.latitude, address.longitude)) }
  }

  /** "Neighbourhood, City", falling back to the region or the country when the city is unknown. */
  private fun areaName(address: Address): String =
      listOfNotNull(
              address.subLocality,
              address.locality ?: address.subAdminArea ?: address.adminArea,
          )
          .ifEmpty { listOfNotNull(address.countryName) }
          .distinct()
          .joinToString(", ")

  private companion object {
    /** Half-side of the nearby search box: about 55 km north and south. */
    const val NEARBY_BOX_DEGREES = 0.5
  }
}
