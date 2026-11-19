// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.scantag

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanTagIntentTest {

  private val application = ApplicationProvider.getApplicationContext<Application>()

  @Test
  fun phoneAndEmailIntents_launchExpectedActionsAndAddresses() {
    val context = RecordingContext(application)

    launchPhoneIntent(context, "+41 79 123 45 67")
    launchEmailIntent(context, "owner@example.com")

    assertEquals(
        listOf(Intent.ACTION_DIAL, Intent.ACTION_SENDTO),
        context.startedIntents.map(Intent::getAction),
    )
    assertEquals(Uri.fromParts("tel", "+41 79 123 45 67", null), context.startedIntents[0].data)
    assertEquals(Uri.fromParts("mailto", "owner@example.com", null), context.startedIntents[1].data)
  }

  @Test
  fun phoneAndEmailIntents_doNotThrowWhenNoHandlerExists() {
    val context = RecordingContext(application) { throw ActivityNotFoundException() }

    launchPhoneIntent(context, "+41 79 123 45 67")
    launchEmailIntent(context, "owner@example.com")

    assertEquals(2, context.startedIntents.size)
  }

  @Test
  fun openNfcSettings_doesNotFallbackWhenNfcSettingsAreAvailable() {
    val context = RecordingContext(application)

    openNfcSettings(context)

    assertEquals(
        listOf(Settings.ACTION_NFC_SETTINGS),
        context.startedIntents.map(Intent::getAction),
    )
  }

  @Test
  fun openNfcSettings_fallsBackToWirelessSettingsWhenNfcSettingsAreUnavailable() {
    val context =
        RecordingContext(application) { intent ->
          if (intent.action == Settings.ACTION_NFC_SETTINGS) {
            throw ActivityNotFoundException()
          }
        }

    openNfcSettings(context)

    assertEquals(
        listOf(Settings.ACTION_NFC_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS),
        context.startedIntents.map(Intent::getAction),
    )
  }

  @Test
  fun openNfcSettings_doesNotThrowWhenNeitherSettingsHandlerExists() {
    val context = RecordingContext(application) { throw ActivityNotFoundException() }

    openNfcSettings(context)

    assertEquals(
        listOf(Settings.ACTION_NFC_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS),
        context.startedIntents.map(Intent::getAction),
    )
  }

  private class RecordingContext(
      base: Context,
      private val onStartActivity: (Intent) -> Unit = {},
  ) : ContextWrapper(base) {
    val startedIntents = mutableListOf<Intent>()

    override fun startActivity(intent: Intent) {
      startedIntents += intent
      onStartActivity(intent)
    }
  }
}
