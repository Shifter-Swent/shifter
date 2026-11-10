// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import android.os.Looper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialUnknownException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.model.authentication.FakeAuthRepository
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.UserRepositoryException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

  private val ada = AuthUser(uid = "uid-1", email = "ada@example.com", displayName = "Ada")
  private val repository = FakeAuthRepository(ada)
  private val userRepository =
      FakeUserRepository(listOf(User("uid-1", "Ada", "ada@example.com", mode = UserMode.VOLUNTEER)))
  private val credentialManager = mockk<CredentialManager>()
  private val viewModel = SettingsViewModel(repository, userRepository, UserMode.VOLUNTEER)

  init {
    coEvery { credentialManager.clearCredentialState(any<ClearCredentialStateRequest>()) } just runs
  }

  @Test
  fun signOut_clearsTheSessionAndTheCredentialsThenReportsIt() {
    viewModel.signOut(credentialManager)
    idle()

    assertEquals(1, repository.signOutCalls)
    coVerify(exactly = 1) {
      credentialManager.clearCredentialState(any<ClearCredentialStateRequest>())
    }
    assertTrue(viewModel.uiState.value.signedOut)
    assertNull(viewModel.uiState.value.errorMsg)
  }

  @Test
  fun signOut_onFirebaseFailure_staysSignedInAndKeepsTheCredentials() {
    repository.signOutResult = Result.failure(IllegalStateException("Network down"))

    viewModel.signOut(credentialManager)
    idle()

    coVerify(exactly = 0) {
      credentialManager.clearCredentialState(any<ClearCredentialStateRequest>())
    }
    assertFalse(viewModel.uiState.value.signedOut)
    assertEquals("Network down", viewModel.uiState.value.errorMsg)
  }

  @Test
  fun signOut_whenClearingTheCredentialsFails_stillReportsTheSignOut() {
    coEvery { credentialManager.clearCredentialState(any<ClearCredentialStateRequest>()) } throws
        ClearCredentialUnknownException("Provider unavailable")

    viewModel.signOut(credentialManager)
    idle()

    assertTrue(viewModel.uiState.value.signedOut)
    assertNull(viewModel.uiState.value.errorMsg)
  }

  @Test
  fun signOut_clearsTheCredentialsEvenIfTheScreenIsLeftMeanwhile() {
    // The auth-state listener leaves Settings as soon as Firebase signs out, which clears this
    // ViewModel while Credential Manager is still working.
    val clearing = CompletableDeferred<Unit>()
    var cleared = false
    coEvery { credentialManager.clearCredentialState(any<ClearCredentialStateRequest>()) } coAnswers
        {
          clearing.await()
          cleared = true
        }
    val store = ViewModelStore()
    val leftViewModel =
        ViewModelProvider(
            store,
            SettingsViewModel.factory(repository, userRepository, UserMode.VOLUNTEER),
        )[SettingsViewModel::class.java]

    leftViewModel.signOut(credentialManager)
    idle()
    store.clear()
    clearing.complete(Unit)
    idle()

    assertTrue(cleared)
  }

  @Test
  fun onSignedOutHandled_resetsTheEvent() {
    viewModel.signOut(credentialManager)
    idle()

    viewModel.onSignedOutHandled()

    assertFalse(viewModel.uiState.value.signedOut)
  }

  @Test
  fun clearErrorMsg_removesTheShownError() {
    repository.signOutResult = Result.failure(IllegalStateException("Network down"))
    viewModel.signOut(credentialManager)
    idle()

    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsg)
  }

  @Test
  fun factory_createsViewModelOnTheGivenRepository() {
    val built =
        SettingsViewModel.factory(repository, userRepository, UserMode.VOLUNTEER)
            .create(SettingsViewModel::class.java, CreationExtras.Empty)

    built.signOut(credentialManager)
    idle()

    assertEquals(1, repository.signOutCalls)
    assertTrue(built.uiState.value.signedOut)
  }

  @Test
  fun changeMode_savesTheOtherModeThenReportsIt() {
    viewModel.changeMode()
    idle()

    assertEquals(UserMode.ORGANIZER, userRepository.users.getValue("uid-1").mode)
    assertEquals(UserMode.ORGANIZER, viewModel.uiState.value.modeChanged)
    assertEquals(UserMode.ORGANIZER, viewModel.uiState.value.mode)
    assertFalse(viewModel.uiState.value.changingMode)
  }

  @Test
  fun changeMode_fromOrganizer_switchesToVolunteer() {
    userRepository.users["uid-1"] =
        User("uid-1", "Ada", "ada@example.com", mode = UserMode.ORGANIZER)
    val organizer = SettingsViewModel(repository, userRepository, UserMode.ORGANIZER)

    organizer.changeMode()
    idle()

    assertEquals(UserMode.VOLUNTEER, userRepository.users.getValue("uid-1").mode)
    assertEquals(UserMode.VOLUNTEER, organizer.uiState.value.modeChanged)
  }

  @Test
  fun changeMode_onFailure_reportsItAndKeepsTheMode() {
    userRepository.failure = UserRepositoryException.Unavailable()

    viewModel.changeMode()
    idle()

    assertTrue(viewModel.uiState.value.modeChangeFailed)
    assertNull(viewModel.uiState.value.modeChanged)
    assertEquals(UserMode.VOLUNTEER, viewModel.uiState.value.mode)
    assertFalse(viewModel.uiState.value.changingMode)
  }

  @Test
  fun changeMode_withoutAnswer_timesOutAndQueuesTheModeBack() {
    // Offline, the write is queued but never answers.
    val offline = mockk<UserRepository>()
    coEvery { offline.setMode("uid-1", UserMode.ORGANIZER) } coAnswers { awaitCancellation() }
    coEvery { offline.setMode("uid-1", UserMode.VOLUNTEER) } just runs
    val waiting = SettingsViewModel(repository, offline, UserMode.VOLUNTEER)

    waiting.changeMode()
    idle()
    assertTrue(waiting.uiState.value.changingMode)
    shadowOf(Looper.getMainLooper())
        .idleFor(Duration.ofMillis(SettingsViewModel.MODE_CHANGE_TIMEOUT_MILLIS))

    assertTrue(waiting.uiState.value.modeChangeFailed)
    assertNull(waiting.uiState.value.modeChanged)
    assertFalse(waiting.uiState.value.changingMode)
    // The mode queued back must land after the write that timed out.
    coVerifyOrder {
      offline.setMode("uid-1", UserMode.ORGANIZER)
      offline.setMode("uid-1", UserMode.VOLUNTEER)
    }
  }

  @Test
  fun changeMode_whileChanging_isIgnored() {
    val pending = CompletableDeferred<Unit>()
    val slow = mockk<UserRepository>()
    coEvery { slow.setMode(any(), any()) } coAnswers { pending.await() }
    val changing = SettingsViewModel(repository, slow, UserMode.VOLUNTEER)

    changing.changeMode()
    idle()
    changing.changeMode()
    pending.complete(Unit)
    idle()

    coVerify(exactly = 1) { slow.setMode(any(), any()) }
  }

  @Test
  fun changeMode_eventsAreResetOnceHandled() {
    viewModel.changeMode()
    idle()
    viewModel.onModeChangeHandled()
    assertNull(viewModel.uiState.value.modeChanged)

    userRepository.failure = UserRepositoryException.Unavailable()
    viewModel.changeMode()
    idle()
    viewModel.onModeChangeErrorShown()
    assertFalse(viewModel.uiState.value.modeChangeFailed)
  }

  /** Runs the coroutines launched in viewModelScope, which use the main looper. */
  private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
