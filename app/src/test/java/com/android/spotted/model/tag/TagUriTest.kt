// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TagUriTest {

  private val host = TagUri.HOST

  @Test
  fun forTagBuildsTheSpottedUrl() {
    assertEquals("https://$host/t/abcd_1234-XY", TagUri.forTag("abcd_1234-XY"))
  }

  @Test
  fun forTagRejectsInvalidIds() {
    assertThrows(IllegalArgumentException::class.java) { TagUri.forTag("short") }
    assertThrows(IllegalArgumentException::class.java) { TagUri.forTag("has space1") }
    assertThrows(IllegalArgumentException::class.java) { TagUri.forTag("a".repeat(65)) }
  }

  @Test
  fun isValidTagIdAcceptsOnlyUrlSafeIdsOf8To64Characters() {
    assertTrue(TagUri.isValidTagId("abcdefgh"))
    assertTrue(TagUri.isValidTagId("a".repeat(64)))
    assertFalse(TagUri.isValidTagId("abcdefg"))
    assertFalse(TagUri.isValidTagId("abcd/efgh"))
    assertFalse(TagUri.isValidTagId(""))
  }

  @Test
  fun parseTagIdReadsWhatForTagWrites() {
    assertEquals("pet-tag_0001", TagUri.parseTagId(TagUri.forTag("pet-tag_0001")))
  }

  @Test
  fun parseTagIdIgnoresHostCaseTrailingSlashQueryAndFragment() {
    assertEquals("abcd1234", TagUri.parseTagId("https://${host.uppercase()}/t/abcd1234"))
    assertEquals("abcd1234", TagUri.parseTagId("https://$host/t/abcd1234/"))
    assertEquals("abcd1234", TagUri.parseTagId("https://$host/t/abcd1234?ref=sticker#top"))
  }

  @Test
  fun parseTagIdRejectsUrlsThatAreNotSpottedTags() {
    assertNull(TagUri.parseTagId("http://$host/t/abcd1234"))
    assertNull(TagUri.parseTagId("https://example.com/t/abcd1234"))
    assertNull(TagUri.parseTagId("https://$host.evil.com/t/abcd1234"))
    assertNull(TagUri.parseTagId("https://$host/p/abcd1234"))
    assertNull(TagUri.parseTagId("https://$host/t/"))
    assertNull(TagUri.parseTagId("https://$host/t/abc"))
    assertNull(TagUri.parseTagId("https://$host/t/abcd1234/extra"))
  }

  @Test
  fun parseTagIdRejectsTextThatIsNotAUrl() {
    assertNull(TagUri.parseTagId("not a url"))
    assertNull(TagUri.parseTagId("https://exa mple.com/t/abcd1234"))
    assertNull(TagUri.parseTagId(""))
  }
}
