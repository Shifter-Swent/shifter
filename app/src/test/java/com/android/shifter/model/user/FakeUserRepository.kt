// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import java.util.Date

/**
 * In-memory [UserRepository] for ViewModel unit tests. Set [failure] to make every call throw it.
 */
class FakeUserRepository(initialUsers: List<User> = emptyList()) : UserRepository {

  val users: MutableMap<String, User> = initialUsers.associateBy { it.uid }.toMutableMap()

  var failure: UserRepositoryException? = null

  override suspend fun getUser(uid: String): User? {
    failure?.let { throw it }
    return users[uid]
  }

  override suspend fun getOrCreateUser(uid: String, displayName: String, email: String): User {
    failure?.let { throw it }
    return users.getOrPut(uid) {
      User(uid = uid, displayName = displayName, email = email, createdAt = Date())
    }
  }
}
