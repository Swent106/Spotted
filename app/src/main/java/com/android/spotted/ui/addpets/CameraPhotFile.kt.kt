package com.android.spotted.ui.addpets

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Creates the files the camera app writes photos into.
 *
 * The camera app runs in another process, so it cannot write to our private storage directly. We
 * create an empty file in the cache folder and share it through our FileProvider as a `content://`
 * URI. The camera app gets temporary write access to that one file only.
 */
object CameraPhotoFile {

  /** Sub-folder of the cache folder; must match `cache-path` in `res/xml/file_paths.xml`. */
  private const val FOLDER = "camera"

  /** Camera files older than this are deleted; the upload always happens well before. */
  private val MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(1)

  /** Authority of the FileProvider; must match `android:authorities` in the manifest. */
  fun authority(context: Context): String = "${context.packageName}.fileprovider"

  /**
   * Creates a new empty file for the next photo and returns its shareable URI.
   *
   * Each photo gets its own file name, so the image loader never shows a cached older photo. Old
   * camera files are deleted first, so the cache folder doesn't keep growing: a photo is uploaded
   * right after it's taken, so a file older than a day is no longer needed.
   *
   * @param nowMillis current time; a parameter so tests can simulate time passing.
   */
  fun newPhotoUri(context: Context, nowMillis: Long = System.currentTimeMillis()): Uri {
    val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
    deleteOldPhotos(folder, nowMillis)
    val file = File.createTempFile("pet_", ".jpg", folder)
    return FileProvider.getUriForFile(context, authority(context), file)
  }

  private fun deleteOldPhotos(folder: File, nowMillis: Long) {
    folder
        .listFiles()
        ?.filter { nowMillis - it.lastModified() > MAX_AGE_MILLIS }
        ?.forEach { it.delete() }
  }

  /** True if the device has a camera (front or back). */
  fun deviceHasCamera(context: Context): Boolean =
      context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}
