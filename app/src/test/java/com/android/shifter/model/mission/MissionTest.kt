// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Mission.conflictsWith], [MissionLocation] and the rule in
 * [FakeMissionRepository].
 */
class MissionTest {

  @Test
  fun isGeoTimed_followsTheLocation() {
    assertTrue(geoTimed(10, 11).isGeoTimed)
    assertFalse(geoTimed(10, 11).copy(location = null).isGeoTimed)
  }

  @Test
  fun overlappingSlotsOfTheSameTeamConflict() {
    assertConflict(true, geoTimed(10, 12), geoTimed(11, 13))
  }

  @Test
  fun slotsWithTheSameStartOrTheSameEndConflict() {
    assertConflict(true, geoTimed(10, 12), geoTimed(10, 11))
    assertConflict(true, geoTimed(10, 12), geoTimed(11, 12))
  }

  @Test
  fun aSlotInsideAnotherConflicts() {
    assertConflict(true, geoTimed(9, 14), geoTimed(10, 11))
  }

  @Test
  fun adjacentSlotsDoNotConflict() {
    assertConflict(false, geoTimed(10, 11), geoTimed(11, 12))
  }

  @Test
  fun separateSlotsDoNotConflict() {
    assertConflict(false, geoTimed(10, 11), geoTimed(12, 13))
  }

  @Test
  fun missionsOfDifferentTeamsDoNotConflict() {
    assertConflict(false, geoTimed(10, 12), geoTimed(10, 12).copy(teamId = "team-bar"))
  }

  @Test
  fun missionsOfNoTeamDoNotConflict() {
    assertConflict(
        false,
        geoTimed(10, 12).copy(teamId = null),
        geoTimed(10, 12).copy(teamId = null),
    )
  }

  @Test
  fun aMissionWithoutLocationDoesNotConflict() {
    assertConflict(false, geoTimed(10, 12), geoTimed(10, 12).copy(location = null))
  }

  @Test
  fun missionsOfDifferentEventsDoNotConflict() {
    assertConflict(false, geoTimed(10, 12), geoTimed(10, 12).copy(eventId = "event-2"))
  }

  @Test
  fun location_acceptsTheBoundsOfTheCoordinates() {
    MissionLocation("North pole", latitude = 90.0, longitude = 180.0)
    MissionLocation("South pole", latitude = -90.0, longitude = -180.0)
  }

  @Test
  fun location_rejectsCoordinatesOutOfRange() {
    assertThrows(IllegalArgumentException::class.java) { MissionLocation("", 90.1, 0.0) }
    assertThrows(IllegalArgumentException::class.java) { MissionLocation("", 0.0, -180.1) }
    assertThrows(IllegalArgumentException::class.java) { MissionLocation("", Double.NaN, 0.0) }
  }

  @Test
  fun fakeRepository_refusesAConflictingMissionAndKeepsTheOthers() = runTest {
    val repository = FakeMissionRepository()
    val first = repository.createMission(geoTimed(10, 12))

    val failure = runCatching { repository.createMission(geoTimed(11, 13)) }.exceptionOrNull()
    repository.createMission(geoTimed(12, 13))

    assertTrue(
        "expected MissionOverlapException, but was: $failure",
        failure is MissionOverlapException,
    )
    assertEquals(first, (failure as MissionOverlapException).conflictingMission)
    assertEquals(2, repository.created.size)
  }

  /** Checks [expected] both ways, since a conflict does not depend on which mission comes first. */
  private fun assertConflict(expected: Boolean, a: Mission, b: Mission) {
    assertEquals(expected, a.conflictsWith(b))
    assertEquals(expected, b.conflictsWith(a))
  }

  /** A GeoTimed mission of `team-logistics` from [startHour] to [endHour] on the same day. */
  private fun geoTimed(startHour: Int, endHour: Int) =
      Mission(
          eventId = "event-1",
          title = "Sort the food donations",
          description = "",
          teamId = "team-logistics",
          volunteersNeeded = 2,
          startAt = at(startHour),
          endAt = at(endHour),
          location = MissionLocation("Rue de Lausanne 1, Genève", 46.21, 6.14),
          createdAt = at(0),
      )

  private fun at(hour: Int): Instant =
      Instant.parse("2026-06-20T00:00:00Z").plusSeconds(hour * 3600L)
}
