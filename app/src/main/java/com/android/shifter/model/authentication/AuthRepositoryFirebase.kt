// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Based on Bootcamp authentication material.
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.swent.shifter.model.authentication

import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * AuthRepository implementation backed by Firebase Authentication.
 *
 * [auth] is injected explicitly. Current production construction is centralized in
 * [com.swent.shifter.model.RepositoryProviderFactory].
 */
class AuthRepositoryFirebase(
    private val auth: FirebaseAuth,
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

  /** Reads the session Firebase persisted on the device, without any network call. */
  override fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()

  /**
   * Turns Firebase's [FirebaseAuth.AuthStateListener] callback into a [Flow].
   *
   * Nothing runs until the flow is collected; each collector then registers its own listener, which
   * Firebase calls once on registration with the current session, then on every sign-in or
   * sign-out, including a session ending outside the app.
   */
  override val authState: Flow<AuthUser?> = callbackFlow {
    // Sends the session Firebase reports to the collector, mapped to the app's own type.
    val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
    auth.addAuthStateListener(listener)
    // Keeps the flow open while collected, then removes the listener so Firebase does not keep it.
    awaitClose { auth.removeAuthStateListener(listener) }
  }

  /** Clears the current Firebase Authentication session. */
  override fun signOut(): Result<Unit> {
    return runCatching { auth.signOut() }
  }
}

/** Maps the Firebase account to the provider-independent [AuthUser]. */
private fun FirebaseUser.toAuthUser() =
    AuthUser(uid = uid, email = email, displayName = displayName)
