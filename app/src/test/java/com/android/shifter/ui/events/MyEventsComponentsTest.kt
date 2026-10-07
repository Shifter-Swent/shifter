// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.events.components.EventCard
import com.swent.shifter.ui.events.components.EventCardAction
import com.swent.shifter.ui.events.components.EventTabs
import com.swent.shifter.ui.events.components.MyEventsLayout
import com.swent.shifter.ui.events.components.MyEventsTopBar
import com.swent.shifter.ui.events.components.StatusPill
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyEventsComponentsTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun card(id: String, badge: EventBadge) =
      EventCardUi(
          id = id,
          title = "Event $id",
          dateLabel = "Sat 14 Jun 2025",
          locationLabel = "Lyon, Place Bellecour",
          timeLabel = "08:00 – 14:00",
          badge = badge,
          footerLabel = "Role: Volunteer",
      )

  @Test
  fun eventCard_showsEveryLabelAndReportsItsAction() {
    var clicks = 0
    composeTestRule.setContent {
      ShifterTheme {
        EventCard(
            card("e1", EventBadge.CONFIRMED),
            EventCardAction("Withdraw", Color.Red) { clicks++ },
        )
      }
    }

    composeTestRule.onNodeWithTag(MyEventsTestTags.statusPill("e1")).assertTextEquals("● Confirmed")
    listOf(
            "Event e1",
            "Sat 14 Jun 2025",
            "Lyon, Place Bellecour",
            "08:00 – 14:00",
            "Role: Volunteer",
        )
        .forEach { composeTestRule.onNodeWithText(it).assertIsDisplayed() }
    composeTestRule
        .onNodeWithTag(MyEventsTestTags.eventAction("e1"))
        .assertTextEquals("Withdraw")
        .performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun eventCard_withoutAction_hidesTheLink() {
    composeTestRule.setContent { ShifterTheme { EventCard(card("e1", EventBadge.ENDED), null) } }

    composeTestRule.onNodeWithTag(MyEventsTestTags.eventAction("e1")).assertDoesNotExist()
  }

  @Test
  fun statusPill_showsTheLabelOfEveryBadge() {
    composeTestRule.setContent {
      ShifterTheme {
        Column {
          EventBadge.entries.forEach { StatusPill(it, Modifier.testTag("pill_${it.name}")) }
        }
      }
    }

    EventBadge.entries.forEach {
      composeTestRule.onNodeWithTag("pill_${it.name}").assertTextEquals("● ${it.label}")
    }
  }

  @Test
  fun topBar_showsTheOptionalSubtitleAndReportsAvatarClicks() {
    var avatarClicks = 0
    composeTestRule.setContent {
      ShifterTheme {
        MyEventsTopBar(
            avatarInitial = "J",
            onAvatarClick = { avatarClicks++ },
            subtitle = "Organizer View",
        )
      }
    }

    composeTestRule.onNodeWithTag(MyEventsTestTags.TITLE).assertTextEquals("My Events")
    composeTestRule.onNodeWithTag(MyEventsTestTags.SUBTITLE).assertTextEquals("Organizer View")
    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()

    assertEquals(1, avatarClicks)
  }

  @Test
  fun topBar_withoutSubtitle_showsOnlyTheTitle() {
    composeTestRule.setContent {
      ShifterTheme { MyEventsTopBar(avatarInitial = "J", onAvatarClick = {}) }
    }

    composeTestRule.onNodeWithTag(MyEventsTestTags.SUBTITLE).assertDoesNotExist()
  }

  @Test
  fun eventTabs_markTheSelectedTabAndReportClicks() {
    val selected = mutableListOf<EventTab>()
    composeTestRule.setContent {
      ShifterTheme { EventTabs(selected = EventTab.UPCOMING, onSelect = { selected += it }) }
    }

    composeTestRule.onNodeWithTag(MyEventsTestTags.UPCOMING_TAB).assertIsSelected()
    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).assertIsNotSelected().performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.UPCOMING_TAB).performClick()

    assertEquals(listOf(EventTab.PAST, EventTab.UPCOMING), selected)
  }

  private fun setLayout(state: MyEventsUiState) {
    composeTestRule.setContent {
      ShifterTheme {
        MyEventsLayout(
            state = state,
            avatarInitial = "J",
            onTabSelected = {},
            onAvatarClick = {},
            cardAction = { EventCardAction("Open", Color.Blue) {} },
        ) {
          Text("Floating", Modifier.testTag("floating"))
        }
      }
    }
  }

  @Test
  fun layout_showsTheCardsOfTheSelectedTabAndTheFloatingContent() {
    setLayout(
        MyEventsUiState(
            selectedTab = EventTab.PAST,
            upcoming = listOf(card("upcoming", EventBadge.CONFIRMED)),
            past = listOf(card("past", EventBadge.ENDED)),
        )
    )

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).assertIsSelected()
    composeTestRule.onNodeWithTag(MyEventsTestTags.eventCard("past")).assertExists()
    composeTestRule.onNodeWithTag(MyEventsTestTags.eventAction("past")).assertTextEquals("Open")
    composeTestRule.onNodeWithTag(MyEventsTestTags.eventCard("upcoming")).assertDoesNotExist()
    composeTestRule.onNodeWithTag("floating").assertIsDisplayed()
  }

  @Test
  fun layout_showsLoadingInsteadOfTheList() {
    setLayout(MyEventsUiState(isLoading = true))

    composeTestRule.onNodeWithTag(MyEventsTestTags.LOADING).assertExists()
    composeTestRule.onNodeWithTag(MyEventsTestTags.EVENT_LIST).assertDoesNotExist()
  }

  @Test
  fun layout_showsTheErrorMessage() {
    setLayout(MyEventsUiState(errorMessage = "Network down"))

    composeTestRule.onNodeWithTag(MyEventsTestTags.ERROR).assertTextEquals("Network down")
  }

  @Test
  fun layout_showsAnEmptyMessagePerTab() {
    setLayout(MyEventsUiState(selectedTab = EventTab.PAST))

    composeTestRule.onNodeWithTag(MyEventsTestTags.EMPTY_STATE).assertTextEquals("No past events")
  }
}
