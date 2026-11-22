// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Transaction
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.firestore.invalidField
import com.swent.shifter.model.firestore.optionalInstant
import com.swent.shifter.model.firestore.optionalString
import com.swent.shifter.model.firestore.requireBoolean
import com.swent.shifter.model.firestore.requireString
import com.swent.shifter.model.location.LiveLocationSchema
import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamSchema
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

const val USERS_COLLECTION = "users"

const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"

/** Names this entity in the failure a malformed document raises. */
private const val ENTITY = "User"

private const val PUBLIC_PROFILE_ENTITY = "PublicProfile"

/**
 * [UserRepository] backed by the Firestore collections `users/{uid}`, which only its owner reads,
 * and `publicProfiles/{uid}`, which any signed-in user may read one at a time.
 */
class UserRepositoryFirestore(private val db: FirebaseFirestore) : UserRepository {

  override suspend fun getUser(uid: String): User? = translatingErrors {
    db.collection(USERS_COLLECTION)
        .document(uid)
        .get()
        .await()
        .takeIf { it.exists() }
        ?.let { userFromFirestoreOrThrow(it) }
  }

  override suspend fun getOrCreateUser(
      uid: String,
      displayName: String,
      email: String,
      mode: UserMode?,
  ): User = translatingErrors {
    val ref = db.collection(USERS_COLLECTION).document(uid)
    val publicRef = db.collection(PUBLIC_PROFILES_COLLECTION).document(uid)
    // A transaction makes check-then-create atomic, so concurrent sign-ins cannot both create.
    // It returns the profile, or why the stored one could not be mapped.
    val profile =
        db.runTransaction { transaction ->
              // A transaction reads everything before it writes.
              val stored = transaction.get(ref).takeIf { it.exists() }
              val hasPublicProfile = transaction.get(publicRef).exists()
              if (stored == null) {
                Result.success(
                    transaction.createUser(
                        ref,
                        publicRef,
                        User(uid = uid, displayName = displayName, email = email, mode = mode),
                        hasPublicProfile,
                    )
                )
              } else {
                transaction.completeStoredUser(ref, publicRef, stored, hasPublicProfile, mode)
              }
            }
            .await()
    profile.getOrElse { throw UserRepositoryException.Unknown(it) }
  }

  /**
   * Writes the profile of a first sign-in for [user] at [ref], and its public profile at
   * [publicRef] unless [hasPublicProfile].
   */
  private fun Transaction.createUser(
      ref: DocumentReference,
      publicRef: DocumentReference,
      user: User,
      hasPublicProfile: Boolean,
  ): User {
    set(ref, newUserFields(user.displayName, user.email, user.mode))
    if (!hasPublicProfile) set(publicRef, publicProfileFields(user.displayName))
    return user
  }

  /**
   * Maps the [stored] profile at [ref], then gives it what it lacks: its public profile at
   * [publicRef] unless [hasPublicProfile], and the [mode] of this sign-in if it has none. Returns
   * the profile, or why the stored one could not be mapped.
   */
  private fun Transaction.completeStoredUser(
      ref: DocumentReference,
      publicRef: DocumentReference,
      stored: DocumentSnapshot,
      hasPublicProfile: Boolean,
      mode: UserMode?,
  ): Result<User> {
    // Mapped before writing anything: a malformed profile gets neither a mode nor a public
    // profile, so a sign-in that fails leaves nothing behind. Only this pure call is guarded, as an
    // IllegalStateException caught around an await() could be a coroutine cancellation.
    val user =
        try {
          userFromFirestore(stored)
        } catch (e: IllegalStateException) {
          return Result.failure(e)
        }
    // The rules require the public name to be the private one.
    if (!hasPublicProfile) set(publicRef, publicProfileFields(user.displayName))
    // A profile from before modes takes the mode of this sign-in. One already set, even unknown to
    // this version, is never overwritten at sign-in.
    if (mode == null || stored.get(MODE) != null) return Result.success(user)
    update(ref, mapOf(MODE to mode.name))
    return Result.success(user.copy(mode = mode))
  }

  override suspend fun setMode(uid: String, mode: UserMode): Unit = translatingErrors {
    // An update, not a set: the rules refuse it when there is no profile, rather than creating one.
    db.collection(USERS_COLLECTION).document(uid).update(MODE, mode.name).await()
  }

  override suspend fun setLocationSharing(
      uid: String,
      enabled: Boolean,
      sharedIn: Collection<Team>,
  ): Unit = translatingErrors {
    val batch = db.batch()
    // An update, not a set: the rules refuse it when there is no profile, rather than creating one.
    batch.update(db.collection(USERS_COLLECTION).document(uid), LOCATION_SHARING_ENABLED, enabled)
    if (!enabled) sharedIn.forEach { batch.delete(liveLocationOf(it, uid)) }
    batch.commit().await()
  }

  /**
   * Where [uid] last shared their position with the manager of [team]. Only the path is taken from
   * the schemas, as in the team repository: this one does not depend on the live location one.
   */
  private fun liveLocationOf(team: Team, uid: String) =
      db.collection(EventSchema.COLLECTION)
          .document(team.eventId)
          .collection(TeamSchema.COLLECTION)
          .document(team.id)
          .collection(LiveLocationSchema.COLLECTION)
          .document(uid)

  /**
   * Listens to `users/{uid}`, so a publishing session follows the consent without polling.
   *
   * Firestore delivers a local write to this listener before the server confirms it (the snapshot
   * then carries `hasPendingWrites`), so turning sharing off through [setLocationSharing] stops GPS
   * at once even while that batch is still queued offline, without waiting for the call to return.
   *
   * The first snapshot is the current value, so the flow emits nothing before the setting is known:
   * no placeholder `false` that would delete a valid position, nor a `true` that would authorize
   * GPS too early. A document that does not exist reads as `false`, consent being opt-in.
   * [distinctUntilChanged] suppresses duplicate consent values: the listener fires on any change to
   * `users/{uid}`, so editing another field such as `mode` (#436) re-delivers the unchanged
   * boolean, which is dropped rather than forwarded as a spurious change. The listener does not
   * request `MetadataChanges.INCLUDE`, so it receives no metadata-only acknowledgement snapshots to
   * filter.
   *
   * A listener error ends the flow with a [UserRepositoryException]; a malformed profile ends it
   * with [UserRepositoryException.Unknown]. The flow never goes quiet on the last value read, so a
   * lost observation cannot keep authorizing GPS; the caller decides what to do with the failure.
   */
  override fun observeLocationSharing(uid: String): Flow<Boolean> = callbackFlow {
    val registration =
        db.collection(USERS_COLLECTION).document(uid).addSnapshotListener { snapshot, error ->
          when {
            error != null -> close(toRepositoryException(error))
            snapshot == null || !snapshot.exists() -> trySend(false)
            else ->
                try {
                  trySend(snapshot.requireBoolean(ENTITY, LOCATION_SHARING_ENABLED))
                } catch (e: IllegalStateException) {
                  close(UserRepositoryException.Unknown(e))
                }
          }
        }
    // Keeps the flow open while collected, then removes the listener so Firestore stops it.
    awaitClose { registration.remove() }
  }
      .distinctUntilChanged()

  override suspend fun setNotificationEnabled(
      uid: String,
      kind: NotificationKind,
      enabled: Boolean,
  ): Unit = translatingErrors {
    // An update of the one key, not of the whole map: a choice made on another phone for another
    // kind is kept. An update, not a set: the rules refuse it when there is no profile.
    db.collection(USERS_COLLECTION)
        .document(uid)
        .update(FieldPath.of(NOTIFICATION_SETTINGS, kind.name), enabled)
        .await()
  }

  /**
   * Listens to `users/{uid}`, as [observeLocationSharing] does: the first snapshot is the current
   * value, a local write is delivered before the server confirms it, and a listener error or a
   * malformed field ends the flow with a [UserRepositoryException]. [distinctUntilChanged] drops
   * the unchanged sets the listener re-delivers when another field of the profile changes.
   *
   * A document that does not exist reads as no kind turned off, but only once the server says so.
   * Offline, without a cached copy of the profile, Firestore delivers a snapshot from the cache
   * that only means "unknown": reading it as "every kind on" would show kinds the user turned off,
   * so it is skipped and the flow waits for the server. The listener asks for metadata changes
   * ([MetadataChanges.INCLUDE]) because the server confirming that the profile is still missing
   * changes no data, only `isFromCache`: without them, that answer would never be delivered and the
   * flow would stay silent.
   */
  override fun observeDisabledNotifications(uid: String): Flow<Set<NotificationKind>> =
      callbackFlow {
        val registration =
            db.collection(USERS_COLLECTION).document(uid).addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->
              when {
                error != null -> close(toRepositoryException(error))
                snapshot != null && !snapshot.exists() && snapshot.metadata.isFromCache -> Unit
                snapshot == null || !snapshot.exists() -> trySend(emptySet())
                else ->
                    try {
                      trySend(disabledNotificationsFrom(snapshot))
                    } catch (e: IllegalStateException) {
                      close(UserRepositoryException.Unknown(e))
                    }
              }
            }
        awaitClose { registration.remove() }
      }
      .distinctUntilChanged()

  override suspend fun getPublicProfile(uid: String): PublicProfile? = translatingErrors {
    db.collection(PUBLIC_PROFILES_COLLECTION)
        .document(uid)
        .get()
        .await()
        .takeIf { it.exists() }
        ?.let { publicProfileFromFirestoreOrThrow(it) }
  }

  companion object {
    const val DISPLAY_NAME = "displayName"
    const val EMAIL = "email"
    const val LOCATION_SHARING_ENABLED = "locationSharingEnabled"
    const val CREATED_AT = "createdAt"
    const val MODE = "mode"
    const val NOTIFICATION_SETTINGS = "notificationSettings"

    /**
     * Fields of a newly created profile, with [mode] when given. Location sharing starts disabled
     * until opt-in.
     */
    fun newUserFields(
        displayName: String,
        email: String,
        mode: UserMode? = null,
    ): Map<String, Any> =
        mapOf(
            DISPLAY_NAME to displayName,
            EMAIL to email,
            LOCATION_SHARING_ENABLED to false,
            CREATED_AT to FieldValue.serverTimestamp(),
        ) + listOfNotNull(mode?.let { MODE to it.name })

    /** Fields of a public profile: only what any signed-in user may see. */
    fun publicProfileFields(displayName: String): Map<String, Any> =
        mapOf(DISPLAY_NAME to displayName)

    /**
     * Maps a `publicProfiles/{uid}` document to a [PublicProfile], taking its uid from the document
     * id. Throws [IllegalStateException] when the name is missing or not a string.
     */
    fun publicProfileFromFirestore(snapshot: DocumentSnapshot): PublicProfile =
        PublicProfile(
            uid = snapshot.id,
            displayName = snapshot.requireString(PUBLIC_PROFILE_ENTITY, DISPLAY_NAME),
        )

    /**
     * Maps a `users/{uid}` document to a [User], taking [User.uid] from the document id.
     *
     * Throws [IllegalStateException] when a field is missing or has an unexpected type, like the
     * other mappers: a malformed profile is a schema problem, not an empty one. Only
     * [User.createdAt] may be absent, while the server timestamp is still pending, and [User.mode],
     * until the user chooses it. A mode this version does not know, added by a later one, is read
     * as `null` rather than failing the whole profile, so an older install can still sign in.
     */
    fun userFromFirestore(snapshot: DocumentSnapshot): User =
        User(
            uid = snapshot.id,
            displayName = snapshot.requireString(ENTITY, DISPLAY_NAME),
            email = snapshot.requireString(ENTITY, EMAIL),
            locationSharingEnabled = snapshot.requireBoolean(ENTITY, LOCATION_SHARING_ENABLED),
            createdAt = snapshot.optionalInstant(ENTITY, CREATED_AT),
            mode =
                snapshot.optionalString(ENTITY, MODE)?.let { name ->
                  UserMode.entries.firstOrNull { it.name == name }
                },
        )

    /**
     * The kinds [snapshot] turns off. [NOTIFICATION_SETTINGS] maps the name of a kind to whether it
     * is on, and a missing map or kind is on. A name this version does not know, added by a later
     * one, is skipped, as for [User.mode], so an older install still reads the others.
     *
     * Throws [IllegalStateException] when the field is not a map, or when a kind this version knows
     * is not a boolean: a malformed choice is a schema problem, not "on".
     */
    private fun disabledNotificationsFrom(snapshot: DocumentSnapshot): Set<NotificationKind> {
      val settings = snapshot.get(NOTIFICATION_SETTINGS) ?: return emptySet()
      if (settings !is Map<*, *>) snapshot.invalidField(ENTITY, NOTIFICATION_SETTINGS)
      return NotificationKind.entries.filterTo(mutableSetOf()) { kind ->
        when (val enabled = settings[kind.name]) {
          null -> false
          is Boolean -> !enabled
          else -> snapshot.invalidField(ENTITY, "$NOTIFICATION_SETTINGS.${kind.name}")
        }
      }
    }

    /** Translates a Firestore error into the [UserRepositoryException] callers handle. */
    internal fun toRepositoryException(e: FirebaseFirestoreException): UserRepositoryException =
        when (e.code) {
          Code.PERMISSION_DENIED,
          Code.UNAUTHENTICATED -> UserRepositoryException.PermissionDenied(e)
          Code.UNAVAILABLE,
          Code.DEADLINE_EXCEEDED -> UserRepositoryException.Unavailable(e)
          else -> UserRepositoryException.Unknown(e)
        }

    private inline fun <T> translatingErrors(block: () -> T): T =
        try {
          block()
        } catch (e: FirebaseFirestoreException) {
          throw toRepositoryException(e)
        }

    /**
     * Maps a stored profile, reporting one that does not match the schema as
     * [UserRepositoryException.Unknown]. The [IllegalStateException] is only caught around this
     * pure call: catching it around an `await()` would also swallow coroutine cancellation, a
     * subclass of it.
     */
    private fun userFromFirestoreOrThrow(snapshot: DocumentSnapshot): User =
        try {
          userFromFirestore(snapshot)
        } catch (e: IllegalStateException) {
          throw UserRepositoryException.Unknown(e)
        }

    /** Like [userFromFirestoreOrThrow], for a public profile. */
    private fun publicProfileFromFirestoreOrThrow(snapshot: DocumentSnapshot): PublicProfile =
        try {
          publicProfileFromFirestore(snapshot)
        } catch (e: IllegalStateException) {
          throw UserRepositoryException.Unknown(e)
        }
  }
}
