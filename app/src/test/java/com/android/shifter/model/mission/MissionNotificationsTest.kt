// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.notification.MissionLine
import com.swent.shifter.model.notification.NotificationData
import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.notification.requireStorable
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the data of the mission notifications, shared by every writer. */
class MissionNotificationsTest {

  @Test
  fun missionAssignedData_listsTheMissionsEarliestFirst() {
    val sunday = mission("m1", "Pack up", day = 21)
    val saturday = mission("m2", "Set up", day = 20)

    val data = missionAssignedData(NotificationData.Value.ADDED, listOf(sunday, saturday))

    assertEquals(NotificationData.Value.ADDED, data[NotificationData.Key.CHANGE])
    assertEquals(
        listOf(lineOf(saturday), lineOf(sunday)),
        MissionLine.decodeAll(data.getValue(NotificationData.Key.MISSIONS)),
    )
  }

  @Test
  fun missionsByAddedPerson_groupsTheNewMissionsOfEachPerson() {
    val setUp = mission("m1", "Set up", day = 20, assigneeIds = listOf("ana"))
    val packUp = mission("m2", "Pack up", day = 21)
    val setUpAfter = setUp.copy(assigneeIds = listOf("ana", "ben"))
    val packUpAfter = packUp.copy(assigneeIds = listOf("ben", "eva"))

    val byPerson = missionsByAddedPerson(listOf(setUp to setUpAfter, packUp to packUpAfter))

    // Ana was already on Set up: she is not told again.
    assertEquals(
        mapOf("ben" to listOf(setUpAfter, packUpAfter), "eva" to listOf(packUpAfter)),
        byPerson,
    )
  }

  @Test
  fun missionsByRemovedPerson_groupsTheMissionsEachPersonLeft() {
    val setUp = mission("m1", "Set up", day = 20, assigneeIds = listOf("ana", "ben"))
    val setUpAfter = setUp.copy(assigneeIds = listOf("ben", "eva"))

    // Ben stays and Eva is added: only Ana is removed.
    assertEquals(
        mapOf("ana" to listOf(setUpAfter)),
        missionsByRemovedPerson(listOf(setUp to setUpAfter)),
    )
  }

  @Test
  fun missionChangedData_withANewStart_tellsTheTimeAndTheNewLine() {
    val later = bar.copy(startAt = bar.startAt.plusSeconds(3600))

    val data = missionChangedData(bar, later)!!

    assertEquals(NotificationData.Value.TIME, data[NotificationData.Key.WHAT])
    assertEquals(lineOf(later), MissionLine.decode(data.getValue(NotificationData.Key.MISSIONS)))
    assertNull(data[NotificationData.Key.ADDRESS])
    requireStorable(NotificationKind.MISSION_CHANGED, data)
  }

  @Test
  fun missionChangedData_withOnlyANewEnd_tellsTheTime() {
    val longer = bar.copy(endAt = bar.endAt.plusSeconds(3600))

    assertEquals(
        NotificationData.Value.TIME,
        missionChangedData(bar, longer)?.get(NotificationData.Key.WHAT),
    )
  }

  @Test
  fun missionChangedData_withANewAddress_tellsThePlaceAndTheAddress() {
    val moved = bar.copy(location = MissionLocation("Place du Marché 1", counter.position))

    val data = missionChangedData(bar, moved)!!

    assertEquals(NotificationData.Value.PLACE, data[NotificationData.Key.WHAT])
    assertEquals("Place du Marché 1", data[NotificationData.Key.ADDRESS])
  }

  @Test
  fun missionChangedData_withAPlaceWhereThereWasNone_tellsThePlace() {
    val placed = missionChangedData(bar.copy(location = null), bar)

    assertEquals(NotificationData.Value.PLACE, placed?.get(NotificationData.Key.WHAT))
    assertEquals(counter.address, placed?.get(NotificationData.Key.ADDRESS))
  }

  @Test
  fun missionChangedData_withANewTimeAndAddress_tellsBoth() {
    val changed =
        bar.copy(
            startAt = bar.startAt.plusSeconds(3600),
            location = MissionLocation("Place du Marché 1", counter.position),
        )

    val data = missionChangedData(bar, changed)!!

    assertEquals(NotificationData.Value.TIME_AND_PLACE, data[NotificationData.Key.WHAT])
    assertEquals("Place du Marché 1", data[NotificationData.Key.ADDRESS])
  }

  @Test
  fun missionChangedData_withOnlyAnotherTitleOrCount_tellsNothing() {
    assertNull(missionChangedData(bar, bar.copy(title = "Bar", volunteersNeeded = 5)))
  }

  @Test
  fun missionChangedData_withTheSameAddressAtAnotherPoint_tellsNothing() {
    val nudged = bar.copy(location = counter.copy(position = GeoPosition(46.6, 6.7)))

    assertNull(missionChangedData(bar, nudged))
  }

  @Test
  fun missionChangedData_withThePlaceRemoved_tellsNothing() {
    assertNull(missionChangedData(bar, bar.copy(location = null)))
  }

  private val counter = MissionLocation("Rue de Genève 12", GeoPosition(46.5, 6.6))

  /** A mission with a place, for the changes of place. */
  private val bar by lazy { mission("m1", "Serve at the bar", day = 20).copy(location = counter) }

  private fun lineOf(mission: Mission) =
      MissionLine(mission.id, mission.title, mission.startAt, mission.endAt)

  private fun mission(
      id: String,
      title: String,
      day: Int,
      assigneeIds: List<String> = emptyList(),
  ): Mission {
    val start = Instant.parse("2030-06-${day}T10:00:00Z")
    return Mission(
        id = id,
        eventId = "event-1",
        title = title,
        description = "",
        teamId = "team-logistics",
        volunteersNeeded = 3,
        startAt = start,
        endAt = start.plusSeconds(7200),
        assigneeIds = assigneeIds,
        createdAt = Instant.EPOCH,
    )
  }
}
