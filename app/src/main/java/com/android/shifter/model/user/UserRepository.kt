// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

/**
 * Access to user profiles. ViewModels depend on this interface, never on an implementation.
 *
 * Every method throws a [UserRepositoryException] when the operation fails.
 */
interface UserRepository {

  /** Returns the profile of [uid], or `null` if it does not exist. */
  suspend fun getUser(uid: String): User?

  /**
   * Returns the profile of [uid], creating it first if it does not exist yet. Call it after every
   * successful sign-in: an existing profile is returned as is and never overwritten.
   *
   * Its [PublicProfile] is created at the same time, with the same name, when it is missing: also
   * for a profile created before public profiles existed.
   *
   * A newly created profile has [User.createdAt] set to `null`, since the server sets the date.
   */
  suspend fun getOrCreateUser(uid: String, displayName: String, email: String): User

  /**
   * Returns the public profile of [uid], or `null` if it does not exist yet. Unlike [getUser], it
   * works for any user, not only the signed-in one: it is how other people's names are shown.
   */
  suspend fun getPublicProfile(uid: String): PublicProfile?
}
