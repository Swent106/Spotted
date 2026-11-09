// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NewIdsTest {

  @Test
  fun keepsOnlyTheIdsNotNotifiedYet_inOrder() {
    assertEquals(listOf("a", "b"), newIds(emptySet(), listOf("a", "b")))
    assertEquals(listOf("c", "a"), newIds(setOf("b"), listOf("c", "b", "a")))
  }

  @Test
  fun returnsNothing_whenThereIsNothingNew() {
    assertEquals(emptyList<String>(), newIds(setOf("a"), listOf("a")))
    assertEquals(emptyList<String>(), newIds(setOf("a"), emptyList()))
    assertEquals(emptyList<String>(), newIds(emptySet(), emptyList()))
  }

  @Test
  fun returnsARepeatedIdOnce() {
    assertEquals(listOf("a", "b"), newIds(emptySet(), listOf("a", "b", "a")))
  }
}
