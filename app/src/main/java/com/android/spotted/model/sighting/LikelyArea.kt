// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.sighting

import com.android.spotted.model.Location

/**
 * Where a lost pet most likely is, estimated from the sightings its owner confirmed (#181).
 *
 * @property center Most likely position of the pet.
 * @property radiusMeters Radius of the area around [center], in metres.
 */
data class LikelyArea(
    val center: Location,
    val radiusMeters: Double,
)
