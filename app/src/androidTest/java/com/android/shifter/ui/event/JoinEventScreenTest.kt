// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
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
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JoinEventScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun typesTheCodeThenFindsTheEvent() {
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
  fun showsTheEventNameAndAppliesOrChangesTheCodeOnClick() {
    var applyClicks = 0
    var changeCodeClicks = 0
    composeTestRule.setContent {
      content(
          uiState = JoinEventUiState(event = EVENT),
          onApply = { applyClicks++ },
          onChangeCode = { changeCodeClicks++ },
      )
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.EVENT_TITLE).assertTextEquals("Lakeside")
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).performClick()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.CHANGE_CODE_BUTTON).performClick()

    assertEquals(1, applyClicks)
    assertEquals(1, changeCodeClicks)
  }

  @Test
  fun replacesTheButtonOnceApplied() {
    composeTestRule.setContent {
      content(
          uiState = JoinEventUiState(event = EVENT, requestStatus = MembershipRequestStatus.PENDING)
      )
    }

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLIED_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun showsADecidedRequestAsSuch() {
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
  fun showsTheErrorMessage() {
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
      onApply: () -> Unit = {},
      onChangeCode: () -> Unit = {},
  ) = JoinEventContent(uiState, onJoinCodeChange, onFindEvent, onApply, onChangeCode)

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
  }
}
