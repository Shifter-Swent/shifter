// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.navigation.OrganizerTabs
import com.swent.shifter.ui.navigation.Tab
import com.swent.shifter.ui.navigation.VolunteerTabs
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
