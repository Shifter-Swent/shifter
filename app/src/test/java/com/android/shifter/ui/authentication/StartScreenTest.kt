// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.authentication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.model.authentication.FakeAuthRepository
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val session = FakeAuthRepository(AuthUser(uid = UID, email = null, displayName = null))

  private var entered: UserMode? = null

  private var askedForMode = false

  private fun showStartScreen(users: UserRepository) {
    val viewModel = StartViewModel(session, users)
    composeTestRule.setContent {
      StartScreen(viewModel, onModeRead = { entered = it }, onNoProfile = { askedForMode = true })
    }
  }

  @Test
  fun noProfile_asksForTheMode() {
    showStartScreen(FakeUserRepository())

    composeTestRule.waitForIdle()
    assertTrue(askedForMode)
    assertNull(entered)
  }

  @Test
  fun loading_showsTheSpinner() {
    // The profile never answers, so the mode stays being read (the timeout is not reached here).
    val users = mockk<UserRepository>()
    coEvery { users.getUser(UID) } coAnswers { awaitCancellation() }
    showStartScreen(users)

    composeTestRule.onNodeWithTag(StartScreenTestTags.LOADING).assertIsDisplayed()
    assertNull(entered)
  }

  @Test
  fun modeRead_entersTheSavedMode() {
    val organizer =
        User(
            uid = UID,
            displayName = "Ada",
            email = "",
            createdAt = Instant.EPOCH,
            mode = UserMode.ORGANIZER,
        )
    showStartScreen(FakeUserRepository(listOf(organizer)))

    composeTestRule.waitForIdle()
    assertEquals(UserMode.ORGANIZER, entered)
  }

  private companion object {
    const val UID = "uid-1"
  }
}
