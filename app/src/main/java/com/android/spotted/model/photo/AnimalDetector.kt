// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.photo

/** Checks whether a photo shows an animal, on the device. */
interface AnimalDetector {

  /**
   * Looks for an animal in the photo at [photoUri] and tells its species. Main-safe; fails (without
   * throwing) when the photo can't be read or analysed.
   */
  suspend fun detect(photoUri: String): Result<AnimalDetection>
}
