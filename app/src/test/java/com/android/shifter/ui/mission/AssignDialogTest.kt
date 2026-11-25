// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
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
      ShifterTheme {
        AssignDialog(
            assigning = assigning,
            onToggle = { calls += "toggle $it" },
            onRetry = { calls += "retry" },
            onSave = { calls += "save" },
            onDismiss = { calls += "dismiss" },
        )
      }
    }
  }

  @Test
  fun assignDialog_reportsEachTapToItsCallback() {
    showDialog(assigning())

    composeTestRule.onNodeWithTag(AssignDialogTestTags.OVER_LIMIT).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AssignDialogTestTags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AssigneePickerTestTags.option("u2")).performClick()
    composeTestRule.onNodeWithTag(AssignDialogTestTags.SAVE).assertIsEnabled().performClick()
    composeTestRule.onNodeWithTag(AssignDialogTestTags.CANCEL).performClick()

    assertEquals(listOf("toggle u2", "save", "dismiss"), calls)
  }

  @Test
  fun retry_afterTheCandidatesFailed_isReportedAndSaveStaysOff() {
    showDialog(assigning(candidates = emptyList(), loadFailed = true))

    composeTestRule.onNodeWithTag(AssigneePickerTestTags.RETRY).performClick()

    assertEquals(listOf("retry"), calls)
    composeTestRule.onNodeWithTag(AssignDialogTestTags.SAVE).assertIsNotEnabled()
  }

  @Test
  fun assignDialog_whileTheCandidatesLoad_cannotSave() {
    showDialog(assigning(candidates = emptyList(), isLoading = true))

    composeTestRule.onNodeWithTag(AssignDialogTestTags.SAVE).assertIsNotEnabled()
  }

  @Test
  fun assignDialog_withTooManyPicked_saysSoAndCannotSave() {
    showDialog(assigning(limit = 1, selectedIds = listOf("u1", "u2")))

    composeTestRule
        .onNodeWithTag(AssignDialogTestTags.OVER_LIMIT)
        .assertTextEquals("This mission now needs only 1 person. Unpick some to save.")
    composeTestRule.onNodeWithTag(AssignDialogTestTags.SAVE).assertIsNotEnabled()
  }

  @Test
  fun assignDialog_whileSaving_cannotBeSavedCancelledNorPicked() {
    showDialog(assigning(isSaving = true))

    composeTestRule.onNodeWithTag(AssignDialogTestTags.SAVE).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(AssignDialogTestTags.CANCEL).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(AssigneePickerTestTags.option("u2")).assertIsNotEnabled()
  }

  @Test
  fun pressBack_closesTheDialog() {
    showDialog(assigning())

    pressBack()

    composeTestRule.runOnIdle { assertEquals(listOf("dismiss"), calls) }
  }

  @Test
  fun pressBack_whileSaving_keepsTheDialogOpen() {
    showDialog(assigning(isSaving = true))

    pressBack()

    composeTestRule.runOnIdle { assertEquals(emptyList<String>(), calls) }
  }

  @Test
  fun assignDialog_afterAFailedSave_showsTheError() {
    showDialog(assigning(saveFailed = true))

    composeTestRule.onNodeWithTag(AssignDialogTestTags.ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AssignDialogTestTags.SAVE).assertIsEnabled()
  }

  @Test
  fun assignDialog_withSomeoneLocked_showsThemPickedButNotTappable() {
    showDialog(assigning(selectedIds = listOf("u1"), lockedIds = setOf("u1")))

    composeTestRule.onNodeWithTag(AssigneePickerTestTags.option("u1")).assertIsOn()
    composeTestRule.onNodeWithTag(AssigneePickerTestTags.option("u1")).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(AssigneePickerTestTags.option("u2")).assertIsEnabled()
  }

  /** The people picked for a mission needing [limit] of them, among Ada and Bob by default. */
  private fun assigning(
      limit: Int = 2,
      candidates: List<AssigneeOption> =
          listOf(AssigneeOption("u1", "Ada"), AssigneeOption("u2", "Bob")),
      selectedIds: List<String> = listOf("u1"),
      isLoading: Boolean = false,
      loadFailed: Boolean = false,
      isSaving: Boolean = false,
      saveFailed: Boolean = false,
      lockedIds: Set<String> = emptySet(),
  ) =
      AssignState(
          missionId = "m1",
          title = "Bar",
          limit = limit,
          selection =
              AssigneeSelection(
                  candidates = candidates,
                  selectedIds = selectedIds,
                  isLoading = isLoading,
                  loadFailed = loadFailed,
                  lockedIds = lockedIds,
              ),
          isSaving = isSaving,
          saveFailed = saveFailed,
      )
}
