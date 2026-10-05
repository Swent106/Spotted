package com.android.spotted.utils

import android.util.Base64
import org.json.JSONObject

object FakeJwtGenerator {
  private var _counter = 0
  private val counter
    get() = _counter++

  private fun base64UrlEncode(input: ByteArray): String {
    return Base64.encodeToString(input, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
  }

  fun createFakeGoogleIdToken(
      name: String = "Test User",
      email: String = "test@example.com"
  ): String {
    val header = JSONObject(mapOf("alg" to "none"))
    val payload =
        JSONObject(
            mapOf(
                "sub" to counter.toString(),
                "email" to email,
                "name" to name,
                "picture" to "http://example.com/avatar.png",
            ))

    val headerEncoded = base64UrlEncode(header.toString().toByteArray())
    val payloadEncoded = base64UrlEncode(payload.toString().toByteArray())

    // Signature can be anything, emulator doesn't check it
    val signature = "sig"

    return "$headerEncoded.$payloadEncoded.$signature"
  }
}
