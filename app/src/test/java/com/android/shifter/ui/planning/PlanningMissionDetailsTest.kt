// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A tap on a mission's row of the [Planning] shows its description, and a second tap hides it. */
@RunWith(AndroidJUnit4::class)
class PlanningMissionDetailsTest {

  @get:Rule val compose = createComposeRule()

  private fun mission(id: String, description: String = "Details of $id") =
      PlanningMission(
          id = id,
          title = "Mission $id",
          startAt = START,
          endAt = START.plusSeconds(3600),
          description = description,
      )

  /** Shows [missions] with the expanded row kept here, as the volunteer's Overview keeps it. */
  private fun show(vararg missions: PlanningMission) {
    compose.setContent {
      var expanded by remember { mutableStateOf<String?>(null) }
      ShifterTheme {
        Planning(
            missions = missions.toList(),
            zone = ZoneOffset.UTC,
            expandedMissionId = expanded,
            onMissionClick = { id -> expanded = if (expanded == id) null else id },
        )
      }
    }
  }

  private fun row(id: String) = compose.onNodeWithTag(PlanningTestTags.mission(id))

  private fun description(id: String) =
      compose.onNodeWithTag(PlanningTestTags.description(id), useUnmergedTree = true)

  @Test
  fun planning_hidesTheDescriptions() {
    show(mission("a"))

    description("a").assertDoesNotExist()
  }

  @Test
  fun tap_onAMission_showsItsDescription() {
    show(mission("a"))

    row("a").performClick()

    description("a").assertTextEquals("Details of a")
  }

  @Test
  fun tap_again_hidesTheDescription() {
    show(mission("a"))

    row("a").performClick()
    row("a").performClick()

    description("a").assertDoesNotExist()
  }

  @Test
  fun tap_onAnotherMission_showsOnlyItsDescription() {
    show(mission("a"), mission("b"))

    row("a").performClick()
    row("b").performClick()

    description("a").assertDoesNotExist()
    description("b").assertTextEquals("Details of b")
  }

  @Test
  fun tap_onAMissionWithoutDescription_saysSo() {
    show(mission("a", description = " "))

    row("a").performClick()

    description("a").assertTextEquals("No details for this mission.")
  }

  /**
   * Shows the Planning under a tall block, in a short scrolling page, as the Overview hosts it.
   * [visible] hides and shows it again, as when the page comes back, and [revealRequest] is the now
   * card's "View details".
   */
  private fun showBelowTheFold(
      initiallyExpanded: String?,
      visible: () -> Boolean = { true },
      revealRequest: () -> Int = { 0 },
  ): ScrollState {
    lateinit var scroll: ScrollState
    compose.setContent {
      scroll = rememberScrollState()
      var expanded by remember { mutableStateOf(initiallyExpanded) }
      ShifterTheme {
        Column(Modifier.height(300.dp).verticalScroll(scroll)) {
          Spacer(Modifier.height(2000.dp))
          if (visible()) {
            Planning(
                missions = listOf(mission("a")),
                zone = ZoneOffset.UTC,
                expandedMissionId = expanded,
                onMissionClick = { id -> expanded = if (expanded == id) null else id },
                revealRequest = revealRequest(),
            )
          }
        }
      }
    }
    return scroll
  }

  @Test
  fun tap_onAMissionBelowTheFold_bringsItIntoView() {
    val scroll = showBelowTheFold(initiallyExpanded = null)

    // The row just shows at the bottom of the page: its description would open below it.
    row("a").performScrollTo()
    val before = scroll.value

    row("a").performClick()
    compose.waitForIdle()

    assertTrue(scroll.value > before)
  }

  @Test
  fun comingBack_withARowExpanded_keepsTheScroll() {
    var visible by mutableStateOf(true)
    val scroll = showBelowTheFold(initiallyExpanded = "a", visible = { visible })
    compose.waitForIdle()

    visible = false
    compose.waitForIdle()
    visible = true
    compose.waitForIdle()

    assertEquals(0, scroll.value)
  }

  @Test
  fun revealRequest_onARowAlreadyExpanded_bringsItIntoViewAgain() {
    var request by mutableStateOf(0)
    val scroll = showBelowTheFold(initiallyExpanded = "a", revealRequest = { request })
    compose.waitForIdle()
    assertEquals(0, scroll.value)

    request++
    compose.waitForIdle()

    assertTrue(scroll.value > 0)
  }

  @Test
  fun row_saysWhetherItIsExpanded() {
    show(mission("a"))
    row("a").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))

    row("a").performClick()

    row("a").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
  }

  @Test
  fun planning_withoutClickHandler_cannotBeTapped() {
    compose.setContent {
      ShifterTheme { Planning(missions = listOf(mission("a")), zone = ZoneOffset.UTC) }
    }

    row("a").assertHasNoClickAction()
  }

  private companion object {
    val START: Instant = Instant.parse("2025-06-14T08:00:00Z")
  }
}
