package com.android.spotted.model.fakes

import com.android.spotted.model.Location
import com.android.spotted.model.location.AddressRepository

/**
 * [AddressRepository] for ViewModel tests.
 * - [name]: the name returned for any position.
 * - [failure]: set it to make [areaName] fail with it.
 */
class FakeAddressRepository(var name: String = "Ouchy, Lausanne") : AddressRepository {
  var failure: Throwable? = null

  override suspend fun areaName(location: Location): Result<String> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(name)
  }
}
