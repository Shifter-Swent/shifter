// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.Team
import java.time.Instant
import org.junit.Assert.assertEquals
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
          uiState = JoinEventUiState(joinCode = "ABC123"),
          onJoinCodeChange = { typed = it },
          onFindEvent = { findClicks++ },
      )
    }

    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .performTextReplacement("XYZ")
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).performClick()

    assertEquals("XYZ", typed)
    assertEquals(1, findClicks)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()
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

  private companion object {
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
