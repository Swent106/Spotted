package com.android.spotted.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapZoomTest {

  @Test
  fun zoomFor_zoomsOutOneLevelEachTimeTheRadiusDoubles() {
    assertEquals(12.0f, zoomFor(1), 0.001f)
    assertEquals(zoomFor(1) - 1f, zoomFor(2), 0.001f)
    assertEquals(zoomFor(5) - 1f, zoomFor(10), 0.001f)
  }

  @Test
  fun zoomFor_largerRadius_isMoreZoomedOut() {
    val zooms = listOf(1, 3, 5, 10).map(::zoomFor)

    assertTrue(zooms.zipWithNext().all { (smaller, larger) -> larger < smaller })
  }
}
