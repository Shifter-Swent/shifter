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
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.JoinCode
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.Team
import java.time.Instant
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
      content(
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
    composeTestRule.setContent { content(uiState = JoinEventUiState()) }

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
        Box(Modifier.width(NARROW_SCREEN)) { content(uiState = JoinEventUiState("ABC234")) }
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
      content(uiState = JoinEventUiState(joinCode = "ABC23"), onFindEvent = { findClicks++ })
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).assertIsNotEnabled()
    codeField().performImeAction()

    assertEquals(0, findClicks)
  }

  @Test
  fun keyboardDone_withACompleteCode_findsTheEvent() {
    var findClicks = 0
    composeTestRule.setContent {
      content(uiState = JoinEventUiState(joinCode = "ABC234"), onFindEvent = { findClicks++ })
    }

    codeField().performImeAction()

    assertEquals(1, findClicks)
  }

  @Test
  fun foundEvent_showsItsNameAndAppliesOnClick() {
    var applyClicks = 0
    composeTestRule.setContent {
      content(
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
      content(
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
      content(uiState = JoinEventUiState(event = EVENT, teams = TEAMS, selectedTeamId = "bar"))
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.teamOption("bar")).assertIsSelected()
  }

  @Test
  fun eventWithoutTeam_tellsItHasNoTeam() {
    composeTestRule.setContent { content(uiState = JoinEventUiState(event = EVENT)) }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.NO_TEAMS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun foundEvent_showsItsDetailsAndGoesBack() {
    var backClicks = 0
    composeTestRule.setContent {
      content(uiState = JoinEventUiState(event = EVENT), onBack = { backClicks++ })
    }

    composeTestRule.onNodeWithTag(EventDetailsTestTags.CATEGORY).assertTextEquals("Music")
    composeTestRule.onNodeWithTag(EventDetailsTestTags.VENUE).assertTextEquals("Lausanne")
    composeTestRule
        .onNodeWithTag(EventDetailsTestTags.DESCRIPTION)
        .assertTextEquals("A festival by the lake.")
    composeTestRule.onNodeWithTag(EventDetailsTestTags.DATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventDetailsTestTags.BACK_BUTTON).performClick()

    assertEquals(1, backClicks)
  }

  @Test
  fun applying_replacesTheButton() {
    composeTestRule.setContent {
      content(
          uiState = JoinEventUiState(event = EVENT, requestStatus = MembershipRequestStatus.PENDING)
      )
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLIED_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun decidedRequest_isShownAsSuch() {
    composeTestRule.setContent {
      content(
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
      content(uiState = JoinEventUiState(error = JoinEventError.UNKNOWN_CODE))
    }

    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.ERROR_MESSAGE)
        .assertTextEquals("No event uses this code")
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()
  }

  @Composable
  private fun content(
      uiState: JoinEventUiState,
      onJoinCodeChange: (String) -> Unit = {},
      onFindEvent: () -> Unit = {},
      onBack: () -> Unit = {},
      onTeamSelected: (String) -> Unit = {},
      onApply: () -> Unit = {},
  ) = JoinEventContent(uiState, onJoinCodeChange, onFindEvent, onBack, onTeamSelected, onApply)

  /** The code step keeping the code typed, normalized like the ViewModel does. */
  private fun showCodeStep() {
    composeTestRule.setContent {
      var joinCode by remember { mutableStateOf("") }
      content(
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
