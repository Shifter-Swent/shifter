// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.authentication

import android.os.Looper
import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.model.authentication.FakeAuthRepository
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepositoryException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ChooseModeViewModelTest {

  private val auth = FakeAuthRepository(ADA)
  private val users = FakeUserRepository()
  private val viewModel = ChooseModeViewModel(auth, users)

  private fun choose(mode: UserMode, chooser: ChooseModeViewModel = viewModel) {
    chooser.choose(mode)
    shadowOf(Looper.getMainLooper()).idle()
  }

  @Test
  fun choose_organizer_createsTheProfileWithItAndEntersIt() {
    choose(UserMode.ORGANIZER)

    val profile = users.users.getValue(UID)
    assertEquals(UserMode.ORGANIZER, profile.mode)
    assertEquals("Ada", profile.displayName)
    assertEquals("ada@example.com", profile.email)
    assertEquals(UserMode.ORGANIZER, viewModel.uiState.value.entered)
    // Still saving while the screen leaves, so the choices do not show again.
    assertTrue(viewModel.uiState.value.saving)
  }

  @Test
  fun choose_volunteer_createsTheProfileWithItAndEntersIt() {
    choose(UserMode.VOLUNTEER)

    assertEquals(UserMode.VOLUNTEER, users.users.getValue(UID).mode)
    assertEquals(UserMode.VOLUNTEER, viewModel.uiState.value.entered)
  }

  @Test
  fun choose_accountWithoutNameOrEmail_createsTheProfileWithEmptyFields() {
    val anonymous = FakeAuthRepository(AuthUser(uid = UID, email = null, displayName = null))

    choose(UserMode.VOLUNTEER, ChooseModeViewModel(anonymous, users))

    val profile = users.users.getValue(UID)
    assertEquals("", profile.displayName)
    assertEquals("", profile.email)
  }

  @Test
  fun choose_profileCreatedMeanwhile_entersItsSavedMode() {
    users.users[UID] =
        User(
            uid = UID,
            displayName = "Ada",
            email = "",
            createdAt = Instant.EPOCH,
            mode = UserMode.VOLUNTEER,
        )

    choose(UserMode.ORGANIZER)

    // The mode already saved is never replaced here: only Settings changes it.
    assertEquals(UserMode.VOLUNTEER, users.users.getValue(UID).mode)
    assertEquals(UserMode.VOLUNTEER, viewModel.uiState.value.entered)
  }

  @Test
  fun choose_failure_showsTheErrorThenSignsOut() {
    users.failure = UserRepositoryException.Unavailable()

    choose(UserMode.ORGANIZER)

    assertEquals(
        "Could not create your profile: The user profile service is unavailable",
        viewModel.uiState.value.errorMsg,
    )
    assertNull(viewModel.uiState.value.entered)
    assertFalse(viewModel.uiState.value.saving)
    // Signed out only once the error is shown, as signing out leaves this screen.
    assertEquals(0, auth.signOutCalls)

    viewModel.onErrorShown()

    assertNull(viewModel.uiState.value.errorMsg)
    assertEquals(1, auth.signOutCalls)
    assertNull(auth.currentUser())
  }

  @Test
  fun choose_withoutSession_doesNothing() {
    val signedOut = ChooseModeViewModel(FakeAuthRepository(), users)

    choose(UserMode.ORGANIZER, signedOut)

    assertTrue(users.users.isEmpty())
    assertEquals(ChooseModeUiState(), signedOut.uiState.value)
  }

  @Test
  fun choose_afterAModeWasEntered_isIgnored() {
    choose(UserMode.VOLUNTEER)

    choose(UserMode.ORGANIZER)

    assertEquals(UserMode.VOLUNTEER, users.users.getValue(UID).mode)
    assertEquals(UserMode.VOLUNTEER, viewModel.uiState.value.entered)
  }

  @Test
  fun onEnteredHandled_clearsTheEvent() {
    choose(UserMode.VOLUNTEER)

    viewModel.onEnteredHandled()

    assertNull(viewModel.uiState.value.entered)
  }

  @Test
  fun choose_whileTheScreenLeaves_isIgnored() {
    choose(UserMode.VOLUNTEER)
    viewModel.onEnteredHandled()

    // A second tap during the transition must not navigate again.
    choose(UserMode.ORGANIZER)

    assertTrue(viewModel.uiState.value.saving)
    assertNull(viewModel.uiState.value.entered)
    assertEquals(UserMode.VOLUNTEER, users.users.getValue(UID).mode)
  }

  @Test
  fun factory_createsViewModelOnTheGivenRepositories() {
    val built =
        ChooseModeViewModel.factory(auth, users)
            .create(ChooseModeViewModel::class.java, CreationExtras.Empty)

    choose(UserMode.ORGANIZER, built)

    assertEquals(UserMode.ORGANIZER, users.users.getValue(UID).mode)
  }

  private companion object {
    const val UID = "uid-1"
    val ADA = AuthUser(uid = UID, email = "ada@example.com", displayName = "Ada")
  }
}
