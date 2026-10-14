// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Based on Bootcamp authentication material.
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.swent.shifter.model.authentication

import androidx.credentials.Credential
import kotlinx.coroutines.flow.Flow

/** Defines the authentication operations required by the UI layer. */
interface AuthRepository {
  /** Authenticates the user with a Google credential. */
  suspend fun signInWithGoogle(credential: Credential): Result<AuthUser>

  /** The account of the session restored at launch or opened since, `null` when signed out. */
  fun currentUser(): AuthUser?

  /**
   * The account of the current session, emitted on collection and again whenever a session opens or
   * ends, including when it ends outside the app.
   *
   * An account disabled or deleted on the server is reported once the SDK finds it gone, at its
   * next ID token refresh (within the hour); until then the local session stays.
   */
  val authState: Flow<AuthUser?>

  /** Signs out the current user. */
  fun signOut(): Result<Unit>
}
