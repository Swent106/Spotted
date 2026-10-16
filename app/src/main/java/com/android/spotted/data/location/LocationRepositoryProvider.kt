package com.android.spotted.data.location

import android.content.Context
import com.android.spotted.model.location.LocationRepository

/**
 * Gives the [LocationRepository] the ViewModel factories use: the device's GPS by default.
 * Instrumented tests replace [create] with a fixed position, so they don't depend on a real GPS.
 */
object LocationRepositoryProvider {
  @Volatile var create: (Context) -> LocationRepository = { LocationRepositoryFused(it) }
}
