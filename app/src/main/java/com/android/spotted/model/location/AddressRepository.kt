// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.location

import com.android.spotted.model.Location

/**
 * Turns a position into a place name people recognise.
 *
 * Contract shared by every implementation (geocoder and fakes):
 * - errors are returned as [Result.failure], never thrown to the caller;
 * - implementations are main-safe (blocking I/O runs off the main thread).
 */
interface AddressRepository {

  /** Returns the neighbourhood and city of [location], e.g. "Ouchy, Lausanne". */
  suspend fun areaName(location: Location): Result<String>

  /** Returns the position of the place named [area], e.g. "Ouchy, Lausanne". */
  suspend fun locate(area: String): Result<Location>

  /**
   * Returns up to [MAX_SUGGESTIONS] places matching [query], the closest to [near] first (in the
   * order found when [near] is null). An empty list means no place matches.
   */
  suspend fun suggest(query: String, near: Location?): Result<List<AddressSuggestion>>

  companion object {
    const val MAX_SUGGESTIONS = 5
  }
}
