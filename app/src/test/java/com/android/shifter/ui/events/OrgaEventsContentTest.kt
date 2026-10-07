// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.events.organizer.OrgaEventsContent
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrgaEventsContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val managed = mutableListOf<String>()
  private val selectedTabs = mutableListOf<EventTab>()
  private var createClicks = 0

  private fun setContent(state: MyEventsUiState) {
    composeTestRule.setContent {
      ShifterTheme {
        OrgaEventsContent(
            state = state,
            avatarInitial = "J",
            onTabSelected = { selectedTabs += it },
            onManageEvent = { managed += it },
            onCreateEventClick = { createClicks++ },
            onAvatarClick = {},
        )
      }
    }
  }

  @Test
  fun showsTheOrganizerViewSubtitleAndItsEvents() {
    val upcoming = SampleMyEvents.organizer.filter { it.badge != EventBadge.ENDED }
    setContent(MyEventsUiState(upcoming = upcoming))

    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MyEventsTestTags.SUBTITLE).assertTextEquals("Organizer View")
    composeTestRule
        .onNodeWithTag(MyEventsTestTags.statusPill("arts-festival"))
        .assertTextEquals("● In preparation")
    composeTestRule
        .onNodeWithTag(MyEventsTestTags.statusPill("food-drive"))
        .assertTextEquals("● Ongoing")
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).assertDoesNotExist()
  }

  @Test
  fun manageEvent_reportsTheEventIdEvenWhenEnded() {
    val past = SampleMyEvents.organizer.filter { it.badge == EventBadge.ENDED }
    setContent(MyEventsUiState(selectedTab = EventTab.PAST, past = past))

    composeTestRule
        .onNodeWithTag(MyEventsTestTags.eventAction("volunteer-fair"))
        .assertTextEquals("Manage event")
        .performClick()

    assertEquals(listOf("volunteer-fair"), managed)
  }

  @Test
  fun tabsAndCreateButton_reportClicks() {
    setContent(MyEventsUiState())

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.CREATE_EVENT_BUTTON).performClick()

    assertEquals(listOf(EventTab.PAST), selectedTabs)
    assertEquals(1, createClicks)
  }
}
