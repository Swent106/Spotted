package com.android.spotted.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.spotted.R
import com.android.spotted.model.alert.Alert
import com.android.spotted.ui.components.DefaultMapCenter
import com.android.spotted.ui.components.PIN_TIP_Y
import com.android.spotted.ui.components.createMapView
import com.android.spotted.ui.home.HomeScreenTexts
import com.android.spotted.ui.home.HomeViewModel
import com.android.spotted.ui.theme.SpottedHomeBackground
import com.android.spotted.ui.theme.SpottedInk
import com.android.spotted.ui.theme.jakarta
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** Test tags used by UI tests to find the elements of the [AlertsMapContent]. */
object AlertsMapTestTags {
  const val SCREEN = "alertsMap_screen"
  const val BACK = "alertsMap_back"
  const val MAP = "alertsMap_map"
  const val EMPTY = "alertsMap_empty"
}

/** Zoom level of the map: a few kilometres around the first alert. */
private const val MAP_ZOOM = 13.0

/**
 * Map tab: the lost pets near the user, wired to the same [HomeViewModel] as Home. Navigation is
 * done by the caller through [onBack] and [onAlertClick].
 */
@Composable
fun AlertsMapScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    onBack: () -> Unit = {},
    onAlertClick: (alertId: String) -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  AlertsMapContent(state.lostPetsNearby.items, onBack, onAlertClick)
}

/** Stateless map of [alerts], one pin per alert; tapping a pin opens that alert. */
@Composable
fun AlertsMapContent(
    alerts: List<Alert>,
    onBack: () -> Unit = {},
    onAlertClick: (alertId: String) -> Unit = {},
) {
  Column(
      Modifier.fillMaxSize()
          .background(SpottedHomeBackground)
          .statusBarsPadding()
          .testTag(AlertsMapTestTags.SCREEN)
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
      IconButton(onClick = onBack, modifier = Modifier.testTag(AlertsMapTestTags.BACK)) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
      }
      Text("Lost pets near you", style = jakarta(20, 26f, FontWeight.SemiBold, SpottedInk))
    }
    Box(
        Modifier.fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .testTag(AlertsMapTestTags.MAP)
    ) {
      AlertsMap(alerts, onAlertClick, Modifier.fillMaxSize())
      if (alerts.isEmpty()) {
        Text(
            HomeScreenTexts.NO_LOST_PETS,
            style = jakarta(13, 17f, FontWeight.SemiBold, SpottedInk),
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.White.copy(alpha = 0.95f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag(AlertsMapTestTags.EMPTY),
        )
      }
    }
  }
}

@Composable
private fun AlertsMap(
    alerts: List<Alert>,
    onAlertClick: (alertId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val currentOnAlertClick by rememberUpdatedState(onAlertClick)
  val mapView = remember { createMapView(context) }
  DisposableEffect(mapView) {
    mapView.onResume()
    onDispose {
      mapView.onPause()
      mapView.onDetach()
    }
  }
  // Centre on the first alert once the alerts are loaded, not on every recomposition
  val center = alerts.firstOrNull()?.lastKnownLocation ?: DefaultMapCenter
  LaunchedEffect(center) {
    mapView.controller.setZoom(MAP_ZOOM)
    mapView.controller.setCenter(GeoPoint(center.latitude, center.longitude))
  }
  AndroidView(
      factory = { mapView },
      modifier = modifier,
      update = { map -> map.showAlerts(alerts) { alertId -> currentOnAlertClick(alertId) } },
  )
}

/** Replaces the pins with one per alert. */
private fun MapView.showAlerts(alerts: List<Alert>, onAlertClick: (alertId: String) -> Unit) {
  overlays.removeAll { it is Marker }
  alerts.forEach { alert ->
    overlays.add(
        Marker(this).apply {
          position = GeoPoint(alert.lastKnownLocation.latitude, alert.lastKnownLocation.longitude)
          icon = ContextCompat.getDrawable(context, R.drawable.ic_map_pin)
          setAnchor(Marker.ANCHOR_CENTER, PIN_TIP_Y)
          title = alert.petName
          infoWindow = null
          setOnMarkerClickListener { _, _ ->
            onAlertClick(alert.id)
            true
          }
        }
    )
  }
  invalidate()
}
