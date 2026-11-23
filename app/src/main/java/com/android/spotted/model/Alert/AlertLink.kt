// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.alert

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Host of the links that open an alert: `https://spotted-fd9bf.web.app/alert/<alertId>`. It is the
 * Firebase Hosting domain of the project, so a phone without the app can show a web page (#475).
 */
const val ALERT_LINK_HOST = "spotted-fd9bf.web.app"

private const val ALERT_LINK_SCHEME = "https"
private const val ALERT_LINK_PATH_PREFIX = "/alert/"

/**
 * Letters, digits, `_` and `-`: the characters of a Firestore id, and nothing a route could use.
 */
private val ALERT_ID_PATTERN = Regex("[A-Za-z0-9_-]+")

/**
 * The link that opens the alert [alertId], e.g. `https://spotted-fd9bf.web.app/alert/milo-4F2A`.
 * The link holds only the id, nothing personal. Fails with an [IllegalArgumentException] when
 * [alertId] is empty or has a character outside `[A-Za-z0-9_-]`.
 */
fun buildAlertLink(alertId: String): Result<String> =
    if (alertId.matches(ALERT_ID_PATTERN)) {
      Result.success("$ALERT_LINK_SCHEME://$ALERT_LINK_HOST$ALERT_LINK_PATH_PREFIX$alertId")
    } else {
      invalid("Invalid alert id: \"$alertId\"")
    }

/**
 * The alert id in [uri], read back from a [buildAlertLink] link. Links come from other apps, so
 * anything else fails with an [IllegalArgumentException], never throws: another scheme, host or
 * port, user info, another path, an extra segment, a query, a fragment, or an id that is empty or
 * has a character outside `[A-Za-z0-9_-]`. The scheme and host are compared without case.
 */
fun parseLink(uri: String?): Result<String> {
  if (uri == null) return invalid("No link")
  val parsed =
      try {
        URI(uri)
      } catch (_: URISyntaxException) {
        return invalid("Not a link: \"$uri\"")
      }
  if (
      !ALERT_LINK_SCHEME.equals(parsed.scheme, ignoreCase = true) ||
          !ALERT_LINK_HOST.equals(parsed.host, ignoreCase = true) ||
          parsed.port != -1 ||
          parsed.rawUserInfo != null
  ) {
    return invalid("Not a Spotted link: \"$uri\"")
  }
  if (parsed.rawQuery != null || parsed.rawFragment != null) {
    return invalid("Unexpected query or fragment: \"$uri\"")
  }
  // The raw path keeps an encoded `%2F` as is, so it can't sneak in an extra segment
  val path = parsed.rawPath.orEmpty()
  if (!path.startsWith(ALERT_LINK_PATH_PREFIX)) return invalid("Not an alert link: \"$uri\"")
  val alertId = path.removePrefix(ALERT_LINK_PATH_PREFIX)
  return if (alertId.matches(ALERT_ID_PATTERN)) Result.success(alertId)
  else invalid("Invalid alert id in \"$uri\"")
}

/**
 * The text shared for [alert]: "Milo is missing near Ouchy, Lausanne. Have you seen them?" then its
 * link. Without a [placeName], the place is the last known position's coordinates. Fails like
 * [buildAlertLink] when the alert's id can't be put in a link.
 */
fun alertShareText(alert: Alert, placeName: String?): Result<String> {
  val place =
      placeName?.takeIf { it.isNotBlank() }
          ?: "%.4f, %.4f"
              .format(
                  Locale.US,
                  alert.lastKnownLocation.latitude,
                  alert.lastKnownLocation.longitude,
              )
  return buildAlertLink(alert.id).map { link ->
    "${alert.petName} is missing near $place. Have you seen them? $link"
  }
}

private fun invalid(message: String): Result<Nothing> =
    Result.failure(IllegalArgumentException(message))
