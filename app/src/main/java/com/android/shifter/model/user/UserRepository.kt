// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
   * [mode] is the mode of the sign-in screen used. It is saved on a new profile, and on an existing
   * one without a mode, but never replaces a mode already set: only [setMode] changes it. When
   * `null`, the mode is left as it is.
   *
   * A newly created profile has [User.createdAt] set to `null`, since the server sets the date.
   */
  suspend fun getOrCreateUser(
      uid: String,
      displayName: String,
      email: String,
      mode: UserMode? = null,
  ): User

  /**
   * Sets [mode] as how [uid] uses Shifter, replacing the current one, on a profile created by
   * [getOrCreateUser]. Throws [UserRepositoryException.PermissionDenied] when there is no profile.
   *
   * Offline, the mode is saved locally at once but the call only returns once the device
   * reconnects: a caller should stop waiting after a timeout, as for the other offline writes.
   */
  suspend fun setMode(uid: String, mode: UserMode)

  /**
   * Returns the public profile of [uid], or `null` if it does not exist yet. Unlike [getUser], it
   * works for any user, not only the signed-in one: it is how other people's names are shown.
   */
  suspend fun getPublicProfile(uid: String): PublicProfile?
}

/**
 * The display names of [userIds], read with one [UserRepository.getPublicProfile] per user, all in
 * parallel: `publicProfiles` cannot be listed, so a single query is not an option.
 *
 * A missing profile or a blank name maps to `null`. A profile that cannot be read
 * ([UserRepositoryException]) leaves its user out of the map without failing the others:
 * `names[id]` is `null` in both cases, and `id !in names` tells a failed read apart.
 */
suspend fun UserRepository.displayNames(userIds: Collection<String>): Map<String, String?> =
    coroutineScope {
      userIds
          .distinct()
          .map { id ->
            async {
              try {
                id to getPublicProfile(id)?.displayName?.takeIf { it.isNotBlank() }
              } catch (e: UserRepositoryException) {
                null
              }
            }
          }
          .awaitAll()
          .filterNotNull()
          .toMap()
    }
