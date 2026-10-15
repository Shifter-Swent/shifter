// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.authentication

import androidx.credentials.Credential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [AuthRepository] for ViewModel unit tests, so they never touch Firebase.
 *
 * A successful [signInWithGoogle] opens a session for the returned user and a successful [signOut]
 * closes it, so [currentUser] and [authState] follow the calls like the real repository does.
 *
 * @param signedInUser The account of the session restored at launch, `null` when signed out.
 */
class FakeAuthRepository(signedInUser: AuthUser? = null) : AuthRepository {

  private val session = MutableStateFlow(signedInUser)

  /** What [signInWithGoogle] returns. */
  var signInResult: Result<AuthUser> = Result.failure(IllegalStateException("No result configured"))

  /** What [signOut] returns. */
  var signOutResult: Result<Unit> = Result.success(Unit)

  /** The credential of the last [signInWithGoogle] call, `null` if it was never called. */
  var receivedCredential: Credential? = null
    private set

  /** How many times [signOut] was called. */
  var signOutCalls = 0
    private set

  override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> {
    receivedCredential = credential
    return signInResult.onSuccess { session.value = it }
  }

  override fun currentUser(): AuthUser? = session.value

  override val authState: StateFlow<AuthUser?> = session.asStateFlow()

  /** Ends the session without [signOut], as when it expires or the account is deleted. */
  fun endSession() {
    session.value = null
  }

  override fun signOut(): Result<Unit> {
    signOutCalls++
    return signOutResult.onSuccess { session.value = null }
  }
}
