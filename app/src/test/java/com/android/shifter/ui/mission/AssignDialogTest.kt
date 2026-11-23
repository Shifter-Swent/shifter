// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssignDialogTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val calls = mutableListOf<String>()

  private fun showDialog(assigning: AssignState) {
    composeTestRule.setContent {
      AssignDialog(
          assigning = assigning,
          onToggle = { calls += "toggle $it" },
          onRetry = { calls += "retry" },
          onSave = { calls += "save" },
          onDismiss = { calls += "dismiss" },
      )
    }
  }

  @Test
  fun assignDialog_reportsEachTapToItsCallback() {
    showDialog(assigning())

    composeTestRule.onNodeWithTag(AssigneePickerTestTags.option("u2")).performClick()
    composeTestRule.onNodeWithTag(AssignDialogTestTags.SAVE).assertIsEnabled().performClick()
    composeTestRule.onNodeWithTag(AssignDialogTestTags.CANCEL).performClick()

    assertEquals(listOf("toggle u2", "save", "dismiss"), calls)
  }

  @Test
  fun assignDialog_withTooManyPicked_saysSoAndCannotSave() {
    showDialog(assigning(limit = 1, selectedIds = listOf("u1", "u2")))

    composeTestRule.onNodeWithTag(AssignDialogTestTags.OVER_LIMIT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AssignDialogTestTags.SAVE).assertIsNotEnabled()
  }

  @Test
  fun assignDialog_whileSaving_cannotBeSavedNorCancelled() {
    showDialog(assigning(isSaving = true))

    composeTestRule.onNodeWithTag(AssignDialogTestTags.SAVE).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(AssignDialogTestTags.CANCEL).assertIsNotEnabled()
  }

  @Test
  fun assignDialog_afterAFailedSave_showsTheError() {
    showDialog(assigning(saveFailed = true))

    composeTestRule.onNodeWithTag(AssignDialogTestTags.ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AssignDialogTestTags.SAVE).assertIsEnabled()
  }

  /** The assignees of a mission needing [limit] people, picked among Ada and Bob. */
  private fun assigning(
      limit: Int = 2,
      selectedIds: List<String> = listOf("u1"),
      isSaving: Boolean = false,
      saveFailed: Boolean = false,
  ) =
      AssignState(
          missionId = "m1",
          title = "Bar",
          limit = limit,
          selection =
              AssigneeSelection(
                  candidates = listOf(AssigneeOption("u1", "Ada"), AssigneeOption("u2", "Bob")),
                  selectedIds = selectedIds,
              ),
          isSaving = isSaving,
          saveFailed = saveFailed,
      )
}
