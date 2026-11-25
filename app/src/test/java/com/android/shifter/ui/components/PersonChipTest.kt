// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
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

  private fun hasState(state: String) =
      SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

  @Test
  fun personChip_readsTheNameThenWhetherProposed() {
    compose.setContent { ShifterTheme { PersonChip("Maya Bernard", PersonChipState.PROPOSED) } }

    // The initials only repeat the name, so the chip reads the name alone, then its state.
    compose
        .onNodeWithText("Maya Bernard")
        .assertTextEquals("Maya Bernard")
        .assert(hasState("Proposed"))
  }

  @Test
  fun personChip_alreadyAssigned_saysSo() {
    compose.setContent { ShifterTheme { PersonChip("Léa Petit", PersonChipState.ASSIGNED) } }

    compose.onNodeWithText("Léa Petit").assert(hasState("Already assigned"))
  }

  @Test
  fun personChip_withoutAName_saysSo() {
    compose.setContent { ShifterTheme { PersonChip(null, PersonChipState.ASSIGNED) } }

    compose.onNodeWithText("Unnamed volunteer").assertExists()
  }
}
