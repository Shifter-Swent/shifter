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
internal fun missionsByAddedPerson(changes: List<Pair<Mission, Mission>>) =
    missionsByPerson(changes) { stored, updated ->
      updated.assigneeIds - stored.assigneeIds.toSet()
    }

/** The missions each person was removed from, as [missionsByAddedPerson] lists those added to. */
internal fun missionsByRemovedPerson(changes: List<Pair<Mission, Mission>>) =
    missionsByPerson(changes) { stored, updated ->
      stored.assigneeIds - updated.assigneeIds.toSet()
    }

/** The updated missions of [changes] grouped by each of the [people] each change concerns. */
private fun missionsByPerson(
    changes: List<Pair<Mission, Mission>>,
    people: (stored: Mission, updated: Mission) -> List<String>,
): Map<String, List<Mission>> {
  val byPerson = linkedMapOf<String, MutableList<Mission>>()
  for ((stored, updated) in changes) {
    for (userId in people(stored, updated)) {
      byPerson.getOrPut(userId) { mutableListOf() } += updated
    }
  }
  return byPerson
}

/**
 * The data of the [com.swent.shifter.model.notification.NotificationKind.MISSION_CHANGED] telling
 * the assignees that [before] became [after], or null when nothing they need to know changed. Only
 * the time, [Mission.startAt] or [Mission.endAt], and the place are told: a new title, description
 * or number of volunteers changes nothing for the volunteer's day.
 *
 * The place is told by its address: a new one comes with it, and a place removed comes without any,
 * so a volunteer told a new time never keeps going to the old address. A new point at the same
 * address is not told: the volunteer reads the address, which has not changed, and the map and the
 * automatic check-in follow the new point by themselves.
 */
internal fun missionChangedData(before: Mission, after: Mission): Map<String, String>? {
  val timeChanged = before.startAt != after.startAt || before.endAt != after.endAt
  val address = after.location?.address
  val placeChanged = address != before.location?.address
  val what =
      when {
        timeChanged && placeChanged -> NotificationData.Value.TIME_AND_PLACE
        timeChanged -> NotificationData.Value.TIME
        placeChanged -> NotificationData.Value.PLACE
        else -> return null
      }
  val data =
      mapOf(
          NotificationData.Key.MISSIONS to after.toMissionLine().encode(),
          NotificationData.Key.WHAT to what,
      )
  return if (placeChanged && address != null) data + (NotificationData.Key.ADDRESS to address)
  else data
}
