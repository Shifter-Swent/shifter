// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.swent.shifter.model.firestore.optionalInstant
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

  override suspend fun getOrCreateUser(uid: String, displayName: String, email: String): User =
      translatingErrors {
        val ref = db.collection(USERS_COLLECTION).document(uid)
        val publicRef = db.collection(PUBLIC_PROFILES_COLLECTION).document(uid)
        // A transaction makes check-then-create atomic, so concurrent sign-ins cannot both create.
        // It returns the existing profile, mapped once the transaction is over.
        val existing =
            db.runTransaction { transaction ->
                  // A transaction reads everything before it writes.
                  val stored = transaction.get(ref).takeIf { it.exists() }
                  val hasPublicProfile = transaction.get(publicRef).exists()
                  if (stored == null) transaction.set(ref, newUserFields(displayName, email))
                  // The rules require the public name to be the private one. A malformed private
                  // profile gets no public profile: mapping it below reports it anyway.
                  val name =
                      if (stored == null) displayName else stored.get(DISPLAY_NAME) as? String
                  if (!hasPublicProfile && name != null) {
                    transaction.set(publicRef, publicProfileFields(name))
                  }
                  stored
                }
                .await()
        existing?.let { userFromFirestoreOrThrow(it) }
            ?: User(uid = uid, displayName = displayName, email = email)
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

    /** Fields of a newly created profile. Location sharing starts disabled until opt-in. */
    fun newUserFields(displayName: String, email: String): Map<String, Any> =
        mapOf(
            DISPLAY_NAME to displayName,
            EMAIL to email,
            LOCATION_SHARING_ENABLED to false,
            CREATED_AT to FieldValue.serverTimestamp(),
        )

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
     * [User.createdAt] may be absent, while the server timestamp is still pending.
     */
    fun userFromFirestore(snapshot: DocumentSnapshot): User =
        User(
            uid = snapshot.id,
            displayName = snapshot.requireString(ENTITY, DISPLAY_NAME),
            email = snapshot.requireString(ENTITY, EMAIL),
            locationSharingEnabled = snapshot.requireBoolean(ENTITY, LOCATION_SHARING_ENABLED),
            createdAt = snapshot.optionalInstant(ENTITY, CREATED_AT),
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
