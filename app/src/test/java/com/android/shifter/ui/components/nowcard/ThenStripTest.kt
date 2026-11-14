// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThenStripTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun thenStrip_showsWhenWhatAndWhere() {
    composeTestRule.setContent {
      ShifterTheme { ThenStrip(Duration.ofMinutes(30), "Welcome desk", place = "Main gate") }
    }

    composeTestRule.onNodeWithTag(NowCardTestTags.NEXT).assertIsDisplayed()
    composeTestRule.onNodeWithText("Then, in 30 min").assertIsDisplayed()
    composeTestRule.onNodeWithText("Welcome desk · Main gate").assertIsDisplayed()
  }

  @Test
  fun thenStrip_withoutPlace_showsTheTitle() {
    composeTestRule.setContent {
      ShifterTheme { ThenStrip(Duration.ofMinutes(10), "Welcome desk", place = null) }
    }

    composeTestRule.onNodeWithText("Welcome desk").assertIsDisplayed()
  }

  @Test
  fun thenStrip_inMoreThanAnHour_writesHoursAndMinutes() {
    composeTestRule.setContent {
      ShifterTheme { ThenStrip(Duration.ofMinutes(75), "Welcome desk", place = null) }
    }

    composeTestRule.onNodeWithText("Then, in 1 h 15").assertIsDisplayed()
  }

  @Test
  fun thenStrip_withNoteAndAction_showsThem() {
    composeTestRule.setContent {
      ShifterTheme {
        ThenStrip(
            startsIn = Duration.ofMinutes(10),
            title = "Welcome desk",
            place = null,
            note = { Text("Short of 1 volunteer") },
            action = { Text("View on map") },
        )
      }
    }

    composeTestRule.onNodeWithText("Short of 1 volunteer").assertIsDisplayed()
    composeTestRule.onNodeWithText("View on map").assertIsDisplayed()
  }
}
