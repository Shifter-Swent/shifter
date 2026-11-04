// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.swent.shifter.model.firestore.optionalInstant
import com.swent.shifter.model.firestore.optionalString
import com.swent.shifter.model.firestore.requireBoolean
import com.swent.shifter.model.firestore.requireString
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
                transaction.set(ref, newUserFields(displayName, email, mode))
                if (!hasPublicProfile) transaction.set(publicRef, publicProfileFields(displayName))
                return@runTransaction Result.success(
                    User(uid = uid, displayName = displayName, email = email, mode = mode)
                )
              }
              // Mapped before writing anything: a malformed profile gets neither a mode nor a
              // public profile, so a sign-in that fails leaves nothing behind. Only this pure call
              // is guarded, as an IllegalStateException caught around an await() could be a
              // coroutine cancellation.
              val user =
                  try {
                    userFromFirestore(stored)
                  } catch (e: IllegalStateException) {
                    return@runTransaction Result.failure(e)
                  }
              // The rules require the public name to be the private one.
              if (!hasPublicProfile)
                  transaction.set(publicRef, publicProfileFields(user.displayName))
              if (mode != null && stored.get(MODE) == null) {
                // A profile from before modes: it takes the mode of this sign-in. One already
                // set, even unknown to this version, is never overwritten at sign-in.
                transaction.update(ref, mapOf(MODE to mode.name))
                Result.success(user.copy(mode = mode))
              } else {
                Result.success(user)
              }
            }
            .await()
    profile.getOrElse { throw UserRepositoryException.Unknown(it) }
  }

  override suspend fun setMode(uid: String, mode: UserMode): Unit = translatingErrors {
    // An update, not a set: the rules refuse it when there is no profile, rather than creating one.
    db.collection(USERS_COLLECTION).document(uid).update(MODE, mode.name).await()
  }

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

    /** Translates a Firestore error into the [UserRepositoryException] callers handle. */
    fun toRepositoryException(e: FirebaseFirestoreException): UserRepositoryException =
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
