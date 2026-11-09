// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

/**
 * Thrown by [MissionRepository.updateMission] when the mission would need fewer volunteers than the
 * people already assigned to it, so the edit screen can tell the organizer how many there are.
 *
 * @property assigneeCount how many people are assigned to the stored mission.
 */
class VolunteersBelowAssigneesException(val assigneeCount: Int) :
    Exception("The mission cannot need fewer volunteers than its $assigneeCount assignees")
