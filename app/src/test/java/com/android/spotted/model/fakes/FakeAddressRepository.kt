// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.Location
import com.android.spotted.model.location.AddressRepository
import com.android.spotted.model.location.AddressSuggestion

/**
 * [AddressRepository] for ViewModel tests.
 * - [name]: the name returned for any position.
 * - [location]: the position returned for any place name.
 * - [suggestions]: the places proposed for any query; [suggestQueries] records each query and
 *   [lastSuggestNear] the last reference position.
 * - [failure]: set it to make every function fail with it.
 */
class FakeAddressRepository(
    var name: String = "Ouchy, Lausanne",
    var location: Location = Location(46.5070, 6.6270),
    var suggestions: List<AddressSuggestion> = emptyList(),
) : AddressRepository {
  var failure: Throwable? = null
  val suggestQueries = mutableListOf<String>()
  var lastSuggestNear: Location? = null

  override suspend fun suggest(query: String, near: Location?): Result<List<AddressSuggestion>> {
    suggestQueries += query
    lastSuggestNear = near
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(suggestions)
  }

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
