// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.authentication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.model.authentication.FakeAuthRepository
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.UserRepositoryException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChooseModeScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val auth = FakeAuthRepository(AuthUser(uid = UID, email = null, displayName = "Ada"))

  private var entered: UserMode? = null

  private fun showChoice(users: UserRepository = FakeUserRepository()) {
    val viewModel = ChooseModeViewModel(auth, users)
    composeTestRule.setContent { ChooseModeScreen(viewModel, onModeChosen = { entered = it }) }
  }

  @Test
  fun choice_showsTheQuestionAndBothModes() {
    showChoice()

    composeTestRule
        .onNodeWithTag(ChooseModeTestTags.TITLE)
        .assertTextEquals("How will you use Shifter?")
    composeTestRule.onNodeWithTag(ChooseModeTestTags.VOLUNTEER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ChooseModeTestTags.ORGANIZER).assertIsDisplayed()
  }

  @Test
  fun organizer_entersOrganizer() {
    showChoice()

    composeTestRule.onNodeWithTag(ChooseModeTestTags.ORGANIZER).performClick()
    composeTestRule.waitForIdle()

    assertEquals(UserMode.ORGANIZER, entered)
  }

  @Test
  fun volunteer_entersVolunteer() {
    showChoice()

    composeTestRule.onNodeWithTag(ChooseModeTestTags.VOLUNTEER).performClick()
    composeTestRule.waitForIdle()

    assertEquals(UserMode.VOLUNTEER, entered)
  }

  @Test
  fun saving_showsTheSpinnerInsteadOfTheModes() {
    val users = mockk<UserRepository>()
    coEvery { users.getOrCreateUser(UID, any(), any(), any()) } coAnswers { awaitCancellation() }
    showChoice(users)

    composeTestRule.onNodeWithTag(ChooseModeTestTags.ORGANIZER).performClick()

    composeTestRule.onNodeWithTag(ChooseModeTestTags.SAVING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ChooseModeTestTags.ORGANIZER).assertDoesNotExist()
    assertNull(entered)
  }

  @Test
  fun failure_signsOutWithoutEntering() {
    showChoice(FakeUserRepository().apply { failure = UserRepositoryException.Unavailable() })

    composeTestRule.onNodeWithTag(ChooseModeTestTags.ORGANIZER).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, auth.signOutCalls)
    assertNull(entered)
  }

  private companion object {
    const val UID = "uid-1"
  }
}
