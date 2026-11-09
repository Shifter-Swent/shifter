// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import com.swent.shifter.model.team.Team
import java.time.Instant

/**
 * In-memory [UserRepository] for ViewModel unit tests. Set [failure] to make every call throw it.
 *
 * Every user in [users] has a public profile with their name, as after signing in.
 */
class FakeUserRepository(initialUsers: List<User> = emptyList()) : UserRepository {

  val users: MutableMap<String, User> = initialUsers.associateBy { it.uid }.toMutableMap()

  var failure: Exception? = null

  override suspend fun getUser(uid: String): User? {
    failure?.let { throw it }
    return users[uid]
  }

  override suspend fun getOrCreateUser(
      uid: String,
      displayName: String,
      email: String,
      mode: UserMode?,
  ): User {
    failure?.let { throw it }
    val user =
        users[uid]
            ?: User(uid = uid, displayName = displayName, email = email, createdAt = Instant.now())
    // Like the real repository, a mode already set is never replaced at sign-in.
    return user.copy(mode = user.mode ?: mode).also { users[uid] = it }
  }

  /** Like the rules, refuses a uid with no profile. */
  override suspend fun setMode(uid: String, mode: UserMode) {
    failure?.let { throw it }
    val user = users[uid] ?: throw UserRepositoryException.PermissionDenied()
    users[uid] = user.copy(mode = mode)
  }

  /** Like the rules, refuses a uid with no profile. */
  override suspend fun setLocationSharing(
      uid: String,
      enabled: Boolean,
      sharedIn: Collection<Team>,
  ) {
    failure?.let { throw it }
    val user = users[uid] ?: throw UserRepositoryException.PermissionDenied()
    users[uid] = user.copy(locationSharingEnabled = enabled)
  }

  override suspend fun getPublicProfile(uid: String): PublicProfile? {
    failure?.let { throw it }
    return users[uid]?.let { PublicProfile(it.uid, it.displayName) }
  }
}
