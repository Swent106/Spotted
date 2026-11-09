// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.sighting

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta

private val compassDirections = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
private val directionGrid =
    listOf(
        listOf("NW", "N", "NE"),
        listOf("W", null, "E"),
        listOf("SW", "S", "SE"),
    )

/** Direction controls backed by the phone's rotation-vector sensor when one is available. */
@Composable
fun DirectionPicker(
    selectedHeadingDegrees: Double?,
    onDirectionSelected: (Double?) -> Unit,
) {
  val context = LocalContext.current
  val sensorManager =
      remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
  val sensor =
      remember(sensorManager) { sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
  val heading = remember { mutableStateOf<Double?>(null) }

  DisposableEffect(sensorManager, sensor) {
    if (sensorManager == null || sensor == null) {
      onDispose {}
    } else {
      val listener =
          object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
              val rotation = FloatArray(9)
              val orientation = FloatArray(3)
              SensorManager.getRotationMatrixFromVector(rotation, event.values)
              SensorManager.getOrientation(rotation, orientation)
              heading.value = normalizeHeadingDegrees(Math.toDegrees(orientation[0].toDouble()))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
          }
      sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
      onDispose { sensorManager.unregisterListener(listener) }
    }
  }

  DirectionPickerContent(
      hasCompass = sensor != null,
      compassHeadingDegrees = heading.value,
      selectedHeadingDegrees = selectedHeadingDegrees,
      onDirectionSelected = onDirectionSelected,
  )
}

/** Stateless direction controls, also used to test the compass UI without device sensors. */
@Composable
fun DirectionPickerContent(
    hasCompass: Boolean,
    compassHeadingDegrees: Double?,
    selectedHeadingDegrees: Double? = null,
    onDirectionSelected: (Double?) -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(28.dp),
      color = Color.White,
  ) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
          "DIRECTION",
          style = jakarta(14, 20f, 700, SpottedTheme.colors.inkSoft),
      )
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        directionGrid.forEach { row ->
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            row.forEach { label ->
              if (label == null) {
                Surface(
                    onClick = { onDirectionSelected(null) },
                    modifier = Modifier.weight(1f).height(60.dp).testTag("sighting_noIdea"),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Color(0xFFD8D8D8)),
                ) {
                  Box(
                      modifier = Modifier.fillMaxSize(),
                      contentAlignment = Alignment.Center,
                  ) {
                    Text(
                        "Don't know",
                        style = jakarta(14, 20f, 600, SpottedTheme.colors.inkSoft),
                    )
                  }
                }
              } else {
                val isSelected = selectedHeadingDegrees?.let(::directionLabelForHeading) == label
                Surface(
                    onClick = { onDirectionSelected(compassDirections.indexOf(label) * 45.0) },
                    modifier =
                        Modifier.weight(1f)
                            .height(60.dp)
                            .testTag("sighting_direction_${label.lowercase()}"),
                    shape = RoundedCornerShape(22.dp),
                    color = if (isSelected) SpottedTheme.colors.blue else Color(0xFFF3F4F6),
                ) {
                  Box(
                      modifier = Modifier.fillMaxSize(),
                      contentAlignment = Alignment.Center,
                  ) {
                    DirectionArrow(
                        degrees = compassDirections.indexOf(label) * 45f,
                        color =
                            if (isSelected) SpottedTheme.colors.onAccent
                            else SpottedTheme.colors.ink,
                        modifier = Modifier.size(24.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }

      if (hasCompass) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("Point your phone", style = jakarta(12, 16f, 600, SpottedTheme.colors.inkSoft))
          DirectionArrow(
              degrees = (compassHeadingDegrees ?: 0.0).toFloat(),
              color = SpottedTheme.colors.blue,
              modifier = Modifier.size(24.dp),
          )
        }
        Button(
            onClick = { compassHeadingDegrees?.let(onDirectionSelected) },
            enabled = compassHeadingDegrees != null,
            modifier = Modifier.fillMaxWidth().testTag("sighting_useCompass"),
            colors = ButtonDefaults.buttonColors(containerColor = SpottedTheme.colors.blue),
        ) {
          Text("Use this direction", style = jakarta(11, 14f, 700, SpottedTheme.colors.onAccent))
        }
      }
    }
  }
}

@Composable
private fun DirectionArrow(degrees: Float, color: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    rotate(degrees) {
      val arrow =
          Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.1f)
            lineTo(size.width * 0.18f, size.height * 0.42f)
            lineTo(size.width * 0.38f, size.height * 0.42f)
            lineTo(size.width * 0.38f, size.height * 0.9f)
            lineTo(size.width * 0.62f, size.height * 0.9f)
            lineTo(size.width * 0.62f, size.height * 0.42f)
            lineTo(size.width * 0.82f, size.height * 0.42f)
            close()
          }
      drawPath(arrow, color)
    }
  }
}

@Preview(showBackground = true, name = "Direction picker · NE selected")
@Composable
private fun DirectionPickerPreview() {
  SpottedTheme {
    Column(modifier = Modifier.padding(16.dp)) {
      DirectionPickerContent(
          hasCompass = true,
          compassHeadingDegrees = 45.0,
          selectedHeadingDegrees = 45.0,
          onDirectionSelected = {},
      )
    }
  }
}

/** Maps a clockwise heading to its nearest eight-point compass label. */
fun directionLabelForHeading(headingDegrees: Double): String {
  val normalized = normalizeHeadingDegrees(headingDegrees)
  val index = ((normalized + 22.5) / 45.0).toInt() % compassDirections.size
  return compassDirections[index]
}

private fun normalizeHeadingDegrees(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0
