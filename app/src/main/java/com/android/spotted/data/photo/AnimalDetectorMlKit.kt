package com.android.spotted.data.photo

import android.content.Context
import androidx.core.net.toUri
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.photo.AnimalDetection
import com.android.spotted.model.photo.AnimalDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * [AnimalDetector] with ML Kit Image Labeling. The model is bundled in the APK, so it works offline
 * and nothing is sent to a server.
 *
 * @property context used to read the photo file.
 */
class AnimalDetectorMlKit(private val context: Context) : AnimalDetector {

  override suspend fun detect(photoUri: String): Result<AnimalDetection> {
    return try {
      val labels =
          withContext(Dispatchers.IO) {
            val image = InputImage.fromFilePath(context, photoUri.toUri())
            ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS).use { labeler ->
              labeler.process(image).await()
            }
          }
      Result.success(toDetection(labels.map { it.text to it.confidence }))
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) {
        throw e
      }
      Result.failure(e)
    }
  }
}

/**
 * Turns ML Kit's labels (text and confidence) into an [AnimalDetection]. Labels under
 * [MIN_CONFIDENCE] are ignored. The photo shows an animal when a label is in [ANIMAL_LABELS]; its
 * species is DOG or CAT, whichever ML Kit is the most confident about, else OTHER.
 */
internal fun toDetection(labels: List<Pair<String, Float>>): AnimalDetection {
  val animals =
      labels
          .filter { (_, confidence) -> confidence >= MIN_CONFIDENCE }
          .map { (text, confidence) -> text.lowercase() to confidence }
          .filter { (text, _) -> text in ANIMAL_LABELS }
  if (animals.isEmpty()) return AnimalDetection(isAnimal = false, species = null)

  val species =
      animals
          .filter { (text, _) -> text in SPECIES_BY_LABEL }
          .maxByOrNull { (_, confidence) -> confidence }
          ?.let { (text, _) -> SPECIES_BY_LABEL.getValue(text) } ?: Species.OTHER
  return AnimalDetection(isAnimal = true, species = species)
}

/** Least confidence for a label to count, from 0 to 1. */
internal const val MIN_CONFIDENCE = 0.6f

/** ML Kit labels (in lowercase) that mean the photo shows an animal. */
internal val ANIMAL_LABELS: Set<String> =
    setOf("dog", "cat", "bird", "pet", "rabbit", "hamster", "horse")

/** ML Kit labels that tell the species; any other animal is [Species.OTHER]. */
private val SPECIES_BY_LABEL = mapOf("dog" to Species.DOG, "cat" to Species.CAT)
