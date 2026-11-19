// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.components.days.DayStripTestTags
import com.swent.shifter.ui.components.isIndeterminateSpinner
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ManagerOverviewContentTest {

  @get:Rule val compose = createComposeRule()

  private val start = Instant.parse("2027-06-20T09:00:00Z")
  // Five minutes in: the check-in of "Set up" is open, the one of "Serve drinks" is not.
  private val now = start.plus(Duration.ofMinutes(5))
  private val utc = ZoneId.of("UTC")

  private val alice = AttendanceVolunteer("u1", "Alice Martin", start.minus(Duration.ofMinutes(8)))
  private val thomas = AttendanceVolunteer("u2", "Thomas Petit", start.plus(Duration.ofMinutes(3)))
  private val emma = AttendanceVolunteer("u3", "Emma Dubois")

  private val setUp =
      mission("m1", "Set up equipment", "logistics", "Logistics", 0)
          .copy(assignees = listOf(alice, thomas, emma), attendanceKnown = true)
  private val drinks = mission("m2", "Serve drinks", "bar", "Bar", 4)
  private val loaded = AttendanceUiState(isLoading = false, missions = listOf(setUp, drinks))

  private fun mission(
      id: String,
      title: String,
      teamId: String?,
      teamName: String?,
      hour: Long,
  ) =
      AttendanceMission(
          id = id,
          title = title,
          teamId = teamId,
          teamName = teamName,
          volunteersNeeded = 3,
          startAt = start.plus(Duration.ofHours(hour)),
          endAt = start.plus(Duration.ofHours(hour + 2)),
          assignees = emptyList(),
      )

  /** [setUp] moved to run from [from] to [to], keeping who is there and who is not. */
  private fun setUpRunning(id: String, from: Duration, to: Duration) =
      setUp.copy(id = id, startAt = now.plus(from), endAt = now.plus(to))

  private fun show(
      state: AttendanceUiState,
      onRetry: () -> Unit = {},
      onCheckIn: (String, String) -> Unit = { _, _ -> },
      onViewOnMap: () -> Unit = {},
      now: Instant = this.now,
      onUndoCheckIn: () -> Unit = {},
      onUndoWindowOver: () -> Unit = {},
      onCheckInErrorShown: () -> Unit = {},
      onMarkCompleted: (String) -> Unit = {},
      onCompletionFailureShown: () -> Unit = {},
      onAddMission: (() -> Unit)? = null,
      onEditMission: ((String) -> Unit)? = null,
  ) {
    compose.setContent {
      ShifterTheme {
        ManagerOverviewContent(
            state = state,
            now = now,
            onRetry = onRetry,
            zone = utc,
            onCheckIn = onCheckIn,
            onViewOnMap = onViewOnMap,
            onUndoCheckIn = onUndoCheckIn,
            onUndoWindowOver = onUndoWindowOver,
            onCheckInErrorShown = onCheckInErrorShown,
            onMarkCompleted = onMarkCompleted,
            onCompletionFailureShown = onCompletionFailureShown,
            onAddMission = onAddMission,
            onEditMission = onEditMission,
        )
      }
    }
  }

  private fun pick(day: LocalDate) = compose.onNodeWithTag(DayStripTestTags.day(day)).performClick()

  /** Brings [tag] into the lazy list, so a card below the fold can be read and tapped. */
  private fun scrollTo(tag: String): SemanticsNodeInteraction {
    compose.onNodeWithTag(ManagerOverviewTestTags.SCREEN).performScrollToNode(hasTestTag(tag))
    return compose.onNodeWithTag(tag)
  }

  /**
   * [text] inside the section [tag] of a mission card: the now card at the top of the screen lists
   * the volunteers of the mission it speaks for, so a name alone no longer picks one node.
   */
  private fun inSection(tag: String, text: String) =
      compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)))

  @Test
  fun collapsedCard_showsTheMissionItsTeamScheduleAndLiveCount() {
    show(loaded)

    // The now card says whose team this is, so the manager is given no subtitle of their own.
    compose.onNodeWithTag(ManagerOverviewTestTags.SUBTITLE).assertDoesNotExist()
    compose.onNodeWithText("My Missions").assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertTextEquals("2 missions")
    compose.onNodeWithText("Set up equipment").assertIsDisplayed()
    // Times alone, since the mission runs on the day being read.
    compose.onNodeWithText("Logistics · 09:00–11:00").assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.counter("m1")).assertTextEquals("2/3")
    // The label heads its counter rather than sitting beside it.
    val label = compose.onAllNodesWithText("LIVE CHECK-IN")[0].getBoundsInRoot()
    val counter = compose.onNodeWithTag(ManagerOverviewTestTags.counter("m1")).getBoundsInRoot()
    assertTrue("the label must end above the counter", label.bottom <= counter.top)
    assertEquals(label.left, counter.left)
    // Nothing is told about who is there until the card is expanded.
    compose.onNodeWithTag(ManagerOverviewTestTags.present("m1")).assertDoesNotExist()
  }

  @Test
  fun missionAfterMidnightOrAcrossIt_showsEnoughOfItsDates() {
    val overnight =
        setUp.copy(
            id = "night",
            startAt = Instant.parse("2027-06-20T22:00:00Z"),
            endAt = Instant.parse("2027-06-21T02:00:00Z"),
        )
    // Still the evening of the 20th, which is where the manager looks for it.
    val afterMidnight =
        setUp.copy(
            id = "late",
            startAt = Instant.parse("2027-06-21T02:00:00Z"),
            endAt = Instant.parse("2027-06-21T03:00:00Z"),
        )
    val nextDay = setUpRunning("next", Duration.ofDays(1), Duration.ofDays(1).plusHours(2))
    show(loaded.copy(missions = listOf(overnight, afterMidnight, nextDay)))

    // Crossing midnight reads as the two days it spans rather than "22:00–02:00", and after
    // midnight names its date.
    compose.onNodeWithText("Logistics · 20 Jun 22:00 – 21 Jun 02:00").assertIsDisplayed()
    compose.onNodeWithText("Logistics · 21 Jun, 02:00–03:00").assertIsDisplayed()
    // On its own day, a mission needs no date: the strip says which day it is.
    pick(LocalDate.of(2027, 6, 21))
    compose.onNodeWithText("Logistics · 09:05–11:05").assertIsDisplayed()
  }

  @Test
  fun missionOfNoTeamOrOfAGoneTeam_namesThemGeneralAndUnknown() {
    val general = setUp.copy(id = "general", teamId = null, teamName = null)
    val gone = setUp.copy(id = "gone", teamId = "deleted", teamName = null)
    show(loaded.copy(missions = listOf(general, gone)))

    scrollTo(ManagerOverviewTestTags.mission("general"))
    compose.onNodeWithText("General · 09:00–11:00").assertIsDisplayed()
    // A team that no longer exists is not shown as General, which means no team at all.
    scrollTo(ManagerOverviewTestTags.mission("gone"))
    compose.onNodeWithText("Unknown team · 09:00–11:00").assertIsDisplayed()
    // It keeps a chip of its own, so its missions stay filterable; a mission of no team has none.
    scrollTo(ManagerOverviewTestTags.teamChip("deleted")).assertIsDisplayed()
  }

  @Test
  fun expandingAMission_showsWhoIsPresentWithTheirTimeAndWhoIsMissing() {
    show(loaded)

    scrollTo(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    val present = ManagerOverviewTestTags.present("m1")
    scrollTo(present).assertIsDisplayed()
    compose.onNodeWithText("PRESENT (2)").assertIsDisplayed()
    inSection(present, "Alice Martin").assertIsDisplayed()
    compose.onNodeWithText("08:52").assertIsDisplayed()
    inSection(present, "Thomas Petit").assertIsDisplayed()
    compose.onNodeWithText("09:03").assertIsDisplayed()
    val missing = ManagerOverviewTestTags.missing("m1")
    scrollTo(missing)
    compose.onNodeWithText("MISSING (1)").assertIsDisplayed()
    inSection(missing, "Emma Dubois").assertIsDisplayed()

    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.present("m1")).assertDoesNotExist()
  }

  @Test
  fun tappingAMissingVolunteer_checksThemInToThatMission() {
    var checkedIn: Pair<String, String>? = null
    show(loaded, onCheckIn = { missionId, userId -> checkedIn = missionId to userId })
    scrollTo(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    scrollTo(ManagerOverviewTestTags.checkIn("m1", "u3")).performClick()

    assertEquals("m1" to "u3", checkedIn)
  }

  @Test
  fun missingVolunteer_tellsScreenReadersWhatATapDoes() {
    show(loaded)
    scrollTo(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    scrollTo(ManagerOverviewTestTags.checkIn("m1", "u3"))
        .assert(
            SemanticsMatcher("clicks to check Emma in") {
              it.config.getOrNull(SemanticsActions.OnClick)?.label == "Check in Emma Dubois"
            }
        )
    // Said once for the section, not on every name.
    compose.onNodeWithText("Tap a name to check them in").assertIsDisplayed()
  }

  @Test
  fun presentVolunteer_cannotBeCheckedInAgain() {
    show(loaded)
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.checkIn("m1", "u1")).assertDoesNotExist()
  }

  @Test
  fun missionThatIsOver_hasNoTappableNameAndSaysWhy() {
    // A check-in would record the time of the tap, after the mission, rather than an arrival.
    val over = setUpRunning("over", Duration.ofHours(-3), Duration.ofHours(-1))
    show(loaded.copy(missions = listOf(over)))
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("over")).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.checkIn("over", "u3")).assertDoesNotExist()
    compose.onNodeWithText("Check-in is closed: the mission is completed.").assertIsDisplayed()
    compose.onNodeWithText("Tap a name to check them in").assertDoesNotExist()
  }

  @Test
  fun missionClosedByHand_hasNoTappableNameAndSaysWhy() {
    // Still running, but closed by its manager: its attendance is history.
    show(loaded.copy(missions = listOf(setUp.copy(completed = true))))
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.checkIn("m1", "u3")).assertDoesNotExist()
    compose.onNodeWithText("Check-in is closed: the mission is completed.").assertIsDisplayed()
  }

  @Test
  fun missingVolunteer_hasATouchTargetOfAtLeast48dp() {
    // One tap writes a check-in that cannot be changed: a hurried tap must not hit the next name.
    show(loaded)
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.checkIn("m1", "u3")).assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun pendingCheckIn_isMarkedAndCountedInTheBanner() {
    val pending = alice.copy(checkInPending = true, checkedInManually = true)
    show(loaded.copy(missions = listOf(setUp.copy(assignees = listOf(pending, thomas, emma)))))
    scrollTo(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    scrollTo(ManagerOverviewTestTags.pending("m1", "u1")).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.manual("m1", "u1")).assertTextEquals("Manual")
    compose.onNodeWithTag(ManagerOverviewTestTags.pending("m1", "u2")).assertDoesNotExist()
    compose
        .onNodeWithTag(ManagerOverviewTestTags.PENDING_BANNER)
        .assertTextEquals("1 check-in is waiting for the network. Keep Shifter open.")
  }

  @Test
  fun withEveryCheckInConfirmed_showsNoBanner() {
    show(loaded)

    compose.onNodeWithTag(ManagerOverviewTestTags.PENDING_BANNER).assertDoesNotExist()
  }

  @Test
  fun undoableCheckIn_offersToUndoIt() {
    var undone = false
    show(
        loaded.copy(undoableCheckIn = UndoableCheckIn("m1", "u3", "Emma Dubois")),
        onUndoCheckIn = { undone = true },
    )

    compose.onNodeWithText("Emma Dubois checked in").assertIsDisplayed()
    compose.onNodeWithText("Undo").performClick()

    assertTrue(undone)
  }

  @Test
  fun undoSnackbar_goingAwayUnused_endsTheUndoWindow() {
    var windowOver = false
    var undone = false
    show(
        loaded.copy(undoableCheckIn = UndoableCheckIn("m1", "u3", "Emma Dubois")),
        onUndoCheckIn = { undone = true },
        onUndoWindowOver = { windowOver = true },
    )
    compose.onNodeWithText("Emma Dubois checked in").assertIsDisplayed()

    // The check-in is only sent once "Undo" is gone, so it can never be tapped too late.
    compose.mainClock.advanceTimeBy(SHORT_SNACKBAR_MILLIS)
    compose.waitForIdle()

    assertTrue(windowOver)
    assertFalse(undone)
    compose.onNodeWithText("Undo").assertDoesNotExist()
  }

  @Test
  fun failedCheckIn_namesTheVolunteer() {
    var shown = false
    show(
        loaded.copy(
            checkInErrors =
                listOf(ManualCheckInFailure(ManualCheckInError.NOT_ALLOWED, "Emma Dubois"))
        ),
        onCheckInErrorShown = { shown = true },
    )

    compose
        .onNodeWithText("Emma Dubois can no longer be checked in to this mission.")
        .assertIsDisplayed()
    compose.mainClock.advanceTimeBy(SHORT_SNACKBAR_MILLIS)
    compose.waitForIdle()
    assertTrue(shown)
  }

  @Test
  fun checkInOutsideTheWindow_saysWhy() {
    show(
        loaded.copy(
            checkInErrors =
                listOf(ManualCheckInFailure(ManualCheckInError.OUTSIDE_WINDOW, "Emma Dubois"))
        )
    )

    compose
        .onNodeWithText(
            "Emma Dubois can't be checked in now: check-in is open from 15 minutes before the " +
                "mission until its end."
        )
        .assertIsDisplayed()
  }

  @Test
  fun sameFailureTwice_isShownTwice() {
    // The same volunteer refused twice: equal failures, which must not be taken for one.
    val refused = ManualCheckInFailure(ManualCheckInError.FAILED, "Emma Dubois")
    var failures by mutableStateOf(listOf(refused, refused))
    var shown = 0
    compose.setContent {
      ShifterTheme {
        ManagerOverviewContent(
            state = loaded.copy(checkInErrors = failures),
            now = now,
            onRetry = {},
            zone = utc,
            onCheckInErrorShown = {
              shown++
              failures = failures.drop(1)
            },
        )
      }
    }

    repeat(2) {
      compose.onNodeWithText("Could not check Emma Dubois in. Try again.").assertIsDisplayed()
      compose.mainClock.advanceTimeBy(SHORT_SNACKBAR_MILLIS)
      compose.waitForIdle()
    }

    assertEquals(2, shown)
    assertTrue(failures.isEmpty())
  }

  @Test
  fun secondCheckIn_keepsItsOwnUndo() {
    var undoable by mutableStateOf(UndoableCheckIn("m1", "u3", "Emma Dubois"))
    var windowsOver = 0
    var undone = 0
    compose.setContent {
      ShifterTheme {
        ManagerOverviewContent(
            state = loaded.copy(undoableCheckIn = undoable),
            now = now,
            onRetry = {},
            zone = utc,
            onUndoCheckIn = { undone++ },
            onUndoWindowOver = { windowsOver++ },
        )
      }
    }
    compose.onNodeWithText("Emma Dubois checked in").assertIsDisplayed()

    // The ViewModel has sent Emma's at once: her snackbar must go without sending Thomas's.
    undoable = UndoableCheckIn("m1", "u2", "Thomas Petit")
    compose.waitForIdle()

    compose.onNodeWithText("Emma Dubois checked in").assertDoesNotExist()
    compose.onNodeWithText("Thomas Petit checked in").assertIsDisplayed()
    assertEquals(0, windowsOver)
    compose.onNodeWithText("Undo").performClick()
    assertEquals(1, undone)
  }

  @Test
  fun missions_expandAndCollapseOnTheirOwn() {
    val b = setUp.copy(id = "b")
    val c = setUp.copy(id = "c")
    show(loaded.copy(missions = listOf(setUp, b, c)))
    val ids = listOf("m1", b.id, c.id)

    // Opening one leaves the ones already open as they are.
    ids.forEach { scrollTo(ManagerOverviewTestTags.missionHeader(it)).performClick() }

    ids.forEach { scrollTo(ManagerOverviewTestTags.present(it)).assertExists() }

    scrollTo(ManagerOverviewTestTags.missionHeader(b.id)).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.present(b.id)).assertDoesNotExist()
    scrollTo(ManagerOverviewTestTags.present("m1")).assertExists()
    scrollTo(ManagerOverviewTestTags.present(c.id)).assertExists()
  }

  @Test
  fun selectingATeam_listsOnlyItsMissions() {
    show(loaded)
    compose.onNodeWithTag(ManagerOverviewTestTags.ALL_TEAMS_CHIP).assertIsSelected()

    compose.onNodeWithTag(ManagerOverviewTestTags.teamChip("bar")).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.mission("m2")).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.mission("m1")).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertTextEquals("1 mission")
  }

  @Test
  fun filteringATeamOutAndBack_leavesTheCardsOpenedAsTheyWere() {
    show(loaded)
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.teamChip("bar")).performClick()
    compose.onNodeWithTag(ManagerOverviewTestTags.ALL_TEAMS_CHIP).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.present("m1")).assertExists()
  }

  @Test
  fun teamFilterAndOpenedCards_surviveAConfigurationChange() {
    val restorer = StateRestorationTester(compose)
    restorer.setContent {
      ShifterTheme {
        ManagerOverviewContent(
            state = loaded,
            now = now,
            onRetry = {},
            zone = utc,
        )
      }
    }
    compose.onNodeWithTag(ManagerOverviewTestTags.teamChip("logistics")).performClick()
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    restorer.emulateSavedInstanceStateRestore()

    // Rotating the phone neither resets the filter nor closes the card opened under it.
    compose.onNodeWithTag(ManagerOverviewTestTags.teamChip("logistics")).assertIsSelected()
    compose.onNodeWithTag(ManagerOverviewTestTags.present("m1")).assertExists()
  }

  @Test
  fun missionShortOfVolunteers_isReportedUnderstaffedOnceItHasStarted() {
    show(loaded)

    compose.onNodeWithTag(ManagerOverviewTestTags.status("m1")).assertTextEquals("Understaffed")
  }

  @Test
  fun missionNotStartedYet_awaitsTheRestWithoutCallingThemMissing() {
    // The check-in opened ten minutes ago, the mission starts in five: nobody is late.
    val soon = setUpRunning("soon", Duration.ofMinutes(5), Duration.ofHours(2))
    show(loaded.copy(missions = listOf(soon)))

    compose.onNodeWithTag(ManagerOverviewTestTags.status("soon")).assertTextEquals("Check-in open")
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("soon")).performClick()

    // Those already there still show, the others are awaited rather than reported late.
    val present = ManagerOverviewTestTags.present("soon")
    scrollTo(present)
    compose.onNodeWithText("PRESENT (2)").assertIsDisplayed()
    inSection(present, "Alice Martin").assertIsDisplayed()
    val expected = ManagerOverviewTestTags.expected("soon")
    scrollTo(expected)
    compose.onNodeWithText("EXPECTED (1)").assertIsDisplayed()
    inSection(expected, "Emma Dubois").assertIsDisplayed()
    compose.onNodeWithText("MISSING (1)").assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.missing("soon")).assertDoesNotExist()
  }

  @Test
  fun markCompleted_isOfferedOnARunningMissionOnly() {
    val over = setUpRunning("over", Duration.ofHours(-3), Duration.ofHours(-1))
    val closed = setUp.copy(id = "closed", completed = true)
    show(loaded.copy(missions = listOf(setUp, drinks, over, closed)))

    scrollTo(ManagerOverviewTestTags.markCompleted("m1")).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.markCompleted("m2")).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.markCompleted("over")).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.markCompleted("closed")).assertDoesNotExist()
  }

  @Test
  fun markCompleted_isNotOfferedToTheOrganizer() {
    show(loaded.copy(isOrganizer = true))

    compose.onNodeWithTag(ManagerOverviewTestTags.markCompleted("m1")).assertDoesNotExist()
  }

  @Test
  fun markCompleted_asksBeforeClosingTheMission() {
    val marked = mutableListOf<String>()
    show(loaded, onMarkCompleted = { marked += it })

    scrollTo(ManagerOverviewTestTags.markCompleted("m1")).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.MARK_COMPLETED_DIALOG).assertIsDisplayed()
    compose.onNodeWithText("This can't be undone.").assertIsDisplayed()
    assertTrue(marked.isEmpty())

    compose.onNodeWithTag(ManagerOverviewTestTags.MARK_COMPLETED_CONFIRM).performClick()

    assertEquals(listOf("m1"), marked)
    compose.onNodeWithTag(ManagerOverviewTestTags.MARK_COMPLETED_DIALOG).assertDoesNotExist()
  }

  @Test
  fun markCompleted_cancelled_closesNothing() {
    val marked = mutableListOf<String>()
    show(loaded, onMarkCompleted = { marked += it })

    scrollTo(ManagerOverviewTestTags.markCompleted("m1")).performClick()
    compose.onNodeWithTag(ManagerOverviewTestTags.MARK_COMPLETED_CANCEL).performClick()

    assertTrue(marked.isEmpty())
    compose.onNodeWithTag(ManagerOverviewTestTags.MARK_COMPLETED_DIALOG).assertDoesNotExist()
  }

  @Test
  fun markCompleted_whileRunning_isDisabled() {
    show(loaded.copy(markingCompletedIds = setOf("m1")))

    scrollTo(ManagerOverviewTestTags.markCompleted("m1")).assertIsNotEnabled()
  }

  @Test
  fun missionAwaitingConfirmation_isShownAsCompletedWithoutAnAction() {
    show(
        loaded.copy(
            missions = listOf(setUp.copy(completed = true)),
            markingCompletedIds = setOf("m1"),
        )
    )

    compose.onNodeWithTag(ManagerOverviewTestTags.status("m1")).assertTextEquals("Completed")
    compose.onNodeWithTag(ManagerOverviewTestTags.markCompleted("m1")).assertDoesNotExist()
    compose.onNodeWithText("The mission could not be marked as completed.").assertDoesNotExist()
  }

  @Test
  fun failedCompletion_showsASnackbarWhoseRetryClosesTheMissionAgain() {
    val marked = mutableListOf<String>()
    var shown = 0
    show(
        loaded.copy(completionFailedMissionId = "m1"),
        onMarkCompleted = { marked += it },
        onCompletionFailureShown = { shown++ },
    )

    compose.onNodeWithText("The mission could not be marked as completed.").assertIsDisplayed()
    compose.onNodeWithText("Retry").performClick()
    compose.waitForIdle()

    assertEquals(1, shown)
    assertEquals(listOf("m1"), marked)
  }

  @Test
  fun failedCompletion_withoutRetry_dismissesTheSnackbarAfterAWhile() {
    val marked = mutableListOf<String>()
    var shown = 0
    show(
        loaded.copy(completionFailedMissionId = "m1"),
        onMarkCompleted = { marked += it },
        onCompletionFailureShown = { shown++ },
    )

    compose.mainClock.advanceTimeBy(11_000)
    compose.waitForIdle()

    assertEquals(1, shown)
    assertTrue(marked.isEmpty())
  }

  @Test
  fun missionThatIsOver_stopsWarningButKeepsWhoWasThere() {
    // Two of the three needed came, and the check-in closed an hour ago with the mission.
    val over = setUpRunning("over", Duration.ofHours(-3), Duration.ofHours(-1))
    show(loaded.copy(missions = listOf(over)))

    // A warning nobody can act on any more is not shown.
    compose.onNodeWithTag(ManagerOverviewTestTags.status("over")).assertTextEquals("Completed")
    compose.onNodeWithTag(ManagerOverviewTestTags.counter("over")).assertTextEquals("2/3")
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("over")).performClick()

    // What happened is still readable, so the manager can go over the attendance afterwards.
    compose.onNodeWithText("PRESENT (2)").assertIsDisplayed()
    compose.onNodeWithText("Alice Martin").assertIsDisplayed()
    compose.onNodeWithText("MISSING (1)").assertIsDisplayed()
    compose.onNodeWithText("Emma Dubois").assertIsDisplayed()
  }

  @Test
  fun missionWhoseCheckInHasNotOpened_warnsAboutNothingAndCannotBeExpanded() {
    // Everyone is expected but the window has not opened: not a soul is late yet.
    show(
        loaded.copy(
            missions = listOf(drinks.copy(assignees = listOf(emma), attendanceKnown = true))
        )
    )

    compose
        .onNodeWithTag(ManagerOverviewTestTags.status("m2"))
        .assertTextEquals("Check-in not open yet")
    // Nothing to reveal, so the header does not offer to open on nothing.
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m2")).assertHasNoClickAction()
    compose.onNodeWithTag(ManagerOverviewTestTags.missing("m2")).assertDoesNotExist()
  }

  @Test
  fun missionWithUnknownAttendance_countsNobodyPresentAndNamesNobodyMissing() {
    val waiting = setUp.copy(attendanceKnown = false)
    val failed = setUp.copy(id = "m3", attendanceKnown = false, attendanceFailed = true)
    show(loaded.copy(missions = listOf(waiting, failed, setUp.copy(id = "m4"))))

    // Not "0/3": nobody is known to be present, which is not the same as nobody being there.
    compose.onNodeWithTag(ManagerOverviewTestTags.counter("m1")).assertTextEquals("—/3")
    // A dash says nothing out loud, so a screen reader is given the words instead.
    compose
        .onNodeWithTag(ManagerOverviewTestTags.counter("m1"))
        .assertContentDescriptionEquals("Attendance unknown, 3 needed")
    compose
        .onNodeWithTag(ManagerOverviewTestTags.status("m1"))
        .assertTextEquals("Waiting for attendance data")
    // Nothing is known, so neither card offers to show who is there.
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m1")).assertHasNoClickAction()
    scrollTo(ManagerOverviewTestTags.counter("m3")).assertTextEquals("—/3")
    compose
        .onNodeWithTag(ManagerOverviewTestTags.status("m3"))
        .assertTextEquals("Attendance unavailable")
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m3")).assertHasNoClickAction()
    compose.onNodeWithTag(ManagerOverviewTestTags.missing("m3")).assertDoesNotExist()
    // The mission that failed leaves the one still followed with its attendance.
    scrollTo(ManagerOverviewTestTags.counter("m4")).assertTextEquals("2/3")
    compose.onNodeWithTag(ManagerOverviewTestTags.status("m4")).assertTextEquals("Understaffed")
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m4")).assertHasClickAction()
  }

  @Test
  fun organizer_readsTheScreenUnderTheirOwnTitle() {
    show(loaded.copy(isOrganizer = true))

    compose.onNodeWithTag(ManagerOverviewTestTags.SUBTITLE).assertTextEquals("Organizer Overview")
  }

  @Test
  fun firstLoad_showsTheSpinnerAndCountsNoMissionYet() {
    show(AttendanceUiState())

    compose.onNodeWithTag(ManagerOverviewTestTags.LOADING).assertIsDisplayed()
    // "0 missions" before there is an answer would read as having none.
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertDoesNotExist()
  }

  @Test
  fun reload_withMissionsAlreadyShown_keepsThemAndTheirCount() {
    show(loaded.copy(isLoading = true))

    compose.onNodeWithTag(ManagerOverviewTestTags.LOADING).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.mission("m1")).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertTextEquals("2 missions")
  }

  @Test
  fun withoutAnyMission_showsTheEmptyState() {
    show(AttendanceUiState(isLoading = false))

    compose.onNodeWithTag(ManagerOverviewTestTags.EMPTY).assertTextEquals("No missions available")
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertTextEquals("0 missions")
  }

  @Test
  @Config(qualifiers = "w360dp-h720dp")
  fun pull_callsOnRetry() {
    var retries = 0
    show(AttendanceUiState(isLoading = false, missions = listOf(setUp)), onRetry = { retries++ })

    compose.onNodeWithTag(ManagerOverviewTestTags.SCREEN).performTouchInput { swipeDown() }

    compose.runOnIdle { assertEquals(1, retries) }
  }

  @Test
  fun reloading_withMissions_keepsThemWithoutASpinner() {
    // An automatic reload, e.g. on coming back: only a pull shows the spinner.
    show(AttendanceUiState(isLoading = true, missions = listOf(setUp)))

    compose.onNodeWithTag(ManagerOverviewTestTags.mission(setUp.id)).assertExists()
    compose.onAllNodes(isIndeterminateSpinner).assertCountEquals(0)
  }

  @Test
  fun loading_withoutMissions_showsOnlyTheListSpinner() {
    show(AttendanceUiState(isLoading = true))

    compose.onNodeWithTag(ManagerOverviewTestTags.LOADING).assertExists()
    compose.onAllNodes(isIndeterminateSpinner).assertCountEquals(1)
  }

  @Test
  @OptIn(ExperimentalTestApi::class)
  fun refreshAction_onAMissionAndTheMissionsTitle_callsOnRetry() {
    var retries = 0
    show(AttendanceUiState(isLoading = false, missions = listOf(setUp)), onRetry = { retries++ })

    // On the nodes themselves, as screen readers read them, not on what they merge.
    compose
        .onNodeWithTag(ManagerOverviewTestTags.missionHeader(setUp.id), useUnmergedTree = true)
        .performCustomAccessibilityActionWithLabel("Refresh")
    // The title of the list, the one node every reader has whether a mission is listed or not.
    compose
        .onNodeWithText("My Missions", useUnmergedTree = true)
        .performCustomAccessibilityActionWithLabel("Refresh")

    compose.runOnIdle { assertEquals(2, retries) }
  }

  @Test
  fun failedLoad_showsTheErrorAndRetries() {
    var retries = 0
    show(AttendanceUiState(isLoading = false, loadFailed = true), onRetry = { retries++ })

    compose.onNodeWithTag(ManagerOverviewTestTags.LOAD_ERROR).assertIsDisplayed()
    // The failure says why there is nothing, so it is neither called empty nor counted on top.
    compose.onNodeWithTag(ManagerOverviewTestTags.EMPTY).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.RETRY).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun failedReload_keepsTheMissionsWithTheError() {
    show(AttendanceUiState(isLoading = false, loadFailed = true, missions = listOf(setUp)))

    compose.onNodeWithTag(ManagerOverviewTestTags.LOAD_ERROR).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.mission(setUp.id)).assertExists()
  }

  @Test
  fun addMission_asAManager_showsTheButtonAndCallsBack() {
    var opened = 0
    show(loaded, onAddMission = { opened++ })

    compose
        .onNodeWithTag(ManagerOverviewTestTags.ADD_MISSION_BUTTON)
        .assertTextEquals("Add mission")
        .performClick()

    assertEquals(1, opened)
  }

  @Test
  fun addMission_asTheOrganizer_isNotShown() {
    // The organizer adds missions from their own Overview, General ones included.
    show(loaded.copy(isOrganizer = true), onAddMission = {})

    compose.onNodeWithTag(ManagerOverviewTestTags.ADD_MISSION_BUTTON).assertDoesNotExist()
  }

  @Test
  fun addMission_withoutACallback_isNotShown() {
    show(loaded)

    compose.onNodeWithTag(ManagerOverviewTestTags.ADD_MISSION_BUTTON).assertDoesNotExist()
  }

  @Test
  fun editMission_tapped_givesTheMissionIdWithoutExpandingTheCard() {
    val edited = mutableListOf<String>()
    show(loaded, onEditMission = { edited += it })

    compose
        .onNodeWithTag(ManagerOverviewTestTags.edit("m1"))
        .assertContentDescriptionEquals("Edit the mission")
        .performClick()

    assertEquals(listOf("m1"), edited)
    compose.onNodeWithTag(ManagerOverviewTestTags.present("m1")).assertDoesNotExist()
  }

  @Test
  fun editMission_asTheOrganizer_isNotOffered() {
    show(loaded.copy(isOrganizer = true), onEditMission = {})

    compose.onNodeWithTag(ManagerOverviewTestTags.edit("m1")).assertDoesNotExist()
  }

  @Test
  fun editMission_onACompletedMission_isNotOffered() {
    val over = setUpRunning("over", Duration.ofHours(-3), Duration.ofHours(-1))
    val closed = setUp.copy(id = "closed", completed = true)
    show(loaded.copy(missions = listOf(drinks, over, closed)), onEditMission = {})

    compose.onNodeWithTag(ManagerOverviewTestTags.edit("m2")).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.edit("over")).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.edit("closed")).assertDoesNotExist()
  }

  @Test
  fun editMission_whileBeingClosed_isNotOffered() {
    show(loaded.copy(markingCompletedIds = setOf("m1")), onEditMission = {})

    compose.onNodeWithTag(ManagerOverviewTestTags.edit("m1")).assertDoesNotExist()
    scrollTo(ManagerOverviewTestTags.edit("m2")).assertIsDisplayed()
  }

  @Test
  fun editMission_withoutCallback_isNotOffered() {
    show(loaded)

    compose.onNodeWithTag(ManagerOverviewTestTags.edit("m1")).assertDoesNotExist()
  }

  @Test
  fun editMission_hasATouchTargetOfAtLeast48dp() {
    show(loaded, onEditMission = {})

    compose.onNodeWithTag(ManagerOverviewTestTags.edit("m1")).assertHeightIsAtLeast(48.dp)
  }

  // The now card at the top of the screen

  @Test
  fun nowCard_sitsAboveTheTeamFiltersAndTheMissions() {
    show(loaded)

    val card = compose.onNodeWithTag(TeamNowCardTestTags.CARD).assertIsDisplayed()
    assertTrue(
        "the card comes before the team filters",
        card.getBoundsInRoot().top <
            compose.onNodeWithTag(ManagerOverviewTestTags.ALL_TEAMS_CHIP).getBoundsInRoot().top,
    )
    assertTrue(
        "and before the missions",
        card.getBoundsInRoot().top <
            compose.onNodeWithTag(ManagerOverviewTestTags.mission("m1")).getBoundsInRoot().top,
    )
  }

  @Test
  fun nowCard_readsTheAttendanceOfTheMissionsWatched() {
    // "Set up" started five minutes ago, inside its grace period, with Emma still on her way.
    show(loaded)

    compose.onNodeWithText("Starting now").assertIsDisplayed()
    compose.onNodeWithText("Set up equipment has started").assertIsDisplayed()
    compose.onNodeWithText("Short of 1 volunteer").assertIsDisplayed()
  }

  @Test
  fun nowCard_checkIn_checksThatVolunteerInToThatMission() {
    // Half an hour in, past its grace period: Emma is reported missing, on a row that checks her
    // in through the same callback as the mission list lower down.
    val late = setUpRunning("late", Duration.ofMinutes(-30), Duration.ofHours(2))
    var checkedIn: Pair<String, String>? = null
    show(
        loaded.copy(missions = listOf(late)),
        onCheckIn = { missionId, userId -> checkedIn = missionId to userId },
    )
    compose.onNodeWithText("1 volunteer missing").assertIsDisplayed()

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("late", "u3")).performClick()

    assertEquals("late" to "u3", checkedIn)
  }

  @Test
  fun nowCard_withTheLastMissingVolunteerCheckedIn_saysTheTeamIsAllOnDuty() {
    val late = setUpRunning("late", Duration.ofMinutes(-30), Duration.ofHours(2))
    // What the view model reports once her check-in is in: nobody is missing any more.
    var missions by mutableStateOf(listOf(late))
    compose.setContent {
      ShifterTheme {
        ManagerOverviewContent(
            state = loaded.copy(missions = missions),
            now = now,
            onRetry = {},
            zone = utc,
        )
      }
    }
    compose.onNodeWithText("1 volunteer missing").assertIsDisplayed()

    missions = listOf(late.copy(assignees = listOf(alice, thomas, emma.copy(checkedInAt = now))))

    compose.onNodeWithText("All on duty").assertIsDisplayed()
    compose.onNodeWithText("3 at work").assertIsDisplayed()
    compose.onNodeWithText("1 volunteer missing").assertDoesNotExist()
  }

  @Test
  fun nowCard_withTheNextMissionOnAnotherDay_leavesItToThatDayAndKeepsTheList() {
    // 20:00 on the first day, the next mission at 09:00 on the second: the event day runs to
    // 04:00, so tomorrow's mission is not tonight's and the card does not announce it.
    val evening = Instant.parse("2027-06-20T20:00:00Z")
    val tomorrow =
        setUp.copy(
            id = "tomorrow",
            startAt = evening.plus(Duration.ofHours(13)),
            endAt = evening.plus(Duration.ofHours(15)),
        )
    show(loaded.copy(missions = listOf(tomorrow)), now = evening)

    compose.onNodeWithText("Your team is done").assertIsDisplayed()
    compose.onNodeWithText("Set up equipment at 09:00").assertDoesNotExist()
    // Only the card reads the day: the list still holds every mission the manager watches.
    scrollTo(ManagerOverviewTestTags.mission("tomorrow")).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertTextEquals("1 mission")
  }

  @Test
  fun nowCard_isDrawnOnlyOnceThereIsAMissionToSpeakFor() {
    // No mission reads as a day over, which a first load, a failed one and an event with none at
    // all are not: the card waits rather than announcing one.
    var state by mutableStateOf(AttendanceUiState(isLoading = true))
    compose.setContent {
      ShifterTheme { ManagerOverviewContent(state = state, now = now, onRetry = {}, zone = utc) }
    }

    compose.onNodeWithTag(TeamNowCardTestTags.CARD).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.LOADING).assertIsDisplayed()

    state = AttendanceUiState(isLoading = false, loadFailed = true)

    compose.onNodeWithTag(TeamNowCardTestTags.CARD).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.LOAD_ERROR).assertIsDisplayed()

    state = AttendanceUiState(isLoading = false)

    compose.onNodeWithTag(TeamNowCardTestTags.CARD).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.EMPTY).assertIsDisplayed()

    state = loaded

    compose.onNodeWithTag(TeamNowCardTestTags.CARD).assertIsDisplayed()
  }

  @Test
  fun nowCard_isTheManagersOwn_andIsNotDrawnForTheOrganizer() {
    // The card speaks of "your team", which the organizer watching every team of the event has
    // none of; their missions and their attendance are untouched. Same state, the one flag apart.
    var state by mutableStateOf(loaded.copy(isOrganizer = true))
    compose.setContent {
      ShifterTheme { ManagerOverviewContent(state = state, now = now, onRetry = {}, zone = utc) }
    }

    compose.onNodeWithTag(TeamNowCardTestTags.CARD).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.mission("m1")).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertTextEquals("2 missions")

    state = loaded

    compose.onNodeWithTag(TeamNowCardTestTags.CARD).assertIsDisplayed()
  }

  private companion object {
    /** Longer than a short snackbar without accessibility settings, which is 4 s. */
    const val SHORT_SNACKBAR_MILLIS = 5_000L
  }

  // The event runs from the 19th to the night of the 21st; today is the 20th.
  private val eventPeriod =
      Instant.parse("2027-06-19T08:00:00Z")..Instant.parse("2027-06-22T02:00:00Z")
  private val today = LocalDate.of(2027, 6, 20)
  private val tomorrow = today.plusDays(1)
  private val barTomorrow =
      drinks.copy(id = "m3", title = "Close the bar", startAt = start.plus(Duration.ofDays(1)))
  private val threeDays =
      loaded.copy(missions = listOf(setUp, drinks, barTomorrow), eventPeriod = eventPeriod)

  @Test
  fun eventOfSeveralDays_opensOnTodayWithItsMissionsOnly() {
    show(threeDays)

    compose.onNodeWithTag(DayStripTestTags.day(today)).assertIsSelected()
    compose.onNodeWithText("Set up equipment").assertIsDisplayed()
    scrollTo(ManagerOverviewTestTags.mission("m2")).assertIsDisplayed()
    compose.onNodeWithText("Close the bar").assertDoesNotExist()
    // The missions of the day shown, not of the event.
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertTextEquals("2 missions")
    compose.onNodeWithTag(ManagerOverviewTestTags.BACK_TO_TODAY).assertDoesNotExist()
  }

  @Test
  fun pickingADay_showsItsMissions_withTheTeamFilterOnAndOff() {
    show(threeDays)

    pick(tomorrow)
    compose.onNodeWithText("Close the bar").assertIsDisplayed()
    compose.onNodeWithText("Set up equipment").assertDoesNotExist()

    compose.onNodeWithTag(ManagerOverviewTestTags.teamChip("bar")).performClick()
    compose.onNodeWithText("Close the bar").assertIsDisplayed()
    // The day stays picked when the team changes, and the other way round.
    compose.onNodeWithTag(DayStripTestTags.day(tomorrow)).assertIsSelected()
    pick(today)
    compose.onNodeWithText("Serve drinks").assertIsDisplayed()
    compose.onNodeWithText("Set up equipment").assertDoesNotExist()
    compose.onNodeWithText("Close the bar").assertDoesNotExist()
  }

  @Test
  fun dayTheFilterLeavesEmpty_saysSo_andKeepsEveryDay() {
    show(threeDays)

    compose.onNodeWithTag(ManagerOverviewTestTags.teamChip("logistics")).performClick()
    pick(tomorrow)

    compose.onNodeWithTag(ManagerOverviewTestTags.EMPTY_DAY).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.EMPTY).assertDoesNotExist()
    compose.onNodeWithText("Close the bar").assertDoesNotExist()
    // The strip is the event's, whatever the team: the 19th, without missions, is still there.
    compose.onNodeWithTag(DayStripTestTags.day(today.minusDays(1))).assertExists()
  }

  @Test
  fun backToToday_takesTheCountsPlaceOnAnotherDay_andShowsTodayAgain() {
    show(threeDays)
    pick(today.minusDays(1))

    compose.onNodeWithTag(ManagerOverviewTestTags.EMPTY_DAY).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.BACK_TO_TODAY).performClick()

    compose.onNodeWithTag(DayStripTestTags.day(today)).assertIsSelected()
    compose.onNodeWithText("Set up equipment").assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.BACK_TO_TODAY).assertDoesNotExist()
  }

  @Test
  fun eventOfOneDay_showsItsMissionsWithoutTheStrip() {
    show(loaded.copy(eventPeriod = start..start.plus(Duration.ofHours(10))))

    compose.onNodeWithTag(DayStripTestTags.STRIP).assertDoesNotExist()
    compose.onNodeWithText("Set up equipment").assertIsDisplayed()
  }

  @Test
  fun eventOfOneDay_onAnotherDay_datesItsMissions() {
    // Two days from now, with no strip to say the mission is not today.
    val later = setUpRunning("later", Duration.ofDays(2), Duration.ofDays(2).plusHours(2))
    show(
        loaded.copy(
            missions = listOf(later),
            eventPeriod = later.startAt..later.endAt,
        )
    )

    compose.onNodeWithTag(DayStripTestTags.STRIP).assertDoesNotExist()
    compose.onNodeWithText("Logistics · 22 Jun, 09:05–11:05").assertIsDisplayed()
  }

  @Test
  fun eventNotStartedYet_opensOnItsFirstDay() {
    val later = barTomorrow.copy(startAt = start.plus(Duration.ofDays(3)))
    show(
        loaded.copy(
            missions = listOf(later),
            eventPeriod = later.startAt..later.startAt.plus(Duration.ofDays(2)),
        )
    )

    compose.onNodeWithTag(DayStripTestTags.day(today.plusDays(3))).assertIsSelected()
    compose.onNodeWithText("Close the bar").assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.BACK_TO_TODAY).assertDoesNotExist()
  }
}
