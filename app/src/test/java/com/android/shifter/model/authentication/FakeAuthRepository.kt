// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.authentication

import androidx.credentials.Credential

/**
 * In-memory [AuthRepository] for ViewModel unit tests, so they never touch Firebase.
 *
 * A successful [signInWithGoogle] opens a session for the returned user and a successful [signOut]
 * closes it, so [currentUser] follows the calls like the real repository does.
 *
 * @param signedInUser The account of the session restored at launch, `null` when signed out.
 */
class FakeAuthRepository(private var signedInUser: AuthUser? = null) : AuthRepository {

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
    return signInResult.onSuccess { signedInUser = it }
  }

  override fun currentUser(): AuthUser? = signedInUser

  override fun signOut(): Result<Unit> {
    signOutCalls++
    return signOutResult.onSuccess { signedInUser = null }
  }
}
