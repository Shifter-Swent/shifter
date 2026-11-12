// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.authentication

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the app opens once [StartViewModel] has read the profile. */
sealed interface StartState {
  /** The profile is being read. */
  data object Reading : StartState

  /** The profile exists: the app enters [mode]. */
  data class Enter(val mode: UserMode) : StartState

  /** There is no profile, as the user left the mode choice without answering: it is asked again. */
  data object ChooseMode : StartState
}

/**
 * Reads, at launch with a session, the mode the user enters: the one saved on their profile, read
 * from the Firestore cache when offline.
 *
 * A profile without a mode enters Volunteer, and nothing is written. So does a profile that cannot
 * be read in [READ_TIMEOUT_MILLIS], so the app never stays stuck on the loading screen. Only a
 * profile known not to exist asks for the mode.
 */
class StartViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

  private val _state = MutableStateFlow<StartState>(StartState.Reading)

  /** What to open, [StartState.Reading] while the profile is read. */
  val state: StateFlow<StartState> = _state.asStateFlow()

  init {
    // Without a session, the navigation returns to sign-in: there is no mode to read.
    authRepository.currentUser()?.uid?.let { uid ->
      viewModelScope.launch { _state.value = read(uid) }
    }
  }

  private suspend fun read(uid: String): StartState {
    val read =
        try {
          withTimeoutOrNull(READ_TIMEOUT_MILLIS) { Read(userRepository.getUser(uid)) }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "Could not read the mode, entering Volunteer", e)
          null
        }
    return when {
      read == null -> StartState.Enter(UserMode.VOLUNTEER)
      read.profile == null -> StartState.ChooseMode
      else -> StartState.Enter(read.profile.mode ?: UserMode.VOLUNTEER)
    }
  }

  /** A profile read in time, null when it does not exist: a timeout gives no [Read] at all. */
  private class Read(val profile: User?)

  companion object {
    private const val TAG = "StartViewModel"

    /** How long the profile is waited for before entering Volunteer. */
    const val READ_TIMEOUT_MILLIS = 5_000L

    fun factory(
        authRepository: AuthRepository,
        userRepository: UserRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { StartViewModel(authRepository, userRepository) }
    }
  }
}
