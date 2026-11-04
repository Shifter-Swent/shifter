// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.ui.event.EventDetailsContent
import com.swent.shifter.ui.settings.SettingsScreen
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The circle of the back button starts 24 dp from the edge, whatever the screen drawing it. */
@RunWith(AndroidJUnit4::class)
class BackButtonPlacementTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun assertCircleStartsAt24Dp() {
    composeTestRule
        .onNodeWithTag(BackButtonTestTags.CIRCLE, useUnmergedTree = true)
        .assertLeftPositionInRootIsEqualTo(24.dp)
  }

  @Test
  fun shifterTopBar_placesTheCircleAt24Dp() {
    composeTestRule.setContent { ShifterTheme { ShifterTopBar(title = "Title", onBack = {}) } }

    assertCircleStartsAt24Dp()
  }

  @Test
  fun settingsScreen_placesTheCircleAt24Dp() {
    composeTestRule.setContent {
      ShifterTheme {
        SettingsScreen(mode = UserMode.VOLUNTEER, onBack = {}, onChangeMode = {}, onSignOut = {})
      }
    }

    assertCircleStartsAt24Dp()
  }

  @Test
  fun eventDetailsContent_placesTheCircleAt24Dp() {
    composeTestRule.setContent {
      ShifterTheme {
        EventDetailsContent(
            event = EVENT,
            teams = emptyList(),
            selectedTeamId = null,
            isLoading = false,
            requestStatus = null,
            error = null,
            onBack = {},
            onTeamSelected = {},
            onApply = {},
        )
      }
    }

    assertCircleStartsAt24Dp()
  }

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
            joinCode = "ABC234",
            createdAt = Instant.ofEpochSecond(500),
        )
  }
}
