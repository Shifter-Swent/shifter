// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.shifter.model.user

/** Access to user profiles. ViewModels depend on this interface, never on an implementation. */
interface UserRepository {

  /** Returns the profile of [uid], or `null` if it does not exist. */
  suspend fun getUser(uid: String): User?

  /**
   * Creates the profile of [uid] if it does not exist yet. Call it after every successful sign-in:
   * an existing profile is never overwritten.
   *
   * @return `true` if the profile was created, `false` if it already existed.
   */
  suspend fun createUserIfAbsent(uid: String, displayName: String, email: String): Boolean
}
