// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.authentication

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads, at launch with a session, the mode the user enters: the one saved on their profile, read
 * from the Firestore cache when offline.
 *
 * A profile without a mode enters Volunteer, and nothing is written. So does a profile that cannot
 * be read in [READ_TIMEOUT_MILLIS], so the app never stays stuck on the loading screen.
 */
class StartViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

  private val _mode = MutableStateFlow<UserMode?>(null)

  /** The mode to enter, null while it is read. */
  val mode: StateFlow<UserMode?> = _mode.asStateFlow()

  init {
    // Without a session, the navigation returns to sign-in: there is no mode to read.
    authRepository.currentUser()?.uid?.let { uid ->
      viewModelScope.launch { _mode.value = read(uid) }
    }
  }

  private suspend fun read(uid: String): UserMode {
    val saved =
        try {
          withTimeoutOrNull(READ_TIMEOUT_MILLIS) { userRepository.getUser(uid)?.mode }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "Could not read the mode, entering Volunteer", e)
          null
        }
    return saved ?: UserMode.VOLUNTEER
  }

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
