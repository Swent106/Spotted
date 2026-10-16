package com.android.spotted.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.android.spotted.model.Location
import com.android.spotted.model.location.AddressRepository
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

  /** "Neighbourhood, City", falling back to the region or the country when the city is unknown. */
  private fun areaName(address: Address): String =
      listOfNotNull(
              address.subLocality,
              address.locality ?: address.subAdminArea ?: address.adminArea,
          )
          .ifEmpty { listOfNotNull(address.countryName) }
          .distinct()
          .joinToString(", ")
}
