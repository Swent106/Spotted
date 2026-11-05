// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.photo

import com.android.spotted.model.Pet.Species

/**
 * What [AnimalDetector] found in a photo.
 *
 * @property isAnimal whether the photo shows an animal.
 * @property species DOG, CAT, or OTHER for any other animal; null when there is no animal.
 */
data class AnimalDetection(val isAnimal: Boolean, val species: Species?)
