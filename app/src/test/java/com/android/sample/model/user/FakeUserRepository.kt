// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import java.util.Date

/** In-memory [UserRepository] for ViewModel unit tests. */
class FakeUserRepository(initialUsers: List<User> = emptyList()) : UserRepository {

  val users: MutableMap<String, User> = initialUsers.associateBy { it.uid }.toMutableMap()

  override suspend fun getUser(uid: String): User? = users[uid]

  override suspend fun createUserIfAbsent(
      uid: String,
      displayName: String,
      email: String,
  ): Boolean {
    if (uid in users) return false
    users[uid] = User(uid = uid, displayName = displayName, email = email, createdAt = Date())
    return true
  }
}
