// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Based on Bootcamp authentication material.

package com.swent.shifter.model.authentication

import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

/** AuthRepository implementation backed by Firebase Authentication. */
class AuthRepositoryFirebase(
    private val auth: FirebaseAuth = Firebase.auth,
    private val helper: GoogleSignInHelper = DefaultGoogleSignInHelper(),
) : AuthRepository {

  /** Validates the Google credential and signs the user in with Firebase Authentication. */
  override suspend fun signInWithGoogle(credential: Credential): Result<FirebaseUser> {
    if (credential !is CustomCredential || credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      return Result.failure(IllegalArgumentException("Unsupported Google credential"))
    }

    return runCatching {
      val idToken = helper.extractIdTokenCredential(credential.data).idToken
      val firebaseUser =
          auth.signInWithCredential(helper.toFirebaseCredential(idToken)).await().user
              ?: error("Firebase returned no authenticated user")
      firebaseUser
    }
  }

  /** Clears the current Firebase Authentication session. */
  override fun signOut(): Result<Unit> {
    return runCatching { auth.signOut() }
  }
}
