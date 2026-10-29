// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanningOrderTest {

  private fun mission(id: String, start: String, end: String, title: String = "Mission") =
      PlanningMission(
          id = id,
          title = title,
          startAt = Instant.parse("2025-06-14T$start:00Z"),
          endAt = Instant.parse("2025-06-14T$end:00Z"),
      )

  @Test
  fun chronological_sortsByStart() {
    val missions = listOf(mission("late", "10:00", "11:00"), mission("early", "08:00", "09:00"))

    assertEquals(listOf("early", "late"), missions.chronological().map { it.id })
  }

  @Test
  fun chronological_withSameStart_putsTheEarliestEndFirst() {
    val missions = listOf(mission("longer", "08:00", "10:00"), mission("shorter", "08:00", "09:00"))

    assertEquals(listOf("shorter", "longer"), missions.chronological().map { it.id })
  }

  @Test
  fun chronological_withSameHours_sortsByTitle() {
    val missions =
        listOf(
            mission("b", "08:00", "09:00", title = "Bar"),
            mission("a", "08:00", "09:00", title = "Accueil"),
        )

    assertEquals(listOf("a", "b"), missions.chronological().map { it.id })
  }
}
