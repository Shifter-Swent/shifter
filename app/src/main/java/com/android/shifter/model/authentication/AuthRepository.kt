package com.swent.shifter.model.authentication

import androidx.credentials.Credential
import com.google.firebase.auth.FirebaseUser

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Based on Bootcamp authentication material.

/** Defines the authentication operations required by the UI layer. */
interface AuthRepository {
  /** Authenticates the user with Firebase using a Google credential. */
  suspend fun signInWithGoogle(credential: Credential): Result<FirebaseUser>

  /** Signs out the current Firebase user. */
  fun signOut(): Result<Unit>
}
