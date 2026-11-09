// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import com.swent.shifter.model.team.Team
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

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
   * Turns [uid]'s consent to share their location with their managers on or off, on a profile
   * created by [getOrCreateUser]. Throws [UserRepositoryException.PermissionDenied] when there is
   * no profile.
   *
   * Turning it off also deletes the position [uid] shared in each team of [sharedIn], **in the same
   * commit**: the rules let a manager read a position until it is deleted, whatever the consent
   * says, so the manager stops seeing it exactly when the server receives the new setting, and
   * never sees one without the other. [sharedIn] is ignored when turning it on.
   *
   * Offline, the change is queued and sent as a whole when the device reconnects, also if the app
   * is closed meanwhile; until then the manager still sees the last position. It is visible at once
   * on this device only if the profile is already cached, for instance read with [getUser]: without
   * a local copy, there is nothing to apply it to until the server answers. The call only returns
   * once the server has it, so a caller should stop waiting after a timeout, as for [setMode].
   */
  suspend fun setLocationSharing(uid: String, enabled: Boolean, sharedIn: Collection<Team>)

  /**
   * Emits [uid]'s current [locationSharingEnabled][User.locationSharingEnabled] consent, then again
   * each time it changes, so a live-location session stops collecting GPS as soon as the volunteer
   * turns sharing off.
   *
   * It is the reactive counterpart of the one-shot [getUser], for the one field a publishing
   * session must follow while it runs. Only the signed-in user may observe their own profile, as
   * for [getUser].
   *
   * The contract a publisher relies on:
   * - **No placeholder while it loads.** The flow emits nothing until the first value is known, so
   *   it never reports a default `false` that would delete a valid position, nor a `true` that
   *   would authorize GPS before the real setting is read. A profile that does not exist yet reads
   *   as `false`, consent being opt-in.
   * - **Local writes first.** An implementation backed by a snapshot listener emits a change the
   *   moment it is written on this device, before the server confirms it, so turning sharing off
   *   stops GPS at once even while the write is still queued offline. It does **not** wait for
   *   [setLocationSharing] to return.
   * - **A failure is reported, never swallowed into a stale `true`.** The flow fails with a
   *   [UserRepositoryException] when the observation cannot continue, rather than going quiet on
   *   the last value read: the caller decides what a lost observation means, and must not let it
   *   keep authorizing GPS. See `sharingConsent` for the fail-safe the app applies.
   */
  fun observeLocationSharing(uid: String): Flow<Boolean>

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
