package com.android.spotted.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the texts computed by the Home screen. */
class HomeScreenFormatTest {

  private val now = 1_000_000_000_000L
  private val minute = 60_000L

  @Test
  fun timeAgo_underAnHour_isInMinutes() {
    assertEquals("0 min ago", timeAgo(now, now))
    assertEquals("59 min ago", timeAgo(now - 59 * minute, now))
  }

  @Test
  fun timeAgo_underADay_isInHours() {
    assertEquals("1 h ago", timeAgo(now - 60 * minute, now))
    assertEquals("23 h ago", timeAgo(now - (24 * 60 - 1) * minute, now))
  }

  @Test
  fun timeAgo_betweenOneAndTwoDays_isYesterday() {
    assertEquals("yesterday", timeAgo(now - 24 * 60 * minute, now))
  }

  @Test
  fun timeAgo_twoDaysOrMore_isInDays() {
    assertEquals("2 days ago", timeAgo(now - 48 * 60 * minute, now))
  }

  @Test
  fun timeAgo_inTheFuture_countsAsNow() {
    assertEquals("0 min ago", timeAgo(now + 5 * minute, now))
  }

  @Test
  fun areaLabel_withHomeArea_isUppercaseWithTheRadius() {
    assertEquals("PLAINPALAIS · 3 KM AROUND YOU", areaLabel(" Plainpalais "))
  }

  @Test
  fun areaLabel_withoutHomeArea_isOnlyTheRadius() {
    assertEquals("3 KM AROUND YOU", areaLabel(""))
  }
}
