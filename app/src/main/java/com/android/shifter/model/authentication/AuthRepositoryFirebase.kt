// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Based on Bootcamp authentication material.
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.swent.shifter.model.authentication

import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/** AuthRepository implementation backed by Firebase Authentication. */
class AuthRepositoryFirebase(
    private val auth: FirebaseAuth = Firebase.auth,
    private val helper: GoogleSignInHelper = DefaultGoogleSignInHelper(),
) : AuthRepository {

  /** Validates the Google credential and signs the user in with Firebase Authentication. */
  override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> {
    if (credential !is CustomCredential || credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      return Result.failure(IllegalArgumentException("Unsupported Google credential"))
    }

    return try {
      val idToken = helper.extractIdTokenCredential(credential.data).idToken
      val firebaseUser =
          auth.signInWithCredential(helper.toFirebaseCredential(idToken)).await().user
              ?: error("Firebase returned no authenticated user")
      Result.success(firebaseUser.toAuthUser())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /** Clears the current Firebase Authentication session. */
  override fun signOut(): Result<Unit> {
    return runCatching { auth.signOut() }
  }
}

/** Maps the Firebase account to the provider-independent [AuthUser]. */
private fun FirebaseUser.toAuthUser() =
    AuthUser(uid = uid, email = email, displayName = displayName)
