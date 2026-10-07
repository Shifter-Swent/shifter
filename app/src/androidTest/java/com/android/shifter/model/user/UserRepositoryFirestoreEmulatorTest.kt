// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.shifter.firebase.FirestoreEmulator
import com.google.firebase.firestore.FirebaseFirestoreException
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.DISPLAY_NAME
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.LOCATION_SHARING_ENABLED
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs [UserRepositoryFirestore] against the Firebase emulators, with `firestore.rules`. */
@RunWith(AndroidJUnit4::class)
class UserRepositoryFirestoreEmulatorTest {

  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val repository = UserRepositoryFirestore(db)
  private lateinit var uid: String

  @Before
  fun signIn() = runTest {
    // A fresh anonymous account per test, so tests never share a profile.
    uid = auth.signInAnonymously().await().user!!.uid
  }

  @After fun signOut() = auth.signOut()

  @Test
  fun getOrCreateUser_createsProfileOnFirstSignIn() = runTest {
    assertNull(repository.getUser(uid))

    val created = repository.getOrCreateUser(uid, "Ada", "ada@example.com")

    assertEquals(User(uid, "Ada", "ada@example.com"), created)
    val stored = repository.getUser(uid)!!
    assertEquals(created, stored.copy(createdAt = null))
    assertNotNull(stored.createdAt)
  }

  @Test
  fun getOrCreateUser_returnsExistingProfileWithoutOverwritingIt() = runTest {
    repository.getOrCreateUser(uid, "Ada", "ada@example.com")
    val createdAt = repository.getUser(uid)!!.createdAt
    db.collection(USERS_COLLECTION)
        .document(uid)
        .update(mapOf(DISPLAY_NAME to "Ada L.", LOCATION_SHARING_ENABLED to true))
        .await()

    val user = repository.getOrCreateUser(uid, "Ada", "ada@example.com")

    assertEquals("Ada L.", user.displayName)
    assertTrue(user.locationSharingEnabled)
    assertEquals(createdAt, user.createdAt)
  }

  @Test
  fun rules_forbidAccessingAnotherUsersProfile() = runTest {
    val thrown = runCatching {
      repository.getOrCreateUser("someone-else", "Eve", "eve@example.com")
    }
        .exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.PermissionDenied)
  }

  @Test
  fun rules_forbidCreatingProfileWithLocationSharingAlreadyEnabled() = runTest {
    val fields = UserRepositoryFirestore.newUserFields("Ada", "ada@example.com").toMutableMap()
    fields[LOCATION_SHARING_ENABLED] = true
    try {
      db.collection(USERS_COLLECTION).document(uid).set(fields).await()
      fail("Expected PERMISSION_DENIED")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }
}
