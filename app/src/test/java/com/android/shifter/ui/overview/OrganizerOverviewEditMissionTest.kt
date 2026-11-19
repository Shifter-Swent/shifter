// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opening the edit form of a mission from [OrganizerOverviewContent]. */
@RunWith(AndroidJUnit4::class)
class OrganizerOverviewEditMissionTest {

  @get:Rule val compose = createComposeRule()

  private val now = Instant.parse("2027-06-19T10:00:00Z")
  private val state =
      OrganizerOverviewUiState(
          isLoading = false,
          missions =
              listOf(
                  OverviewMission(
                      id = "m1",
                      title = "Set up",
                      teamId = null,
                      isCompleted = false,
                      startAt = now,
                  ),
                  OverviewMission(
                      id = "m2",
                      title = "Clean up",
                      teamId = null,
                      isCompleted = true,
                      startAt = now,
                  ),
              ),
      )

  @Test
  fun missionRow_click_opensItsEditForm() {
    val edited = mutableListOf<String>()
    compose.setContent {
      ShifterTheme {
        OrganizerOverviewContent(
            state,
            OrganizerOverviewActions(onEditMission = { edited += it }),
            now = now,
            zone = ZoneOffset.UTC,
        )
      }
    }

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.mission("m1")).performClick()

    assertEquals(listOf("m1"), edited)
  }

  @Test
  fun missionRow_ofACompletedMission_doesNotOpenTheForm() {
    val edited = mutableListOf<String>()
    compose.setContent {
      ShifterTheme {
        OrganizerOverviewContent(
            state,
            OrganizerOverviewActions(onEditMission = { edited += it }),
            now = now,
            zone = ZoneOffset.UTC,
        )
      }
    }

    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.mission("m2"))
        .assertIsNotEnabled()
        .performClick()

    assertEquals(emptyList<String>(), edited)
  }
}
