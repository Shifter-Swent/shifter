// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.schedule

import java.time.Instant

/**
 * A mission of the proposed schedule, with who is on it already and who the proposal adds.
 *
 * @property isGeneral whether the mission is a General one, of no team.
 * @property teamName the name of the mission's team, or null when it could not be read, e.g. for a
 *   team that was deleted; unused for a General mission.
 * @property volunteersNeeded how many people the mission needs.
 * @property assigned the names of the people already assigned, who stay assigned; null for a name
 *   that could not be read, a name being never blank.
 * @property proposed the names of the people the proposal adds, saved only on accept; null as in
 *   [assigned].
 * @property missing how many spots stay empty once the proposal is accepted, to fill by hand: the
 *   mission's count in `ScheduleProposal.unfilled`, 0 when it has none. It is the one figure the
 *   screen does not derive, so it has no default: a mission short of people is shown so only when
 *   this says so, whatever [assigned] and [proposed] hold.
 */
data class ProposedMission(
    val id: String,
    val title: String,
    val startAt: Instant,
    val endAt: Instant,
    val isGeneral: Boolean,
    val teamName: String?,
    val volunteersNeeded: Int,
    val assigned: List<String?> = emptyList(),
    val proposed: List<String?> = emptyList(),
    val missing: Int,
)

/** Why saving the proposal failed, which decides what the organizer can do next. */
enum class ScheduleSaveError {
  /** The save did not get through, e.g. without a connection: accepting again may work. */
  RETRY,

  /**
   * The missions changed while the screen was open, e.g. someone was assigned or a mission
   * completed meanwhile: the same proposal would fail again, so it has to be prepared again.
   */
  OUTDATED,

  /** The event has more missions than can be saved at once: accepting again fails the same way. */
  TOO_LARGE,
}

/**
 * State of the proposed schedule of an event, for every day of it: the screen shows one day at a
 * time, picked from its own day strip.
 *
 * @property isLoading whether the proposal is being prepared. True at first, so the screen does not
 *   say there is nothing to propose before it has looked.
 * @property loadFailed whether preparing it failed. [missions] then keep the proposal of an earlier
 *   load, if any, which stays shown under the error.
 * @property eventPeriod when the event starts and ends, so the screen can offer each of its days,
 *   or null until the event has been read.
 * @property missions the missions `proposeSchedule` goes through, by start time: every mission of
 *   the event but the completed ones. A mission already started or with more people than it needs
 *   is listed too, with what [ProposedMission.missing] says.
 * @property isSaving whether the proposal is being saved.
 * @property saveError why saving it failed, or null when it did not; the proposal is kept. It is
 *   cleared when the proposal is prepared or accepted again.
 * @property saved whether the proposal was saved: the caller then leaves the screen or says so, and
 *   it cannot be accepted again.
 */
data class AutoScheduleUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val eventPeriod: ClosedRange<Instant>? = null,
    val missions: List<ProposedMission> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: ScheduleSaveError? = null,
    val saved: Boolean = false,
) {
  /** How many people the proposal assigns, over the whole event. */
  val newAssignments: Int
    get() = missions.sumOf { it.proposed.size }

  /** How many missions still miss people once the proposal is accepted. */
  val missionsToFinish: Int
    get() = missions.count { it.missing > 0 }

  /**
   * Whether the proposal, once prepared, adds nobody and leaves no mission to finish by hand: there
   * is nothing to show but that. A proposal that adds nobody to missions still short of people is
   * shown, so they can be finished by hand.
   */
  val nothingToPropose: Boolean
    get() = !isLoading && !loadFailed && newAssignments == 0 && missionsToFinish == 0

  /**
   * Whether Accept can be tapped: the proposal is prepared, adds someone, is neither being saved
   * nor saved, and saving it again could work.
   */
  val canAccept: Boolean
    get() =
        !isLoading &&
            !loadFailed &&
            !isSaving &&
            !saved &&
            newAssignments > 0 &&
            (saveError == null || saveError == ScheduleSaveError.RETRY)
}

/**
 * Every interaction of the proposed schedule, grouped so it takes a single parameter.
 *
 * @property onRetry prepares the proposal again, after a failed load or an outdated save.
 */
data class AutoScheduleActions(
    val onAccept: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRetry: () -> Unit = {},
)
