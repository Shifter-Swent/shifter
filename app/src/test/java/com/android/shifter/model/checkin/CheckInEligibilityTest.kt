// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

import com.swent.shifter.model.checkin.CheckInEligibility.Eligible
import com.swent.shifter.model.checkin.CheckInEligibility.Ineligible
import com.swent.shifter.model.checkin.CheckInEligibility.Reason
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInEligibilityTest {

  private val start = Instant.parse("2026-11-07T14:00:00Z")
  private val end = Instant.parse("2026-11-07T15:30:00Z")
  private val opening = start - CheckInRules.OPENS_BEFORE_START
  private val place = GeoPosition(46.5191, 6.5668)
  private val mission =
      Mission(
          eventId = "event",
          title = "Bar",
          description = "Serve drinks",
          volunteersNeeded = 2,
          startAt = start,
          endAt = end,
          location = MissionLocation("Rolex Learning Center", place.latitude, place.longitude),
          createdAt = Instant.parse("2026-10-01T00:00:00Z"),
      )

  @Test
  fun checkInEligibility_inTheZoneDuringTheMission_isEligible() {
    assertEquals(Eligible, checkInEligibility(mission, start, place))
  }

  @Test
  fun checkInEligibility_withoutLocation_isNoLocation() {
    val result = checkInEligibility(mission.copy(location = null), start, place)

    assertEquals(Ineligible(Reason.NO_LOCATION), result)
  }

  @Test
  fun checkInEligibility_exactlyWhenTheWindowOpens_isEligible() {
    assertEquals(Eligible, checkInEligibility(mission, opening, place))
  }

  @Test
  fun checkInEligibility_justBeforeTheWindowOpens_isTooEarly() {
    val result = checkInEligibility(mission, opening - Duration.ofSeconds(1), place)

    assertEquals(Ineligible(Reason.TOO_EARLY), result)
  }

  @Test
  fun checkInEligibility_exactlyAtTheEnd_isEligible() {
    assertEquals(Eligible, checkInEligibility(mission, end, place))
  }

  @Test
  fun checkInEligibility_justAfterTheEnd_isTooLate() {
    val result = checkInEligibility(mission, end + Duration.ofSeconds(1), place)

    assertEquals(Ineligible(Reason.TOO_LATE), result)
  }

  @Test
  fun checkInEligibility_withoutPosition_isNoPosition() {
    assertEquals(Ineligible(Reason.NO_POSITION), checkInEligibility(mission, start, null))
  }

  @Test
  fun checkInEligibility_justInsideTheRadius_isEligible() {
    assertEquals(Eligible, checkInEligibility(mission, start, northOfPlace(199.0)))
  }

  @Test
  fun checkInEligibility_exactlyAtTheRadius_isEligible() {
    val edge = northOfPlace(CheckInRules.RADIUS_METERS)
    // Guards the test itself: the point must really be on the edge for the boundary to be tested.
    assertEquals(CheckInRules.RADIUS_METERS, edge.distanceTo(place), 1e-6)

    assertEquals(Eligible, checkInEligibility(mission, start, edge))
  }

  @Test
  fun checkInEligibility_justOutsideTheRadius_isTooFar() {
    val result = checkInEligibility(mission, start, northOfPlace(201.0))

    assertEquals(Ineligible(Reason.TOO_FAR), result)
  }

  @Test
  fun checkInEligibility_tooEarlyAndTooFar_isTooEarly() {
    val result = checkInEligibility(mission, opening - Duration.ofHours(1), northOfPlace(5_000.0))

    assertEquals(Ineligible(Reason.TOO_EARLY), result)
  }

  @Test
  fun checkInEligibility_tooLateWithoutPosition_isTooLate() {
    val result = checkInEligibility(mission, end + Duration.ofHours(1), null)

    assertEquals(Ineligible(Reason.TOO_LATE), result)
  }

  /** The point [meters] north of the mission's place, along its meridian. */
  private fun northOfPlace(meters: Double) =
      GeoPosition(
          place.latitude + Math.toDegrees(meters / GeoPosition.EARTH_RADIUS_METERS),
          place.longitude,
      )
}
