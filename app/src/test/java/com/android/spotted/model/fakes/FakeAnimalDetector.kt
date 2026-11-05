// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.Pet.Species
import com.android.spotted.model.photo.AnimalDetection
import com.android.spotted.model.photo.AnimalDetector

/**
 * [AnimalDetector] for ViewModel tests.
 * - [result]: what every detection finds (a dog by default).
 * - [failure]: set it to make the detections fail with it.
 * - [checkedPhotos]: every photo checked, in order.
 */
class FakeAnimalDetector : AnimalDetector {
  var result = AnimalDetection(isAnimal = true, species = Species.DOG)
  var failure: Throwable? = null

  val checkedPhotos = mutableListOf<String>()

  override suspend fun detect(photoUri: String): Result<AnimalDetection> {
    checkedPhotos += photoUri
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(result)
  }
}
