// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.navigation.OrganizerTabs
import com.swent.shifter.ui.navigation.Tab
import com.swent.shifter.ui.navigation.VolunteerTabs
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class ShifterNavigationBarTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun show(tabs: List<Tab>, selected: Tab, onSelect: (Tab) -> Unit = {}) {
    composeTestRule.setContent {
      ShifterTheme { ShifterNavigationBar(tabs = tabs, selected = selected, onSelect = onSelect) }
    }
  }

  @Test
  fun bar_showsEveryTabWithItsLabel() {
    show(OrganizerTabs.all, OrganizerTabs.People)

    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    OrganizerTabs.all.forEach { tab ->
      composeTestRule.onNodeWithTag(tab.testTag).assertIsDisplayed()
      composeTestRule.onNode(hasTestTag(tab.testTag) and hasText(tab.label)).assertIsDisplayed()
    }
  }

  // Robolectric's default graphics mode lays no text out, so it would never report an ellipsis.
  // The phone width is the default of robolectric.properties.
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Test
  fun bar_onAPhoneWidth_showsEachOrganizerLabelInFullOnceSelected() {
    var selected by mutableStateOf<Tab>(OrganizerTabs.home)
    composeTestRule.setContent {
      ShifterTheme { ShifterNavigationBar(OrganizerTabs.all, selected, onSelect = {}) }
    }

    // A selected label is bolder, so wider: each one is checked with its tab selected.
    OrganizerTabs.all.forEach { tab ->
      selected = tab
      composeTestRule.waitForIdle()
      val results = mutableListOf<TextLayoutResult>()
      composeTestRule
          .onNode(
              hasText(tab.label) and hasAnyAncestor(hasTestTag(tab.testTag)),
              useUnmergedTree = true,
          )
          .fetchSemanticsNode()
          .config[SemanticsActions.GetTextLayoutResult]
          .action!!
          .invoke(results)
      assertFalse("${tab.label} is cut", results.single().isLineEllipsized(0))
    }
  }

  @Test
  fun bar_marksOnlyTheSelectedTab() {
    show(VolunteerTabs.all, VolunteerTabs.Map)

    VolunteerTabs.all.forEach { tab ->
      val node = composeTestRule.onNodeWithTag(tab.testTag)
      if (tab == VolunteerTabs.Map) node.assertIsSelected() else node.assertIsNotSelected()
    }
  }

  @Test
  fun tappingATab_reportsThatTab() {
    val tapped = mutableListOf<Tab>()
    show(OrganizerTabs.all, OrganizerTabs.home, onSelect = { tapped += it })

    composeTestRule.onNodeWithTag(OrganizerTabs.Map.testTag).performClick()

    assertEquals(listOf<Tab>(OrganizerTabs.Map), tapped)
  }

  @Test
  fun tabs_haveATouchTargetOfAtLeast48dp() {
    show(OrganizerTabs.all, OrganizerTabs.home)

    OrganizerTabs.all.forEach { tab ->
      val height = composeTestRule.onNodeWithTag(tab.testTag).getUnclippedBoundsInRoot().height
      assertTrue("$height is too small", height >= 48.dp)
    }
  }
}
