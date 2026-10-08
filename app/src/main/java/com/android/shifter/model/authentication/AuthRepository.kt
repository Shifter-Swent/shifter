// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Based on Bootcamp authentication material.
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.swent.shifter.model.authentication

import androidx.credentials.Credential

/** Defines the authentication operations required by the UI layer. */
interface AuthRepository {
  /** Authenticates the user with a Google credential. */
  suspend fun signInWithGoogle(credential: Credential): Result<AuthUser>

  /** Signs out the current user. */
  fun signOut(): Result<Unit>
}
