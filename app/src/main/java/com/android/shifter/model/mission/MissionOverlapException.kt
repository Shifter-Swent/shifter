// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

/**
 * Thrown by [MissionRepository.createMission] when the new mission
 * [conflictsWith][Mission.conflictsWith] an existing one, so the creation screen can tell the
 * organizer which mission is in the way.
 *
 * @property conflictingMission the existing mission whose slot the new one overlaps.
 */
class MissionOverlapException(val conflictingMission: Mission) :
    Exception("The mission overlaps the mission '${conflictingMission.id}' of the same team")
