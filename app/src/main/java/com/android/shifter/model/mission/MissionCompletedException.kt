// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

/**
 * Thrown by [MissionRepository.updateMission] when the mission is already [completed][isCompleted],
 * closed by hand or past its end, so its details can no longer change.
 *
 * @property mission the stored mission that is completed.
 */
class MissionCompletedException(val mission: Mission) :
    Exception("The mission '${mission.id}' is completed and can no longer be changed")
