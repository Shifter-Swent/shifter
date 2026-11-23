// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.schedule

import java.time.Instant

/**
 * A mission of the proposed schedule, with who is on it already and who the proposal adds.
 *
 * @property teamName the name of the mission's team, or null for a General mission, of no team.
 * @property volunteersNeeded how many people the mission needs.
 * @property assigned the names of the people already assigned, who stay assigned; null for a name
 *   that could not be read.
 * @property proposed the names of the people the proposal adds, saved only on accept.
 * @property missing how many spots stay empty once the proposal is accepted, to fill by hand.
 */
data class ProposedMission(
    val id: String,
    val title: String,
    val startAt: Instant,
    val endAt: Instant,
    val teamName: String?,
    val volunteersNeeded: Int,
    val assigned: List<String?> = emptyList(),
    val proposed: List<String?> = emptyList(),
    val missing: Int = 0,
)

/**
 * State of the proposed schedule of an event, for every day of it: the screen shows one day at a
 * time, picked from its own day strip.
 *
 * @property isLoading whether the proposal is being prepared.
 * @property loadFailed whether preparing it failed.
 * @property eventPeriod when the event starts and ends, so the screen can offer each of its days,
 *   or null until the event has been read.
 * @property missions every mission of the event, by start time.
 * @property isSaving whether the proposal is being saved.
 * @property saveFailed whether saving it failed; the proposal is kept to try again.
 */
data class AutoScheduleUiState(
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val eventPeriod: ClosedRange<Instant>? = null,
    val missions: List<ProposedMission> = emptyList(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
) {
  /** How many people the proposal assigns, over the whole event. */
  val newAssignments: Int
    get() = missions.sumOf { it.proposed.size }

  /** How many missions still miss people once the proposal is accepted. */
  val missionsToFinish: Int
    get() = missions.count { it.missing > 0 }

  /** Whether the proposal, once prepared, adds nobody: there is nothing to accept. */
  val nothingToPropose: Boolean
    get() = !isLoading && !loadFailed && newAssignments == 0
}

/** Every interaction of the proposed schedule, grouped so it takes a single parameter. */
data class AutoScheduleActions(
    val onAccept: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRetry: () -> Unit = {},
)
