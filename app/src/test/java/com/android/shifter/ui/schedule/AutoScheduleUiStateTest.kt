// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.schedule

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoScheduleUiStateTest {

  private fun mission(
      id: String,
      proposed: List<String?> = emptyList(),
      missing: Int = 0,
  ) =
      ProposedMission(
          id = id,
          title = id,
          startAt = Instant.parse("2026-11-21T08:00:00Z"),
          endAt = Instant.parse("2026-11-21T11:00:00Z"),
          isGeneral = false,
          teamName = "Logistics",
          volunteersNeeded = 3,
          proposed = proposed,
          missing = missing,
      )

  private val proposal =
      AutoScheduleUiState(
          isLoading = false,
          missions =
              listOf(mission("a", proposed = listOf("Maya", null), missing = 1), mission("b")),
      )

  @Test
  fun defaultState_isLoadingWithNothingToSayYet() {
    val state = AutoScheduleUiState()

    assertTrue(state.isLoading)
    assertFalse(state.nothingToPropose)
    assertFalse(state.canAccept)
  }

  @Test
  fun totals_countEveryProposedPersonAndEveryShortMission() {
    // A name that could not be read is still someone assigned.
    assertEquals(2, proposal.newAssignments)
    assertEquals(1, proposal.missionsToFinish)
    assertFalse(proposal.nothingToPropose)
    assertTrue(proposal.canAccept)
  }

  @Test
  fun nobodyProposed_withMissionsShort_isShownButCannotBeAccepted() {
    val state = AutoScheduleUiState(isLoading = false, missions = listOf(mission("a", missing = 2)))

    assertFalse(state.nothingToPropose)
    assertEquals(1, state.missionsToFinish)
    assertFalse(state.canAccept)
  }

  @Test
  fun nobodyProposed_withEveryMissionFull_isNothingToPropose() {
    val state = AutoScheduleUiState(isLoading = false, missions = listOf(mission("a")))

    assertTrue(state.nothingToPropose)
    assertFalse(state.canAccept)
  }

  @Test
  fun loadFailed_isNotNothingToProposeAndCannotBeAccepted() {
    val state = proposal.copy(loadFailed = true)

    assertFalse(state.nothingToPropose)
    assertFalse(state.canAccept)
  }

  @Test
  fun canAccept_isOffWhileLoadingSavingOrSaved() {
    assertFalse(proposal.copy(isLoading = true).canAccept)
    assertFalse(proposal.copy(isSaving = true).canAccept)
    assertFalse(proposal.copy(saved = true).canAccept)
  }

  @Test
  fun canAccept_afterASaveError_onlyWhenTryingAgainCanWork() {
    assertTrue(proposal.copy(saveError = ScheduleSaveError.RETRY).canAccept)
    assertFalse(proposal.copy(saveError = ScheduleSaveError.OUTDATED).canAccept)
    assertFalse(proposal.copy(saveError = ScheduleSaveError.TOO_LARGE).canAccept)
  }
}
