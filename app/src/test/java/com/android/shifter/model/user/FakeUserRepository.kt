// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

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

  override suspend fun getOrCreateUser(uid: String, displayName: String, email: String): User {
    failure?.let { throw it }
    return users.getOrPut(uid) {
      User(uid = uid, displayName = displayName, email = email, createdAt = Instant.now())
    }
  }

  /** Like the rules, refuses another mode than the one chosen, and a uid with no profile. */
  override suspend fun chooseMode(uid: String, mode: UserMode) {
    failure?.let { throw it }
    val user = users[uid] ?: throw UserRepositoryException.PermissionDenied()
    if (user.mode != null && user.mode != mode) throw UserRepositoryException.PermissionDenied()
    users[uid] = user.copy(mode = mode)
  }

  override suspend fun getPublicProfile(uid: String): PublicProfile? {
    failure?.let { throw it }
    return users[uid]?.let { PublicProfile(it.uid, it.displayName) }
  }
}
