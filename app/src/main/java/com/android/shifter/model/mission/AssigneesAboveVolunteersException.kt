// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

/**
 * Thrown by [MissionRepository.updateAssignees] when more people are given than the mission needs,
 * for instance because its volunteers needed were lowered since they were picked, so the screen can
 * tell the organizer how many the mission takes now.
 *
 * An [IllegalArgumentException], like the other refusals of [MissionRepository.updateAssignees].
 *
 * @property volunteersNeeded how many people the stored mission needs.
 */
class AssigneesAboveVolunteersException(val volunteersNeeded: Int) :
    IllegalArgumentException("The mission needs only $volunteersNeeded volunteers")
