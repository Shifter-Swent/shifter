// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** What the location sharing setting has to tell the volunteer, under its switch. */
enum class LocationSharingNotice {
  /** The setting could not be read, so the switch stays disabled. */
  LOAD_FAILED,
  /** The change was refused or could not be prepared: the switch is back where it was. */
  SAVE_FAILED,
  /** The change is saved on the device but the server has not confirmed it yet. */
  SAVED_OFFLINE,
}

data class SettingsUiState(
    /** The mode the user is in, which the change of mode leaves. */
    val mode: UserMode = UserMode.VOLUNTEER,
    val errorMsg: String? = null,
    /** One-shot event: set once signed out, reset once the screen has navigated. */
    val signedOut: Boolean = false,
    /** Waiting for the server to confirm a change of mode. */
    val changingMode: Boolean = false,
    /** One-shot event: the mode changed to, set once confirmed, reset once the screen has left. */
    val modeChanged: UserMode? = null,
    /** The last change of mode failed or timed out, until the screen has shown it. */
    val modeChangeFailed: Boolean = false,
    /** Whether the volunteer shares their location, `null` until it is read. */
    val locationSharing: Boolean? = null,
    /** True while a change of [locationSharing] is being saved: the switch waits for it. */
    val savingLocationSharing: Boolean = false,
    val locationSharingNotice: LocationSharingNotice? = null,
)

/**
 * Signs the user out, changes the mode saved on their profile from [mode] to the other one, and
 * turns their consent to share their location with their managers on and off (#404). Sharing the
 * position itself is not done here (#377).
 */
class SettingsViewModel(
    private val repository: AuthRepository,
    private val userRepository: UserRepository,
    private val membershipRequestRepository: MembershipRequestRepository,
    private val teamRepository: TeamRepository,
    mode: UserMode,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SettingsUiState(mode = mode))
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  init {
    loadLocationSharing()
  }

  /**
   * Saves the other mode on the profile, then raises [SettingsUiState.modeChanged]. The screen only
   * changes once the server has confirmed: offline, the write would only answer on reconnection, so
   * it stops waiting after [MODE_CHANGE_TIMEOUT_MILLIS] and reports a failure, the mode unchanged.
   */
  fun changeMode() {
    val state = _uiState.value
    val uid = repository.currentUser()?.uid
    if (state.changingMode || uid == null) return
    val target = if (state.mode == UserMode.VOLUNTEER) UserMode.ORGANIZER else UserMode.VOLUNTEER

    viewModelScope.launch {
      _uiState.update { it.copy(changingMode = true, modeChangeFailed = false) }
      try {
        withTimeout(MODE_CHANGE_TIMEOUT_MILLIS) { userRepository.setMode(uid, target) }
      } catch (e: TimeoutCancellationException) {
        // The write stays queued and would reach the server on reconnection: queue the current
        // mode after it, so the profile ends where the user still is.
        viewModelScope.launch { restoreMode(uid, state.mode) }
        failModeChange(e)
        return@launch
      } catch (e: CancellationException) {
        _uiState.update { it.copy(changingMode = false) }
        throw e
      } catch (e: Exception) {
        failModeChange(e)
        return@launch
      }
      _uiState.update { it.copy(mode = target, changingMode = false, modeChanged = target) }
    }
  }

  /** Marks the change of mode as handled, so the screen does not navigate again. */
  fun onModeChangeHandled() {
    _uiState.update { it.copy(modeChanged = null) }
  }

  /** Marks the failed change of mode as shown. */
  fun onModeChangeErrorShown() {
    _uiState.update { it.copy(modeChangeFailed = false) }
  }

  private suspend fun restoreMode(uid: String, mode: UserMode) {
    try {
      userRepository.setMode(uid, mode)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Could not restore the mode after a timeout", e)
    }
  }

  private fun failModeChange(e: Exception) {
    Log.w(TAG, "Could not change the mode", e)
    _uiState.update { it.copy(changingMode = false, modeChangeFailed = true) }
  }

  private fun loadLocationSharing() {
    val uid = repository.currentUser()?.uid ?: return
    viewModelScope.launch {
      try {
        val enabled = userRepository.getUser(uid)?.locationSharingEnabled
        _uiState.update {
          it.copy(
              locationSharing = enabled,
              locationSharingNotice =
                  if (enabled == null) LocationSharingNotice.LOAD_FAILED else null,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(locationSharingNotice = LocationSharingNotice.LOAD_FAILED) }
      }
    }
  }

  /**
   * Turns location sharing on or off. The switch moves at once and goes back if the change fails.
   *
   * Turning it off deletes the position shared in every team the volunteer is in, in the same
   * commit as the setting (see [UserRepository.setLocationSharing]). The teams are those of every
   * event they were accepted to, not only the running ones: deleting a position that is not there
   * is harmless, and reading each event's period would cost as many reads as it saves. Offline,
   * they come from the cache, so a team never opened on this device is missed: the publisher
   * removes that position if a session starts in that team before the event ends (#377), and
   * otherwise it stays readable until the event's period is over.
   *
   * Offline, the server does not confirm the change before [SAVE_TIMEOUT_MILLIS]: it is then kept
   * as saved, since Firestore sends it when the device reconnects, and the volunteer is told so.
   */
  fun setLocationSharing(enabled: Boolean) {
    val state = _uiState.value
    val uid = repository.currentUser()?.uid
    val previous = state.locationSharing
    if (uid == null || previous == null || state.savingLocationSharing) return
    _uiState.update {
      it.copy(locationSharing = enabled, savingLocationSharing = true, locationSharingNotice = null)
    }
    viewModelScope.launch {
      try {
        // Leaving Settings cancels viewModelScope, which would otherwise drop a change still
        // reading the teams before it is written, while the switch already showed it saved. Once
        // written, the change no longer needs this coroutine: Firestore keeps it queued.
        val confirmed =
            withContext(NonCancellable) {
              val teams = if (enabled) emptyList() else teamsOf(uid)
              withTimeoutOrNull(SAVE_TIMEOUT_MILLIS) {
                userRepository.setLocationSharing(uid, enabled, teams)
              }
            }
        _uiState.update {
          it.copy(
              savingLocationSharing = false,
              locationSharingNotice =
                  if (confirmed == null) LocationSharingNotice.SAVED_OFFLINE else null,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update {
          it.copy(
              locationSharing = previous,
              savingLocationSharing = false,
              locationSharingNotice = LocationSharingNotice.SAVE_FAILED,
          )
        }
      }
    }
  }

  /** The teams [uid] is a member of, in every event they were accepted to. */
  private suspend fun teamsOf(uid: String): List<Team> = coroutineScope {
    membershipRequestRepository
        .getMembershipRequestsByUId(uid)
        .filterValues { it.status == MembershipRequestStatus.ACCEPTED }
        .keys
        .map { eventId -> async { teamRepository.getTeamsOfMember(eventId, uid) } }
        .awaitAll()
        .flatten()
  }

  /**
   * Closes the Firebase session, then clears the Credential Manager state so the next sign-in
   * offers the account picker again. If Firebase fails the user stays signed in and nothing is
   * cleared; a failure to clear the credentials does not keep a signed-out user on this screen.
   */
  fun signOut(credentialManager: CredentialManager) {
    viewModelScope.launch {
      repository
          .signOut()
          .onFailure { error ->
            _uiState.update { it.copy(errorMsg = error.localizedMessage ?: "Sign-out failed") }
          }
          .onSuccess {
            try {
              // The auth-state listener leaves this screen as soon as Firebase signs out, which
              // cancels viewModelScope: the cleanup must still finish.
              withContext(NonCancellable) {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
              }
            } catch (e: ClearCredentialException) {
              Log.w(TAG, "Could not clear the credential state", e)
            }
            _uiState.update { it.copy(signedOut = true) }
          }
    }
  }

  /** Marks the sign-out event as handled, so the screen does not navigate again. */
  fun onSignedOutHandled() {
    _uiState.update { it.copy(signedOut = false) }
  }

  /** Clears the sign-out error once it has been shown. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  companion object {
    private const val TAG = "SettingsViewModel"

    /** How long a change of mode may wait for the server before it is reported as failed. */
    const val MODE_CHANGE_TIMEOUT_MILLIS = 10_000L

    /**
     * How long to wait for the server to confirm a change of location sharing before keeping it as
     * saved offline, as for the other offline writes.
     */
    const val SAVE_TIMEOUT_MILLIS = 10_000L

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments. [mode] is the mode of the app Settings is opened from.
     */
    fun factory(repositories: RepositoryProvider, mode: UserMode): ViewModelProvider.Factory =
        viewModelFactory {
          initializer {
            SettingsViewModel(
                repositories.authRepository,
                repositories.userRepository,
                repositories.membershipRequestRepository,
                repositories.teamRepository,
                mode,
            )
          }
        }
  }
}
