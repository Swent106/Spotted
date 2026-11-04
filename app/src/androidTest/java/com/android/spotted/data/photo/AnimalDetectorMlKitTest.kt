// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs [AnimalDetectorMlKit] with the real ML Kit model, on photos written to the cache. */
@RunWith(AndroidJUnit4::class)
class AnimalDetectorMlKitTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val detector = AnimalDetectorMlKit(context)

  @Test
  fun plainPhoto_showsNoAnimal() = runTest {
    val photo = File(context.cacheDir, "plain.png")
    photo.outputStream().use { out ->
      Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
          .apply { eraseColor(Color.GRAY) }
          .compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    val detection = detector.detect(Uri.fromFile(photo).toString()).getOrThrow()

    assertFalse(detection.isAnimal)
    assertNull(detection.species)
  }

  @Test
  fun missingPhoto_failsWithoutThrowing() = runTest {
    val missing = File(context.cacheDir, "does-not-exist.jpg")

    assertTrue(detector.detect(Uri.fromFile(missing).toString()).isFailure)
  }
}
