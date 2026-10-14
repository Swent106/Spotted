package com.android.spotted.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.android.spotted.R
import com.android.spotted.model.Location
import com.android.spotted.ui.theme.PlusJakartaSans
import com.android.spotted.ui.theme.SpottedBlue
import com.android.spotted.ui.theme.SpottedInk
import com.android.spotted.ui.theme.SpottedMapBackground
import java.io.File
import kotlin.math.log2
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/** Test tags used by UI tests to find the elements of the [MapPicker]. */
object MapPickerTestTags {
  const val MAP = "mapPicker_map"
  const val HINT = "mapPicker_hint"
  const val RADIUS_LABEL = "mapPicker_radiusLabel"
}

/** Where the map is centred while no location is chosen yet: Lausanne. */
val DefaultMapCenter = Location(latitude = 46.5197, longitude = 6.6323)

/**
 * Reusable OpenStreetMap map on which the user places a pin by tapping, with a circle of [radiusKm]
 * around it. No API key is needed.
 *
 * Stateless: the chosen [location] comes from the caller, and each tap is reported through
 * [onLocationChange]. Used by Personal info (home location) and meant for Report missing (last
 * known location of the pet). The caller sets the size through [modifier].
 *
 * @param hint shown while no location is chosen.
 * @param radiusLabel shown once a location is chosen, built from [radiusKm].
 */
@Composable
fun MapPicker(
    location: Location?,
    radiusKm: Int,
    onLocationChange: (Location) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Tap the map to place a pin",
    radiusLabel: (Int) -> String = { "Within $it km" },
) {
  val context = LocalContext.current
  val currentOnLocationChange by rememberUpdatedState(onLocationChange)
  val mapView = remember { createMapView(context) { tapped -> currentOnLocationChange(tapped) } }
  DisposableEffect(mapView) {
    mapView.onResume()
    onDispose {
      mapView.onPause()
      mapView.onDetach()
    }
  }
  // Keep the pin and the whole circle in view when the location or the radius changes, but not
  // on other recompositions, so the user's panning is not undone.
  val center = (location ?: DefaultMapCenter).toGeoPoint()
  LaunchedEffect(center, radiusKm) {
    mapView.controller.setZoom(zoomFor(radiusKm).toDouble())
    mapView.controller.setCenter(center)
  }

  Box(
      modifier
          .clip(RoundedCornerShape(16.dp))
          .background(SpottedMapBackground)
          .testTag(MapPickerTestTags.MAP)
  ) {
    AndroidView(
        factory = { mapView },
        modifier = Modifier.matchParentSize(),
        update = { map -> map.showPin(location?.toGeoPoint(), radiusKm) },
    )
    val (text, tag) =
        if (location == null) hint to MapPickerTestTags.HINT
        else radiusLabel(radiusKm) to MapPickerTestTags.RADIUS_LABEL
    Text(
        text,
        style =
            TextStyle(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                color = SpottedInk,
            ),
        modifier =
            Modifier.align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White.copy(alpha = 0.95f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .testTag(tag),
    )
  }
}

private const val METERS_PER_KM = 1000.0

/** Vertical position of the pin's tip in `ic_map_pin`, as a fraction of its height (22 / 24). */
private const val PIN_TIP_Y = 22f / 24f

/** Zoom level that fits a circle of [radiusKm] in a small map; one level per doubling. */
internal fun zoomFor(radiusKm: Int): Float = (12.0 - log2(radiusKm.toDouble())).toFloat()

private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)

private fun createMapView(context: Context, onTap: (Location) -> Unit): MapView {
  Configuration.getInstance().apply {
    // OpenStreetMap's tile servers require an identifying user agent.
    userAgentValue = context.packageName
    osmdroidBasePath = File(context.cacheDir, "osmdroid")
    osmdroidTileCache = File(osmdroidBasePath, "tiles")
  }
  return MapView(context).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    isTilesScaledToDpi = true
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    overlays.add(
        MapEventsOverlay(
            object : MapEventsReceiver {
              override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                onTap(Location(point.latitude, point.longitude))
                return true
              }

              override fun longPressHelper(point: GeoPoint) = false
            }
        )
    )
  }
}

/**
 * Replaces the pin and the radius circle. They are drawn below the tap overlay, so tapping inside
 * the circle still moves the pin.
 */
private fun MapView.showPin(position: GeoPoint?, radiusKm: Int) {
  overlays.removeAll { it is Marker || it is Polygon }
  if (position != null) {
    val circle =
        Polygon(this).apply {
          points = Polygon.pointsAsCircle(position, radiusKm * METERS_PER_KM)
          fillPaint.color = SpottedBlue.copy(alpha = 0.15f).toArgb()
          outlinePaint.color = SpottedBlue.toArgb()
          outlinePaint.strokeWidth = 4f
        }
    val pin =
        Marker(this).apply {
          this.position = position
          icon = ContextCompat.getDrawable(context, R.drawable.ic_map_pin)
          setAnchor(Marker.ANCHOR_CENTER, PIN_TIP_Y)
          infoWindow = null
        }
    overlays.add(0, circle)
    overlays.add(1, pin)
  }
  invalidate()
}
