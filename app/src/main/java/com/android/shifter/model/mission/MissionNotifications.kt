// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.swent.shifter.model.notification.MissionLine
import com.swent.shifter.model.notification.NotificationData

/*
 * What the writers of a mission's notifications share, free of Firebase types so the fake
 * repository builds the same data as the Firestore one: addAssignees (#606), and the changes of
 * updateAssignees and updateMission (#617).
 */

/** This mission as one line of [NotificationData.Key.MISSIONS], with its title and times as now. */
private fun Mission.toMissionLine() = MissionLine(id, title, startAt, endAt)

/**
 * The data of a [com.swent.shifter.model.notification.NotificationKind.MISSION_ASSIGNED]: [change]
 * is [NotificationData.Value.ADDED] or [NotificationData.Value.REMOVED], and the [missions] are
 * listed earliest first, so the first line is the day the notification opens on.
 */
internal fun missionAssignedData(change: String, missions: List<Mission>): Map<String, String> =
    mapOf(
        NotificationData.Key.CHANGE to change,
        NotificationData.Key.MISSIONS to
            MissionLine.encodeAll(missions.sortedBy { it.startAt }.map { it.toMissionLine() }),
    )

/**
 * The missions each person was added to, from each stored mission to its updated version, in the
 * order of [changes], for one notification per person.
 */
internal fun missionsByAddedPerson(
    changes: List<Pair<Mission, Mission>>
): Map<String, List<Mission>> {
  val byPerson = linkedMapOf<String, MutableList<Mission>>()
  for ((stored, updated) in changes) {
    for (userId in updated.assigneeIds - stored.assigneeIds.toSet()) {
      byPerson.getOrPut(userId) { mutableListOf() } += updated
    }
  }
  return byPerson
}
