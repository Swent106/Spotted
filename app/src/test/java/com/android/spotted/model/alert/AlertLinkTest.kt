// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.alert

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertLinkTest {

  private val base = "https://spotted-fd9bf.web.app"

  private val milo =
      Alert(
          id = "milo-4F2A",
          petId = "pet-1",
          ownerId = "owner-1",
          lastKnownLocation = Location(46.5070, 6.6270),
          lostAtMillis = 0L,
          petName = "Milo",
          petSpecies = Species.CAT,
      )

  /** [result] is a failure carrying an [IllegalArgumentException]; [case] names it on failure. */
  private fun assertInvalid(case: String?, result: Result<String>) {
    assertTrue("expected a failure for <$case>, got $result", result.isFailure)
    assertTrue(
        "expected an IllegalArgumentException for <$case>, got ${result.exceptionOrNull()}",
        result.exceptionOrNull() is IllegalArgumentException,
    )
  }

  private fun assertLinksRefused(vararg links: String?) = links.forEach {
    assertInvalid(it, parseLink(it))
  }

  // buildAlertLink

  @Test
  fun buildAlertLink_withValidId_returnsTheHttpsAlertLinkOnTheHostingDomain() {
    assertEquals(Result.success("$base/alert/milo-4F2A"), buildAlertLink("milo-4F2A"))
  }

  @Test
  fun buildAlertLink_withTwentyCharFirestoreId_returnsTheLink() {
    assertEquals(
        Result.success("$base/alert/aB3dE5gH7jK9mN1pQ3sT"),
        buildAlertLink("aB3dE5gH7jK9mN1pQ3sT"),
    )
  }

  @Test
  fun buildAlertLink_withEmptyId_fails() {
    assertInvalid("", buildAlertLink(""))
  }

  @Test
  fun buildAlertLink_withIdContainingCharactersOutsideTheIdAlphabet_failsForEach() {
    listOf("a/b", "../a", "a b", " a", "a?x=1", "a#b", "a%2Fb", "a.b", "é", "a\n").forEach {
      assertInvalid(it, buildAlertLink(it))
    }
  }

  // parseLink: accepted links

  @Test
  fun parseLink_withValidLink_returnsTheAlertId() {
    assertEquals(Result.success("milo-4F2A"), parseLink("$base/alert/milo-4F2A"))
  }

  @Test
  fun parseLink_ofLinkFromBuildAlertLink_returnsTheSameId() {
    listOf("a", "A_b-9", "aB3dE5gH7jK9mN1pQ3sT", "-", "_").forEach { id ->
      assertEquals(id, parseLink(buildAlertLink(id).getOrThrow()).getOrThrow())
    }
  }

  @Test
  fun parseLink_withUppercaseSchemeAndHost_returnsTheAlertId() {
    assertEquals(Result.success("a1"), parseLink("HTTPS://SPOTTED-FD9BF.WEB.APP/alert/a1"))
  }

  @Test
  fun parseLink_keepsTheCaseOfTheAlertId() {
    assertEquals(Result.success("MiLo"), parseLink("$base/alert/MiLo"))
  }

  // parseLink: refused links

  @Test
  fun parseLink_withNull_fails() {
    assertLinksRefused(null)
  }

  @Test
  fun parseLink_withEmptyOrBlankText_fails() {
    assertLinksRefused("", " ")
  }

  @Test
  fun parseLink_withTextThatIsNotAUri_fails() {
    assertLinksRefused("not a link", "$base/alert/a b", "$base/alert/a1 ", " $base/alert/a1")
  }

  @Test
  fun parseLink_withAnotherScheme_fails() {
    assertLinksRefused(
        "http://spotted-fd9bf.web.app/alert/a1",
        "spotted://alert/a1",
        "ftp://spotted-fd9bf.web.app/alert/a1",
    )
  }

  @Test
  fun parseLink_withAnotherHost_fails() {
    assertLinksRefused(
        "https://evil.com/alert/a1",
        "https://spotted-swent.web.app/alert/a1",
        "https://spotted-fd9bf.web.app.evil.com/alert/a1",
        "https://www.spotted-fd9bf.web.app/alert/a1",
        "https://spotted-fd9bf.firebaseapp.com/alert/a1",
    )
  }

  @Test
  fun parseLink_withoutHost_fails() {
    assertLinksRefused("https:///alert/a1", "/alert/a1", "alert/a1", "a1", "mailto:a1")
  }

  @Test
  fun parseLink_withPort_fails() {
    assertLinksRefused("$base:443/alert/a1", "$base:8080/alert/a1")
  }

  @Test
  fun parseLink_withUserInfo_fails() {
    assertLinksRefused("https://user@spotted-fd9bf.web.app/alert/a1")
  }

  @Test
  fun parseLink_withoutPath_fails() {
    assertLinksRefused(base, "$base/")
  }

  @Test
  fun parseLink_withAnotherPath_fails() {
    assertLinksRefused(
        "$base/pet/a1",
        "$base/t/a1",
        "$base/alerts/a1",
        "$base/Alert/a1",
        "$base/x/alert/a1",
        "$base/alerta1",
    )
  }

  @Test
  fun parseLink_withEmptyId_fails() {
    assertLinksRefused("$base/alert", "$base/alert/")
  }

  @Test
  fun parseLink_withExtraPathSegmentOrTrailingSlash_fails() {
    assertLinksRefused("$base/alert/a1/extra", "$base/alert/a1/", "$base/alert//a1")
  }

  @Test
  fun parseLink_withEncodedSlashOrDotsInId_fails() {
    assertLinksRefused("$base/alert/a1%2Fextra", "$base/alert/..", "$base/alert/%2E%2E")
  }

  @Test
  fun parseLink_withExtraCharactersInId_fails() {
    assertLinksRefused(
        "$base/alert/a1.",
        "$base/alert/a1!",
        "$base/alert/a1%20",
        "$base/alert/a1~",
        "$base/alert/é",
        "$base/alert/%C3%A9",
    )
  }

  @Test
  fun parseLink_withQuery_fails() {
    assertLinksRefused("$base/alert/a1?x=1", "$base/alert/a1?")
  }

  @Test
  fun parseLink_withFragment_fails() {
    assertLinksRefused("$base/alert/a1#top", "$base/alert/a1#")
  }

  // alertShareText

  @Test
  fun alertShareText_withPlaceName_namesThePlaceAndEndsWithTheLink() {
    assertEquals(
        Result.success(
            "Milo is missing near Ouchy, Lausanne. Have you seen them? $base/alert/milo-4F2A"
        ),
        alertShareText(milo, "Ouchy, Lausanne"),
    )
  }

  @Test
  fun alertShareText_withNullOrBlankPlaceName_usesTheCoordinatesWithFourDecimals() {
    val expected =
        Result.success(
            "Milo is missing near 46.5070, 6.6270. Have you seen them? $base/alert/milo-4F2A"
        )
    assertEquals(expected, alertShareText(milo, null))
    assertEquals(expected, alertShareText(milo, " "))
  }

  @Test
  fun alertShareText_withAlertIdThatCannotBeLinked_fails() {
    assertInvalid("empty id", alertShareText(milo.copy(id = ""), "Ouchy"))
    assertInvalid("id with slash", alertShareText(milo.copy(id = "a/b"), "Ouchy"))
  }
}
