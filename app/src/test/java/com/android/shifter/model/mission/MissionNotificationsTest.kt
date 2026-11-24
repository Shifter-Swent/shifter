// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.swent.shifter.model.notification.MissionLine
import com.swent.shifter.model.notification.NotificationData
import java.time.Instant
import org.junit.Assert.assertEquals
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
