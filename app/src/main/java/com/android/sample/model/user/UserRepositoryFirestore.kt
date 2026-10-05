// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

const val USERS_COLLECTION = "users"

/** [UserRepository] backed by the Firestore collection `users/{uid}`. */
class UserRepositoryFirestore(private val db: FirebaseFirestore) : UserRepository {

  override suspend fun getUser(uid: String): User? {
    val snapshot = db.collection(USERS_COLLECTION).document(uid).get().await()
    return snapshot.data?.let { userFromFirestore(uid, it) }
  }

  override suspend fun createUserIfAbsent(
      uid: String,
      displayName: String,
      email: String,
  ): Boolean {
    val ref = db.collection(USERS_COLLECTION).document(uid)
    // A transaction makes check-then-create atomic, so concurrent sign-ins cannot both create.
    return db.runTransaction { transaction ->
          if (transaction.get(ref).exists()) {
            false
          } else {
            transaction.set(ref, newUserFields(displayName, email))
            true
          }
        }
        .await()
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

    /** Maps a `users/{uid}` document to a [User], with safe defaults for missing fields. */
    fun userFromFirestore(uid: String, data: Map<String, Any?>): User =
        User(
            uid = uid,
            displayName = data[DISPLAY_NAME] as? String ?: "",
            email = data[EMAIL] as? String ?: "",
            locationSharingEnabled = data[LOCATION_SHARING_ENABLED] as? Boolean ?: false,
            createdAt = (data[CREATED_AT] as? Timestamp)?.toDate(),
        )
  }
}
