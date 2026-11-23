// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonChipTest {

  @get:Rule val compose = createComposeRule()

  @Test
  fun personChip_showsTheInitialsThenTheName() {
    compose.setContent { ShifterTheme { PersonChip("Maya Bernard", PersonChipState.PROPOSED) } }

    compose.onNodeWithText("MB").assertExists()
    compose.onNodeWithText("Maya Bernard").assertExists()
  }

  @Test
  fun personChip_withoutAName_saysSo() {
    compose.setContent { ShifterTheme { PersonChip(null, PersonChipState.ASSIGNED) } }

    compose.onNodeWithText("?").assertExists()
    compose.onNodeWithText("Unknown person").assertExists()
  }
}
