// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.photo

/**
 * The file at [uri] can't be decoded as an image, so uploading it again fails the same way.
 *
 * Still an [IllegalArgumentException], so code catching that keeps working.
 */
class NotAnImageException(uri: Any) : IllegalArgumentException("$uri is not an image")
