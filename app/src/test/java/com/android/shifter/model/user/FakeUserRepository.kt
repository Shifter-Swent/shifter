// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import com.swent.shifter.model.team.Team
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * In-memory [UserRepository] for ViewModel unit tests. Set [failure] to make every call throw it.
 *
 * Every user in [users] has a public profile with their name, as after signing in.
 *
 * [observeLocationSharing] is live: a test changes the consent with [setLocationSharing] and the
 * flow emits the new value, as a snapshot listener would, or set [observeFailure] to make it fail
 * like a lost observation.
 */
class FakeUserRepository(initialUsers: List<User> = emptyList()) : UserRepository {

  val users: MutableMap<String, User> = initialUsers.associateBy { it.uid }.toMutableMap()

  var failure: Exception? = null

  /** When set, [observeLocationSharing] fails with it, to test the fail-safe. */
  var observeFailure: UserRepositoryException? = null

  /** Per-user consent streams, so [observeLocationSharing] reflects [setLocationSharing] live. */
  private val consentFlows = mutableMapOf<String, MutableStateFlow<Boolean>>()

  private fun consentFlow(uid: String): MutableStateFlow<Boolean> =
      consentFlows.getOrPut(uid) { MutableStateFlow(users[uid]?.locationSharingEnabled ?: false) }

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

  /** The teams whose position the last [setLocationSharing] turning sharing off deleted. */
  var positionsRemovedFrom: List<Team> = emptyList()

  /** When set, [setLocationSharing] waits for it after saving, as Firestore does offline. */
  var unconfirmedWrite: CompletableDeferred<Unit>? = null

  /** Like the rules, refuses a uid with no profile. */
  override suspend fun setLocationSharing(
      uid: String,
      enabled: Boolean,
      sharedIn: Collection<Team>,
  ) {
    failure?.let { throw it }
    val user = users[uid] ?: throw UserRepositoryException.PermissionDenied()
    users[uid] = user.copy(locationSharingEnabled = enabled)
    if (!enabled) positionsRemovedFrom = sharedIn.toList()
    consentFlow(uid).value = enabled
    unconfirmedWrite?.await()
  }

  override fun observeLocationSharing(uid: String): Flow<Boolean> = flow {
    observeFailure?.let { throw it }
    emitAll(consentFlow(uid))
  }

  override suspend fun getPublicProfile(uid: String): PublicProfile? {
    failure?.let { throw it }
    return users[uid]?.let { PublicProfile(it.uid, it.displayName) }
  }
}
