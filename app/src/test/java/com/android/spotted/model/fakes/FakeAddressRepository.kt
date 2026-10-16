package com.android.spotted.model.fakes

import com.android.spotted.model.Location
import com.android.spotted.model.location.AddressRepository

/**
 * [AddressRepository] for ViewModel tests.
 * - [name]: the name returned for any position.
 * - [location]: the position returned for any place name.
 * - [failure]: set it to make both functions fail with it.
 */
class FakeAddressRepository(
    var name: String = "Ouchy, Lausanne",
    var location: Location = Location(46.5070, 6.6270),
) : AddressRepository {
  var failure: Throwable? = null

  override suspend fun areaName(location: Location): Result<String> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(name)
  }

  override suspend fun locate(area: String): Result<Location> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(location)
  }
}
