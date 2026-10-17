// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssigneePickerTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val candidates =
      listOf(AssigneeOption("ana", "Ana"), AssigneeOption("ben", "Ben"), AssigneeOption("x", null))

  private fun setContent(
      selection: AssigneeSelection,
      limit: Int = 2,
      onToggle: (String) -> Unit = {},
      onRetry: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      ShifterTheme {
        AssigneePicker(
            selection = selection,
            limit = limit,
            enabled = true,
            onToggle = onToggle,
            onRetry = onRetry,
        )
      }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  @Test
  fun showsTheCandidatesWhoIsPickedAndTheCount() {
    setContent(AssigneeSelection(candidates = candidates, selectedIds = listOf("ben")))

    node(AssigneePickerTestTags.COUNT).assertTextEquals("1/2 assigned")
    node(AssigneePickerTestTags.option("ana")).assertIsOff()
    node(AssigneePickerTestTags.option("ben")).assertIsOn()
    composeTestRule.onNodeWithText("Unnamed volunteer").assertIsDisplayed()
  }

  @Test
  fun tappingACandidate_reportsTheirId() {
    val toggled = mutableListOf<String>()
    setContent(AssigneeSelection(candidates = candidates), onToggle = { toggled += it })

    node(AssigneePickerTestTags.option("ana")).performClick()

    assertEquals(listOf("ana"), toggled)
  }

  @Test
  fun whenFull_onlyThePeoplePickedCanBeTapped() {
    setContent(AssigneeSelection(candidates = candidates, selectedIds = listOf("ana")), limit = 1)

    node(AssigneePickerTestTags.option("ana")).assertIsEnabled()
    node(AssigneePickerTestTags.option("ben")).assertIsNotEnabled()
  }

  @Test
  fun whileLoading_showsAProgressInsteadOfTheCandidates() {
    setContent(AssigneeSelection(isLoading = true))

    node(AssigneePickerTestTags.LOADING).assertIsDisplayed()
    node(AssigneePickerTestTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun withNobodyToAssign_saysSo() {
    setContent(AssigneeSelection())

    node(AssigneePickerTestTags.EMPTY).assertIsDisplayed()
  }

  @Test
  fun whenLoadingFailed_offersToRetry() {
    var retries = 0
    setContent(AssigneeSelection(loadFailed = true), onRetry = { retries++ })

    node(AssigneePickerTestTags.LOAD_ERROR).assertIsDisplayed()
    node(AssigneePickerTestTags.RETRY).performClick()

    assertEquals(1, retries)
  }
}
