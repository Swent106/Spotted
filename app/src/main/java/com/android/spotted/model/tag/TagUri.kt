// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.tag

import java.net.URI
import java.net.URISyntaxException

/**
 * The URL written on a Spotted tag: `https://<HOST>/t/<tagId>`.
 *
 * The same URL serves three readers: the app reads the tag id from it, a phone without the app
 * opens it in the browser (#17), and App Links can later open the app directly. Nothing personal is
 * stored on the tag, only this random id.
 */
object TagUri {
  /**
   * Domain of the Spotted tag page. Must be the domain the team hosts the #17 page on (e.g. the
   * Firebase Hosting domain) and must not change once tags are written.
   */
  const val HOST = "spotted-swent.web.app"

  private const val SCHEME = "https"
  private const val PATH_PREFIX = "/t/"
  private val TAG_ID = Regex("[A-Za-z0-9_-]{8,64}")

  /** True for ids that may be written on a tag: 8–64 URL-safe characters. */
  fun isValidTagId(tagId: String): Boolean = TAG_ID.matches(tagId)

  /** The URL to write on the tag for [tagId]. */
  fun forTag(tagId: String): String {
    require(isValidTagId(tagId)) { "Invalid tag id: $tagId" }
    return "$SCHEME://$HOST$PATH_PREFIX$tagId"
  }

  /**
   * The tag id in [uri] if it is a Spotted tag URL, else `null`. The host is compared without case;
   * a trailing slash, query or fragment is ignored.
   */
  fun parseTagId(uri: String): String? {
    val parsed =
        try {
          URI(uri)
        } catch (_: URISyntaxException) {
          return null
        }
    if (!SCHEME.equals(parsed.scheme, ignoreCase = true)) return null
    if (!HOST.equals(parsed.host, ignoreCase = true)) return null
    val path = parsed.path ?: return null
    if (!path.startsWith(PATH_PREFIX)) return null
    val tagId = path.removePrefix(PATH_PREFIX).removeSuffix("/")
    return tagId.takeIf(::isValidTagId)
  }
}
