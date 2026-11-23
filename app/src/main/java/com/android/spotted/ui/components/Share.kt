// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import android.content.Context
import android.content.Intent

/** Opens the system share sheet with [text], e.g. an alert's link, to send it to another app. */
fun Context.shareText(text: String) {
  val send =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
      }
  startActivity(Intent.createChooser(send, null))
}
