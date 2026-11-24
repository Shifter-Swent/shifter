// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.shifter.R
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.JoinCode
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.shift.Shift
import com.swent.shifter.model.shift.slotOf
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.components.shifts.ShiftGridTestTags
import com.swent.shifter.ui.navigation.NavigationTestTags
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JoinEventScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun typingTheCode_findsTheEvent() {
    var typed = ""
    var findClicks = 0
    composeTestRule.setContent {
      Content(
          uiState = JoinEventUiState(joinCode = "ABC234"),
          onJoinCodeChange = { typed = it },
          onFindEvent = { findClicks++ },
      )
    }

    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .performTextReplacement("XYZ")
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON)
        .performScrollTo()
        .performClick()

    assertEquals("XYZ", typed)
    assertEquals(1, findClicks)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun codeStep_showsOneBoxPerCharacter() {
    composeTestRule.setContent { Content(uiState = JoinEventUiState()) }

    repeat(JoinCode.LENGTH) { codeBox(it).assertTextEquals("") }
    codeBox(JoinCode.LENGTH).assertDoesNotExist()
  }

  @Test
  fun typing_fillsTheNextBox() {
    showCodeStep()

    codeField().performTextInput("a")
    codeField().performTextInput("b")

    codeBox(0).assertTextEquals("A")
    codeBox(1).assertTextEquals("B")
    codeBox(2).assertTextEquals("")
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun deleting_emptiesTheLastBox() {
    showCodeStep()

    codeField().performTextInput("AB")
    codeField().performKeyInput { pressKey(Key.Backspace) }

    codeBox(0).assertTextEquals("A")
    codeBox(1).assertTextEquals("")
  }

  @Test
  fun pasting_fillsEveryBoxAndEnablesFind() {
    showCodeStep()

    codeField().performTextReplacement("abc-234")

    "ABC234".forEachIndexed { index, char -> codeBox(index).assertTextEquals(char.toString()) }
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).assertIsEnabled()
  }

  @Test
  fun typing_aSeventhCharacter_leavesTheBoxesUnchanged() {
    showCodeStep()

    codeField().performTextInput("ABC234")
    codeField().performTextInput("X")

    "ABC234".forEachIndexed { index, char -> codeBox(index).assertTextEquals(char.toString()) }
  }

  @Test
  fun typing_aCharacterNoCodeUses_showsItWithTheHintAndDisablesFind() {
    showCodeStep()

    codeField().performTextInput("abcde0")

    codeBox(5).assertTextEquals("0")
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.CODE_HINT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun codeStep_onANarrowScreenWithALargeFont_fitsEveryBox() {
    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)
      ) {
        Box(Modifier.width(NARROW_SCREEN)) { Content(uiState = JoinEventUiState("ABC234")) }
      }
    }

    repeat(JoinCode.LENGTH) {
      codeBox(it).assertIsDisplayed()
      assertTrue(codeBox(it).getUnclippedBoundsInRoot().right <= NARROW_SCREEN)
    }
  }

  @Test
  fun find_untilTheCodeIsComplete_staysDisabled() {
    var findClicks = 0
    composeTestRule.setContent {
      Content(uiState = JoinEventUiState(joinCode = "ABC23"), onFindEvent = { findClicks++ })
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).assertIsNotEnabled()
    codeField().performImeAction()

    assertEquals(0, findClicks)
  }

  @Test
  fun loading_disablesTheScanButton() {
    composeTestRule.setContent {
      Content(uiState = JoinEventUiState(joinCode = "ABC234", isLoading = true))
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.SCAN_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun keyboardDone_withACompleteCode_findsTheEvent() {
    var findClicks = 0
    composeTestRule.setContent {
      Content(uiState = JoinEventUiState(joinCode = "ABC234"), onFindEvent = { findClicks++ })
    }

    codeField().performImeAction()

    assertEquals(1, findClicks)
  }

  @Test
  fun foundEvent_showsItsNameAndAppliesOnClick() {
    var applyClicks = 0
    composeTestRule.setContent {
      Content(
          uiState = JoinEventUiState(event = EVENT, teams = TEAMS, selectedTeamId = "bar"),
          onApply = { applyClicks++ },
      )
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.EVENT_TITLE).assertTextEquals("Lakeside")
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).performClick()

    assertEquals(1, applyClicks)
  }

  @Test
  fun apply_untilATeamIsPicked_staysDisabled() {
    val picked = mutableListOf<String>()
    composeTestRule.setContent {
      Content(
          uiState = JoinEventUiState(event = EVENT, teams = TEAMS),
          onTeamSelected = { picked += it },
      )
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.teamOption("welcome")).performClick()

    assertEquals(listOf("welcome"), picked)
  }

  @Test
  fun pickingATeam_showsItAsSelected() {
    composeTestRule.setContent {
      Content(uiState = JoinEventUiState(event = EVENT, teams = TEAMS, selectedTeamId = "bar"))
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.teamOption("bar")).assertIsSelected()
  }

  @Test
  fun endedEvent_saysItHasEndedWithoutApply() {
    composeTestRule.setContent {
      Content(uiState = JoinEventUiState(event = EVENT, teams = TEAMS, hasEnded = true))
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.ENDED).assertIsDisplayed()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.teamOption("bar")).assertDoesNotExist()
    // Nothing below the ended text asks to send a request.
    composeTestRule.onNodeWithText(string(R.string.event_details_apply_hint)).assertDoesNotExist()
    composeTestRule
        .onNodeWithText(string(R.string.event_details_approval_notice))
        .assertDoesNotExist()
  }

  private fun string(id: Int) =
      InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

  @Test
  fun eventWithoutTeam_tellsItHasNoTeam() {
    composeTestRule.setContent { Content(uiState = JoinEventUiState(event = EVENT)) }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.NO_TEAMS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun codeStep_keepsItsTitleAndLeavesWithTheBackButton() {
    var leaves = 0
    composeTestRule.setContent { Content(uiState = JoinEventUiState(), onLeave = { leaves++ }) }

    // Only the back button is added: the title stays in the step, with no top bar.
    composeTestRule.onNodeWithText("Join an event").assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    assertEquals(1, leaves)
  }

  @Test
  fun foundEvent_showsItsDetailsAndGoesBack() {
    var backClicks = 0
    var leaves = 0
    composeTestRule.setContent {
      Content(
          uiState = JoinEventUiState(event = EVENT),
          onBack = { backClicks++ },
          onLeave = { leaves++ },
      )
    }

    composeTestRule.onNodeWithTag(EventDetailsTestTags.CATEGORY).assertTextEquals("Music")
    composeTestRule.onNodeWithTag(EventDetailsTestTags.VENUE).assertTextEquals("Lausanne")
    composeTestRule
        .onNodeWithTag(EventDetailsTestTags.DESCRIPTION)
        .assertTextEquals("A festival by the lake.")
    composeTestRule.onNodeWithTag(EventDetailsTestTags.DATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    // On the event, Back returns to the code rather than leaving the screen.
    assertEquals(1, backClicks)
    assertEquals(0, leaves)
  }

  @Test
  fun applying_replacesTheButton() {
    composeTestRule.setContent {
      Content(
          uiState = JoinEventUiState(event = EVENT, requestStatus = MembershipRequestStatus.PENDING)
      )
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLIED_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun endedEvent_withARequestSent_stillShowsTheRequest() {
    composeTestRule.setContent {
      Content(
          uiState =
              JoinEventUiState(
                  event = EVENT,
                  requestStatus = MembershipRequestStatus.PENDING,
                  hasEnded = true,
              )
      )
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLIED_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.ENDED).assertDoesNotExist()
  }

  @Test
  fun decidedRequest_isShownAsSuch() {
    composeTestRule.setContent {
      Content(
          uiState =
              JoinEventUiState(event = EVENT, requestStatus = MembershipRequestStatus.REJECTED)
      )
    }

    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.APPLIED_MESSAGE)
        .assertTextEquals("● Your request was declined")
  }

  @Test
  fun error_showsTheErrorMessage() {
    composeTestRule.setContent {
      Content(uiState = JoinEventUiState(error = JoinEventError.UNKNOWN_CODE))
    }

    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.ERROR_MESSAGE)
        .assertTextEquals("No event uses this code")
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()
  }

  // ── The shifts the volunteer picks (#600) ──

  @Test
  fun shifts_withNonePicked_showTheGridAndKeepApplyDisabled() {
    composeTestRule.setContent { Content(uiState = shiftsState()) }

    composeTestRule.onNodeWithTag(ShiftGridTestTags.GRID).performScrollTo().assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.SHIFTS_PICKED)
        .assertTextEquals(string(R.string.join_event_no_shift_picked))
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.PICK_A_SHIFT)
        .assertTextEquals(string(R.string.join_event_pick_a_shift))
  }

  @Test
  fun tappingAnOpenSlot_picksItsShift() {
    val picked = mutableListOf<String>()
    composeTestRule.setContent {
      Content(uiState = shiftsState(), onShiftPicked = { picked += it })
    }

    tapSlot(FRIDAY, 18)

    assertEquals(listOf(shiftAt(FRIDAY, 18).id), picked)
  }

  @Test
  fun tappingAClosedSlot_picksNothing() {
    val picked = mutableListOf<String>()
    composeTestRule.setContent {
      Content(uiState = shiftsState(), onShiftPicked = { picked += it })
    }

    tapSlot(FRIDAY, 10)

    assertEquals(emptyList<String>(), picked)
  }

  @Test
  fun pickedShifts_areSummedUpAndEnableApply() {
    composeTestRule.setContent {
      Content(uiState = shiftsState(picked = SHIFTS.map(Shift::id).toSet()))
    }

    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.SHIFTS_PICKED)
        .assertTextEquals("3 shifts picked: Fri 18–20, Sat 10–14")
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.PICK_A_SHIFT).assertDoesNotExist()
  }

  @Test
  fun eventWithoutShifts_showsNoGrid() {
    composeTestRule.setContent {
      Content(uiState = JoinEventUiState(event = EVENT, teams = TEAMS, selectedTeamId = "bar"))
    }

    composeTestRule.onNodeWithTag(ShiftGridTestTags.GRID).assertDoesNotExist()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.PICK_A_SHIFT).assertDoesNotExist()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertIsEnabled()
  }

  @Test
  fun requestSentBeforeTheShifts_showsNoGrid() {
    composeTestRule.setContent {
      Content(uiState = shiftsState().copy(requestStatus = MembershipRequestStatus.PENDING))
    }

    composeTestRule.onNodeWithTag(ShiftGridTestTags.GRID).assertDoesNotExist()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.SHIFTS_PICKED).assertDoesNotExist()
  }

  /**
   * Taps the slot of [day] at [hour]. The screen is scrolled to the whole grid first: scrolling to
   * a cell only scrolls the grid's row of days sideways, which leaves it out of view.
   */
  private fun tapSlot(day: LocalDate, hour: Int) {
    composeTestRule.onNodeWithTag(ShiftGridTestTags.GRID).performScrollTo()
    composeTestRule.onNodeWithTag(ShiftGridTestTags.slot(day, hour)).performClick()
  }

  /** The event of [SHIFTS], with a team picked and the shifts of [picked]. */
  private fun shiftsState(picked: Set<String> = emptySet()) =
      JoinEventUiState(
          event = SHIFTS_EVENT,
          teams = TEAMS,
          selectedTeamId = "bar",
          shiftPicking = ShiftPicking(SHIFTS, picked),
      )

  @Composable
  private fun Content(
      uiState: JoinEventUiState,
      onJoinCodeChange: (String) -> Unit = {},
      onFindEvent: () -> Unit = {},
      onBack: () -> Unit = {},
      onLeave: () -> Unit = {},
      onTeamSelected: (String) -> Unit = {},
      onApply: () -> Unit = {},
      onShiftPicked: (String) -> Unit = {},
  ) =
      JoinEventContent(
          uiState,
          onJoinCodeChange,
          onFindEvent,
          onBack,
          onLeave,
          onTeamSelected,
          onApply,
          onShiftPicked = onShiftPicked,
      )

  /** The code step keeping the code typed, normalized like the ViewModel does. */
  private fun showCodeStep() {
    composeTestRule.setContent {
      var joinCode by remember { mutableStateOf("") }
      Content(
          uiState = JoinEventUiState(joinCode = joinCode),
          onJoinCodeChange = { joinCode = JoinCode.normalize(it) },
      )
    }
  }

  private fun codeField() = composeTestRule.onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)

  private fun codeBox(index: Int) =
      composeTestRule.onNodeWithTag(JoinEventScreenTestTags.codeBox(index), useUnmergedTree = true)

  private companion object {
    val NARROW_SCREEN = 320.dp

    /** The phone's zone, which the screen reads the shifts in. */
    val ZONE: ZoneId = ZoneId.systemDefault()
    val FRIDAY: LocalDate = LocalDate.parse("2026-06-19")
    val SATURDAY: LocalDate = FRIDAY.plusDays(1)

    fun shiftAt(day: LocalDate, hour: Int): Shift {
      val slot = slotOf(day, hour, ZONE)
      return Shift(
          eventId = "event-1",
          startAt = slot.startAt,
          endAt = slot.endAt,
          createdAt = Instant.ofEpochSecond(500),
      )
    }

    /** Friday 18–20, then Saturday 10–12 and 12–14, back to back. */
    val SHIFTS = listOf(shiftAt(FRIDAY, 18), shiftAt(SATURDAY, 10), shiftAt(SATURDAY, 12))
    val EVENT =
        Event(
            id = "event-1",
            organizerId = "organizer-1",
            title = "Lakeside",
            description = "A festival by the lake.",
            type = EventType.MUSIC,
            startAt = Instant.ofEpochSecond(1_000),
            endAt = Instant.ofEpochSecond(2_000),
            location = EventLocation("Lausanne"),
            createdAt = Instant.ofEpochSecond(500),
        )
    val SHIFTS_EVENT = EVENT.copy(startAt = SHIFTS.first().startAt, endAt = SHIFTS.last().endAt)
    val TEAMS =
        listOf("welcome" to "Welcome", "bar" to "Bar").map { (id, name) ->
          Team(
              id = id,
              eventId = EVENT.id,
              name = name,
              description = "",
              icon = "star",
              createdAt = Instant.ofEpochSecond(500),
          )
        }
  }
}
