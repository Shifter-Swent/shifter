// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.schedule

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.components.days.DayStripTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoScheduleScreenTest {

  @get:Rule val compose = createComposeRule()

  private val zone = ZoneId.of("Europe/Zurich")
  private val friday = LocalDate.parse("2026-11-20")
  private val festival = at(0, "08:00")..at(2, "23:00")
  private val calls = mutableListOf<String>()

  /** [time] on the [day]-th day of a festival from Friday 20 to Sunday 22. */
  private fun at(day: Long, time: String): Instant =
      friday.plusDays(day).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

  private val stands =
      ProposedMission(
          id = "stands",
          title = "Set up the stands",
          startAt = at(1, "08:00"),
          endAt = at(1, "11:00"),
          isGeneral = false,
          teamName = "Logistics",
          volunteersNeeded = 4,
          proposed = listOf("Maya Bernard", "Sam Keller"),
          missing = 2,
      )
  private val bar =
      ProposedMission(
          id = "bar",
          title = "Serve at the bar",
          startAt = at(1, "12:00"),
          endAt = at(1, "16:00"),
          isGeneral = true,
          teamName = null,
          volunteersNeeded = 1,
          proposed = listOf("Alex Laurent"),
          missing = 0,
      )
  private val park =
      ProposedMission(
          id = "park",
          title = "Clean the park",
          startAt = at(2, "09:00"),
          endAt = at(2, "12:00"),
          isGeneral = false,
          teamName = "Logistics",
          volunteersNeeded = 2,
          assigned = listOf("Léa Petit"),
          proposed = listOf(null),
          missing = 0,
      )
  private val proposal =
      AutoScheduleUiState(
          isLoading = false,
          eventPeriod = festival,
          missions = listOf(stands, bar, park),
      )

  /** Shows [state] on Saturday 10:00, the second day of the festival. */
  private fun showSchedule(state: AutoScheduleUiState, now: Instant = at(1, "10:00")) {
    compose.setContent {
      ShifterTheme {
        AutoScheduleContent(
            state = state,
            actions =
                AutoScheduleActions(
                    onAccept = { calls += "accept" },
                    onCancel = { calls += "cancel" },
                    onRetry = { calls += "retry" },
                ),
            now = now,
            zone = zone,
        )
      }
    }
  }

  @Test
  fun summary_countsTheWholeEvent() {
    showSchedule(proposal)

    // Each total is read as one: the number, then what it counts.
    compose
        .onNodeWithTag(AutoScheduleTestTags.NEW_ASSIGNMENTS)
        .assert(hasText("4"))
        .assert(hasText("new assignments, all 3 days"))
    compose
        .onNodeWithTag(AutoScheduleTestTags.MISSIONS_TO_FINISH)
        .assert(hasText("1"))
        .assert(hasText("mission to finish by hand"))
  }

  @Test
  fun legend_isSkippedByScreenReaders() {
    showSchedule(proposal)

    // It only explains the colours: each chip of a mission already says its state.
    compose.onNodeWithText("Proposed").assertDoesNotExist()
    compose.onNodeWithText("Already assigned").assertDoesNotExist()
  }

  @Test
  fun opening_showsTheMissionsOfToday() {
    showSchedule(proposal)

    compose.onNodeWithTag(AutoScheduleTestTags.mission("stands")).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.mission("bar")).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.mission("park")).assertDoesNotExist()
  }

  @Test
  fun pickingADay_showsItsMissions() {
    showSchedule(proposal)

    compose.onNodeWithTag(DayStripTestTags.day(friday.plusDays(2))).performClick()

    compose.onNodeWithTag(AutoScheduleTestTags.mission("park")).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.mission("stands")).assertDoesNotExist()
  }

  @Test
  fun pickingADayWithoutMissions_saysSo() {
    showSchedule(proposal)

    compose.onNodeWithTag(DayStripTestTags.day(friday)).performClick()

    compose.onNodeWithTag(AutoScheduleTestTags.EMPTY_DAY).assertExists()
  }

  @Test
  fun missionCard_showsItsSlotTeamAndCount() {
    showSchedule(proposal)

    compose.onNodeWithText("08:00–11:00 · Logistics").assertExists()
    compose.onNodeWithText("12:00–16:00 · General").assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.count("stands")).assertTextEquals("2/4")
    compose.onNodeWithTag(AutoScheduleTestTags.count("bar")).assertTextEquals("1/1")
  }

  @Test
  fun missionCard_ofATeamThatCouldNotBeRead_saysSoRatherThanGeneral() {
    showSchedule(proposal.copy(missions = listOf(stands.copy(teamName = null))))

    compose.onNodeWithText("08:00–11:00 · Unknown team").assertExists()
  }

  @Test
  fun count_isReadWithWhatIsLeftToFillByHand() {
    showSchedule(proposal)

    compose
        .onNodeWithTag(AutoScheduleTestTags.count("stands"))
        .assertContentDescriptionEquals("2 of 4 people, 2 to fill by hand")
    compose
        .onNodeWithTag(AutoScheduleTestTags.count("bar"))
        .assertContentDescriptionEquals("1 of 1 person")
  }

  @Test
  fun missionCard_showsWhoIsAssignedThenWhoIsProposed() {
    showSchedule(proposal, now = at(2, "10:00"))

    val assigned =
        compose
            .onNodeWithTag(AutoScheduleTestTags.assignedChip("park", 0))
            .assert(hasText("Léa Petit"))
            .getUnclippedBoundsInRoot()
    // A name that could not be read is still a person on the mission.
    val proposed =
        compose
            .onNodeWithTag(AutoScheduleTestTags.proposedChip("park", 0))
            .assert(hasText("Unnamed volunteer"))
            .getUnclippedBoundsInRoot()
    assertTrue(
        "Those already assigned come first",
        assigned.top < proposed.top ||
            (assigned.top == proposed.top && assigned.left < proposed.left),
    )
    compose.onNodeWithTag(AutoScheduleTestTags.count("park")).assertTextEquals("2/2")
  }

  @Test
  fun accept_acceptsEveryDayAtOnce() {
    showSchedule(proposal)

    compose
        .onNodeWithTag(AutoScheduleTestTags.ACCEPT)
        .assertIsEnabled()
        .assert(hasText("Accept all 3 days"))
        .performClick()

    assertEquals(listOf("accept"), calls)
  }

  @Test
  fun cancel_isReported() {
    showSchedule(proposal)

    compose.onNodeWithTag(AutoScheduleTestTags.CANCEL).performClick()

    assertEquals(listOf("cancel"), calls)
  }

  @Test
  fun nothingToPropose_saysSoAndCannotBeAccepted() {
    val full = park.copy(volunteersNeeded = 1, proposed = emptyList())
    showSchedule(proposal.copy(missions = listOf(full)))

    compose.onNodeWithTag(AutoScheduleTestTags.NOTHING_TO_PROPOSE).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.SUMMARY).assertDoesNotExist()
    compose.onNodeWithTag(AutoScheduleTestTags.ACCEPT).assertIsNotEnabled()
    compose.onNodeWithTag(AutoScheduleTestTags.CANCEL).assertIsEnabled()
  }

  @Test
  fun nobodyProposed_withMissionsShort_showsThemToFinishByHand() {
    // Once a proposal is accepted, running it again proposes nobody, but the short missions stay.
    showSchedule(proposal.copy(missions = listOf(stands.copy(proposed = emptyList()))))

    compose.onNodeWithTag(AutoScheduleTestTags.NOTHING_TO_PROPOSE).assertDoesNotExist()
    compose.onNodeWithTag(AutoScheduleTestTags.mission("stands")).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.MISSIONS_TO_FINISH).assert(hasText("1"))
    compose.onNodeWithTag(AutoScheduleTestTags.ACCEPT).assertIsNotEnabled()
  }

  @Test
  fun saving_showsItsProgressAndCannotBeAcceptedNorCancelledAgain() {
    showSchedule(proposal.copy(isSaving = true))

    compose.onNodeWithTag(AutoScheduleTestTags.SAVING, useUnmergedTree = true).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.ACCEPT).assertIsNotEnabled()
    compose.onNodeWithTag(AutoScheduleTestTags.CANCEL).assertIsNotEnabled()
  }

  @Test
  fun saveFailed_keepsTheProposalToTryAgain() {
    showSchedule(proposal.copy(saveError = ScheduleSaveError.RETRY))

    compose
        .onNodeWithTag(AutoScheduleTestTags.SAVE_ERROR)
        .assertTextEquals("Could not save the schedule. Try again.")
    compose.onNodeWithTag(AutoScheduleTestTags.SAVE_RELOAD).assertDoesNotExist()
    compose.onNodeWithTag(AutoScheduleTestTags.mission("stands")).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.ACCEPT).assertIsEnabled()
  }

  @Test
  fun saveOutdated_offersToReloadRatherThanAcceptAgain() {
    showSchedule(proposal.copy(saveError = ScheduleSaveError.OUTDATED))

    compose
        .onNodeWithTag(AutoScheduleTestTags.SAVE_ERROR)
        .assertTextEquals(
            "The missions changed meanwhile. Reload the proposal to see what is left."
        )
    compose.onNodeWithTag(AutoScheduleTestTags.ACCEPT).assertIsNotEnabled()
    compose.onNodeWithTag(AutoScheduleTestTags.SAVE_RELOAD).performClick()

    assertEquals(listOf("retry"), calls)
  }

  @Test
  fun saveTooLarge_saysWhyAndCannotBeAccepted() {
    showSchedule(proposal.copy(saveError = ScheduleSaveError.TOO_LARGE))

    compose
        .onNodeWithTag(AutoScheduleTestTags.SAVE_ERROR)
        .assertTextEquals("This event has too many missions to save at once.")
    compose.onNodeWithTag(AutoScheduleTestTags.SAVE_RELOAD).assertDoesNotExist()
    compose.onNodeWithTag(AutoScheduleTestTags.ACCEPT).assertIsNotEnabled()
  }

  @Test
  fun loading_showsTheSpinnerAndCannotBeAccepted() {
    showSchedule(AutoScheduleUiState(eventPeriod = festival))

    compose.onNodeWithTag(AutoScheduleTestTags.LOADING).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.NOTHING_TO_PROPOSE).assertDoesNotExist()
    compose.onNodeWithTag(AutoScheduleTestTags.ACCEPT).assertIsNotEnabled()
  }

  @Test
  fun reloading_keepsTheProposalShownUnderItsProgress() {
    showSchedule(proposal.copy(isLoading = true))

    compose.onNodeWithTag(AutoScheduleTestTags.RELOADING).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.LOADING).assertDoesNotExist()
    compose.onNodeWithTag(AutoScheduleTestTags.mission("stands")).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.ACCEPT).assertIsNotEnabled()
  }

  @Test
  fun loadFailed_offersToRetry() {
    showSchedule(AutoScheduleUiState(isLoading = false, loadFailed = true, eventPeriod = festival))

    compose.onNodeWithTag(AutoScheduleTestTags.LOAD_ERROR).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.ACCEPT).assertIsNotEnabled()
    compose.onNodeWithTag(AutoScheduleTestTags.RETRY).performScrollTo().performClick()

    assertEquals(listOf("retry"), calls)
  }

  @Test
  fun reloadFailed_keepsTheProposalShownUnderTheError() {
    showSchedule(proposal.copy(loadFailed = true))

    compose.onNodeWithTag(AutoScheduleTestTags.LOAD_ERROR).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.mission("stands")).assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.ACCEPT).assertIsNotEnabled()
  }

  @Test
  fun eventOfOneDay_hasNoStripAndIsAcceptedAsAWhole() {
    val saturday = at(1, "08:00")..at(1, "23:00")
    showSchedule(proposal.copy(eventPeriod = saturday, missions = listOf(stands, bar)))

    compose.onNodeWithTag(DayStripTestTags.STRIP).assertDoesNotExist()
    compose.onNodeWithText("new assignments, its only day").assertExists()
    compose.onNodeWithTag(AutoScheduleTestTags.ACCEPT).assert(hasText("Accept"))
  }
}
