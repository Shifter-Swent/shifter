// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestoreException
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.DISPLAY_NAME
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.LOCATION_SHARING_ENABLED
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the Firebase emulators, with the rules of `firestore.rules`. */
@RunWith(AndroidJUnit4::class)
class UserRepositoryFirestoreTest {

  private val auth = FirebaseEmulator.auth
  private val db = FirebaseEmulator.firestore
  private val repository = UserRepositoryFirestore(db)
  private lateinit var uid: String

  @Before
  fun signIn() = runTest {
    // A fresh anonymous account per test, so tests never share a profile.
    uid = auth.signInAnonymously().await().user!!.uid
  }

  @After fun signOut() = auth.signOut()

  @Test
  fun createUserIfAbsent_createsProfileOnFirstSignIn() = runTest {
    assertNull(repository.getUser(uid))

    assertTrue(repository.createUserIfAbsent(uid, "Ada", "ada@example.com"))

    val user = repository.getUser(uid)
    assertNotNull(user)
    assertEquals("Ada", user!!.displayName)
    assertEquals("ada@example.com", user.email)
    assertFalse(user.locationSharingEnabled)
    assertNotNull(user.createdAt)
  }

  @Test
  fun createUserIfAbsent_neverOverwritesExistingProfile() = runTest {
    repository.createUserIfAbsent(uid, "Ada", "ada@example.com")
    val created = repository.getUser(uid)!!
    db.collection(USERS_COLLECTION)
        .document(uid)
        .update(mapOf(DISPLAY_NAME to "Ada L.", LOCATION_SHARING_ENABLED to true))
        .await()

    assertFalse(repository.createUserIfAbsent(uid, "Ada", "ada@example.com"))

    val user = repository.getUser(uid)!!
    assertEquals("Ada L.", user.displayName)
    assertTrue(user.locationSharingEnabled)
    assertEquals(created.createdAt, user.createdAt)
  }

  @Test
  fun rules_forbidCreatingAnotherUsersProfile() = runTest {
    try {
      repository.createUserIfAbsent("someone-else", "Eve", "eve@example.com")
      fail("Expected PERMISSION_DENIED")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
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
