// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import android.os.Looper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialUnknownException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.model.authentication.FakeAuthRepository
import com.swent.shifter.model.location.LocalSharingConsent
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.FakeMembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
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
import java.time.Instant
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

  private val repository = FakeAuthRepository(AuthUser(UID, "ada@example.com", "Ada"))
  private val users = FakeUserRepository(listOf(User(UID, "Ada", "ada@example.com")))
  private val requests = FakeMembershipRequestRepository()
  private val teams = FakeTeamRepository()
  private val consent = LocalSharingConsent()
  private val credentialManager = mockk<CredentialManager>()
  private val viewModel by lazy {
    SettingsViewModel(repository, users, requests, teams, UserMode.VOLUNTEER, consent)
  }

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
        ViewModelProvider(store, SettingsViewModel.factory(repositories(), UserMode.VOLUNTEER))[
            SettingsViewModel::class.java]

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
        SettingsViewModel.factory(repositories(), UserMode.VOLUNTEER)
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

    assertEquals(UserMode.ORGANIZER, users.users.getValue(UID).mode)
    assertEquals(UserMode.ORGANIZER, viewModel.uiState.value.modeChanged)
    assertEquals(UserMode.ORGANIZER, viewModel.uiState.value.mode)
    assertFalse(viewModel.uiState.value.changingMode)
  }

  @Test
  fun changeMode_fromOrganizer_switchesToVolunteer() {
    users.users[UID] = User(UID, "Ada", "ada@example.com", mode = UserMode.ORGANIZER)
    val organizer =
        SettingsViewModel(repository, users, requests, teams, UserMode.ORGANIZER, consent)

    organizer.changeMode()
    idle()

    assertEquals(UserMode.VOLUNTEER, users.users.getValue(UID).mode)
    assertEquals(UserMode.VOLUNTEER, organizer.uiState.value.modeChanged)
  }

  @Test
  fun changeMode_onFailure_reportsItAndKeepsTheMode() {
    users.failure = UserRepositoryException.Unavailable()

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
    coEvery { offline.setMode(UID, UserMode.ORGANIZER) } coAnswers { awaitCancellation() }
    coEvery { offline.setMode(UID, UserMode.VOLUNTEER) } just runs
    val waiting =
        SettingsViewModel(repository, offline, requests, teams, UserMode.VOLUNTEER, consent)

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
      offline.setMode(UID, UserMode.ORGANIZER)
      offline.setMode(UID, UserMode.VOLUNTEER)
    }
  }

  @Test
  fun changeMode_whileChanging_isIgnored() {
    val pending = CompletableDeferred<Unit>()
    val slow = mockk<UserRepository>()
    coEvery { slow.setMode(any(), any()) } coAnswers { pending.await() }
    val changing = SettingsViewModel(repository, slow, requests, teams, UserMode.VOLUNTEER, consent)

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

    users.failure = UserRepositoryException.Unavailable()
    viewModel.changeMode()
    idle()
    viewModel.onModeChangeErrorShown()
    assertFalse(viewModel.uiState.value.modeChangeFailed)
  }

  @Test
  fun locationSharing_isReadFromTheProfile() {
    users.users[UID] = users.users.getValue(UID).copy(locationSharingEnabled = true)

    idle(viewModel)

    assertEquals(true, viewModel.uiState.value.locationSharing)
    assertNull(viewModel.uiState.value.locationSharingNotice)
  }

  @Test
  fun locationSharing_whenTheProfileCannotBeRead_staysUnknownAndSaysSo() {
    users.failure = UserRepositoryException.Unavailable()

    idle(viewModel)
    viewModel.setLocationSharing(true)
    idle()

    // Unknown, so the switch cannot be used: turning it on would overwrite a setting never read.
    assertNull(viewModel.uiState.value.locationSharing)
    assertEquals(LocationSharingNotice.LOAD_FAILED, viewModel.uiState.value.locationSharingNotice)
  }

  @Test
  fun locationSharing_withoutProfile_saysItCouldNotBeLoaded() {
    users.users.clear()

    idle(viewModel)

    assertNull(viewModel.uiState.value.locationSharing)
    assertEquals(LocationSharingNotice.LOAD_FAILED, viewModel.uiState.value.locationSharingNotice)
  }

  @Test
  fun setLocationSharing_on_savesItWithoutRemovingAnything() {
    idle(viewModel)

    viewModel.setLocationSharing(true)
    idle()

    assertTrue(users.users.getValue(UID).locationSharingEnabled)
    assertEquals(true, viewModel.uiState.value.locationSharing)
    assertFalse(viewModel.uiState.value.savingLocationSharing)
    assertEquals(emptyList<Team>(), users.positionsRemovedFrom)
  }

  @Test
  fun setLocationSharing_off_removesThePositionInEveryTeamOfTheAcceptedEvents() {
    users.users[UID] = users.users.getValue(UID).copy(locationSharingEnabled = true)
    request("event-1", MembershipRequestStatus.ACCEPTED)
    request("event-2", MembershipRequestStatus.ACCEPTED)
    request("event-3", MembershipRequestStatus.PENDING)
    val bar = team("event-1", "bar", listOf(UID))
    val gate = team("event-2", "gate", listOf(UID, "other"))
    team("event-2", "kitchen", listOf("other"))
    team("event-3", "parking", listOf(UID))
    idle(viewModel)

    viewModel.setLocationSharing(false)
    idle()

    assertFalse(users.users.getValue(UID).locationSharingEnabled)
    assertEquals(setOf(bar, gate), users.positionsRemovedFrom.toSet())
    assertEquals(false, viewModel.uiState.value.locationSharing)
  }

  @Test
  fun setLocationSharing_whenTheTeamsCannotBeRead_savesNothingAndGoesBack() {
    users.users[UID] = users.users.getValue(UID).copy(locationSharingEnabled = true)
    request("event-1", MembershipRequestStatus.ACCEPTED)
    teams.failure = IllegalStateException("Network down")
    idle(viewModel)

    viewModel.setLocationSharing(false)
    idle()

    // Turning it off without removing the positions would leave the manager seeing one.
    assertTrue(users.users.getValue(UID).locationSharingEnabled)
    assertEquals(true, viewModel.uiState.value.locationSharing)
    assertEquals(LocationSharingNotice.SAVE_FAILED, viewModel.uiState.value.locationSharingNotice)
  }

  @Test
  fun setLocationSharing_whenRefused_switchesBackAndSaysSo() {
    idle(viewModel)
    users.failure = UserRepositoryException.PermissionDenied()

    viewModel.setLocationSharing(true)
    idle()

    assertEquals(false, viewModel.uiState.value.locationSharing)
    assertFalse(viewModel.uiState.value.savingLocationSharing)
    assertEquals(LocationSharingNotice.SAVE_FAILED, viewModel.uiState.value.locationSharingNotice)
  }

  @Test
  fun setLocationSharing_whileSaving_movesTheSwitchAndIgnoresAnotherChange() {
    users.unconfirmedWrite = CompletableDeferred()
    idle(viewModel)

    viewModel.setLocationSharing(true)
    idle()
    viewModel.setLocationSharing(false)
    idle()

    assertEquals(true, viewModel.uiState.value.locationSharing)
    assertTrue(viewModel.uiState.value.savingLocationSharing)
    users.unconfirmedWrite?.complete(Unit)
    idle()
    assertFalse(viewModel.uiState.value.savingLocationSharing)
    assertTrue(users.users.getValue(UID).locationSharingEnabled)
  }

  @Test
  fun setLocationSharing_unconfirmed_keepsTheChangeAndSaysItIsSavedOffline() {
    users.unconfirmedWrite = CompletableDeferred()
    idle(viewModel)

    viewModel.setLocationSharing(true)
    shadowOf(Looper.getMainLooper())
        .idleFor(Duration.ofMillis(SettingsViewModel.SAVE_TIMEOUT_MILLIS + 1))

    assertEquals(true, viewModel.uiState.value.locationSharing)
    assertFalse(viewModel.uiState.value.savingLocationSharing)
    assertEquals(LocationSharingNotice.SAVED_OFFLINE, viewModel.uiState.value.locationSharingNotice)
  }

  @Test
  fun setLocationSharing_off_isSavedEvenIfSettingsIsLeftWhileReadingTheTeams() {
    users.users[UID] = users.users.getValue(UID).copy(locationSharingEnabled = true)
    request("event-1", MembershipRequestStatus.ACCEPTED)
    val bar = team("event-1", "bar", listOf(UID))
    val reading = CompletableDeferred<Unit>()
    val slowTeams = mockk<TeamRepository>()
    coEvery { slowTeams.getTeamsOfMember("event-1", UID) } coAnswers
        {
          reading.await()
          listOf(bar)
        }
    val store = ViewModelStore()
    val leftViewModel =
        ViewModelProvider(
            store,
            viewModelFactory {
              initializer {
                SettingsViewModel(
                    repository,
                    users,
                    requests,
                    slowTeams,
                    UserMode.VOLUNTEER,
                    consent,
                )
              }
            },
        )[SettingsViewModel::class.java]
    idle()

    leftViewModel.setLocationSharing(false)
    idle()
    store.clear()
    reading.complete(Unit)
    idle()

    assertFalse(users.users.getValue(UID).locationSharingEnabled)
    assertEquals(listOf(bar), users.positionsRemovedFrom)
  }

  @Test
  fun setLocationSharing_whenSignedOut_doesNothing() {
    idle(viewModel)
    repository.signOut()

    viewModel.setLocationSharing(true)
    idle()

    assertFalse(viewModel.uiState.value.savingLocationSharing)
    assertFalse(users.users.getValue(UID).locationSharingEnabled)
  }

  private fun request(eventId: String, status: MembershipRequestStatus) =
      requests.add(
          eventId,
          MembershipRequest(
              userId = UID,
              teamId = "any",
              availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH)),
              status = status,
              createdAt = Instant.EPOCH,
          ),
      )

  private fun team(eventId: String, id: String, memberIds: List<String>) =
      Team(
              id = id,
              eventId = eventId,
              name = id,
              description = "",
              icon = "",
              memberIds = memberIds,
              createdAt = Instant.EPOCH,
          )
          .also { teams.teams += it }

  private fun repositories() =
      RepositoryProvider(
          repository,
          mockk(),
          users,
          requests,
          mockk(),
          teams,
          mockk(),
          mockk(),
          mockk(),
      )

  /** Builds [viewModel] and lets it read the profile. */
  private fun idle(viewModel: SettingsViewModel) {
    viewModel.uiState
    idle()
  }

  /** Runs the coroutines launched in viewModelScope, which use the main looper. */
  private fun idle() = shadowOf(Looper.getMainLooper()).idle()

  private companion object {
    const val UID = "ada"
  }
}
