// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.android.spotted.model.Location
import com.android.spotted.model.distanceKm
import com.android.spotted.model.location.AddressRepository
import com.android.spotted.model.location.AddressSuggestion
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** [AddressRepository] backed by the device's [Geocoder]; needs a network connection. */
class AddressRepositoryGeocoder(private val geocoder: Geocoder) : AddressRepository {

  constructor(context: Context) : this(Geocoder(context.applicationContext))

  override suspend fun areaName(location: Location): Result<String> =
      try {
        val address = firstAddressAt(location)
        val name = address?.let {
          areaName(it.subLocality, it.locality, it.subAdminArea, it.adminArea, it.countryName)
        }
        if (name.isNullOrBlank()) Result.failure(NoSuchElementException("No place name found"))
        else Result.success(name)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  /**
   * From Android 13, the blocking lookup can return no address on some phones, so the listener
   * version is used there; the blocking one stays for older versions, which have nothing else.
   */
  private suspend fun firstAddressAt(location: Location): Address? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
          geocoder.getFromLocation(
              location.latitude,
              location.longitude,
              1,
              object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                  continuation.resume(addresses.firstOrNull())
                }

                override fun onError(errorMessage: String?) {
                  continuation.resumeWithException(IOException(errorMessage ?: "Geocoder failed"))
                }
              },
          )
        }
      } else {
        withContext(Dispatchers.IO) {
          @Suppress("DEPRECATION")
          geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
        }
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
    val area =
        areaName(
            address.subLocality,
            address.locality,
            address.subAdminArea,
            address.adminArea,
            address.countryName,
        )
    val label = line?.takeIf { it.isNotBlank() } ?: area.takeIf { it.isNotBlank() }
    return label?.let { AddressSuggestion(it, Location(address.latitude, address.longitude)) }
  }

  private companion object {
    /** Half-side of the nearby search box: about 55 km north and south. */
    const val NEARBY_BOX_DEGREES = 0.5
  }
}

/**
 * "Neighbourhood, City", falling back to the region for the city, and to the country when both are
 * unknown. Blank fields count as missing: some geocoders return "" instead of null.
 */
internal fun areaName(
    subLocality: String?,
    locality: String?,
    subAdminArea: String?,
    adminArea: String?,
    countryName: String?,
): String {
  val known = { field: String? -> field?.trim()?.takeIf { it.isNotEmpty() } }
  return listOfNotNull(
          known(subLocality),
          known(locality) ?: known(subAdminArea) ?: known(adminArea),
      )
      .ifEmpty { listOfNotNull(known(countryName)) }
      .distinct()
      .joinToString(", ")
}
