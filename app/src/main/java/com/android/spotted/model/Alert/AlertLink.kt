// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.alert

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Scheme and host of the links that open an alert in the app: `spotted://alert/<alertId>`. */
const val ALERT_LINK_SCHEME = "spotted"
const val ALERT_LINK_HOST = "alert"

/**
 * Letters, digits, `_` and `-`: the characters of a Firestore id, and nothing a route could use.
 */
private val ALERT_ID_PATTERN = Regex("[A-Za-z0-9_-]+")

/** The link that opens the alert [alertId] in the app, e.g. `spotted://alert/milo-4F2A`. */
fun alertLink(alertId: String): String = "$ALERT_LINK_SCHEME://$ALERT_LINK_HOST/$alertId"

/**
 * The alert id in [link], or null when [link] isn't a `spotted://alert/<alertId>` link. Links come
 * from other apps, so anything else (another host, an extra path segment, a strange id) is refused.
 */
fun alertIdFromLink(link: String?): String? {
  if (link == null) return null
  val uri =
      try {
        URI(link)
      } catch (_: URISyntaxException) {
        return null
      }
  if (!uri.scheme.equals(ALERT_LINK_SCHEME, ignoreCase = true)) return null
  if (!uri.host.equals(ALERT_LINK_HOST, ignoreCase = true)) return null
  if (uri.rawQuery != null || uri.rawFragment != null) return null
  val alertId = uri.path?.removePrefix("/") ?: return null
  return alertId.takeIf { it.matches(ALERT_ID_PATTERN) }
}

/**
 * The text shared for [alert]: "Milo is missing near Ouchy, Lausanne. Have you seen them?" then its
 * link. Without a [placeName], the place is the last known position's coordinates.
 */
fun alertShareText(alert: Alert, placeName: String?): String {
  val place =
      placeName?.takeIf { it.isNotBlank() }
          ?: "%.4f, %.4f"
              .format(
                  Locale.US,
                  alert.lastKnownLocation.latitude,
                  alert.lastKnownLocation.longitude,
              )
  return "${alert.petName} is missing near $place. Have you seen them? ${alertLink(alert.id)}"
}
