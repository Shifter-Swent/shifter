// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.DISPLAY_NAME
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.LOCATION_SHARING_ENABLED
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.MODE
import kotlinx.coroutines.tasks.await
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

/** Runs [UserRepositoryFirestore] against the Firebase emulators, with `firestore.rules`. */
@RunWith(AndroidJUnit4::class)
class UserRepositoryFirestoreEmulatorTest {

  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val repository = UserRepositoryFirestore(db)
  private lateinit var uid: String

  @Before
  fun signIn() = emulatorTest {
    // A fresh anonymous account per test, so tests never share a profile.
    uid = auth.signInForFirestore { auth.signInAnonymously().await() }.user!!.uid
  }

  @After fun signOut() = auth.signOut()

  @Test
  fun getOrCreateUser_createsProfileOnFirstSignIn() = emulatorTest {
    assertNull(step("read profile") { repository.getUser(uid) })

    val created =
        step("create profile") { repository.getOrCreateUser(uid, "Ada", "ada@example.com") }

    assertEquals(User(uid, "Ada", "ada@example.com"), created)
    val stored = step("read profile") { repository.getUser(uid) }!!
    assertEquals(created, stored.copy(createdAt = null))
    assertNotNull(stored.createdAt)
  }

  @Test
  fun getOrCreateUser_returnsExistingProfileWithoutOverwritingIt() = emulatorTest {
    step("create profile") { repository.getOrCreateUser(uid, "Ada", "ada@example.com") }
    val createdAt = step("read profile") { repository.getUser(uid) }!!.createdAt
    // A new name goes to both profiles in one commit, as the rules require.
    step("rename") {
      db.batch()
          .update(
              db.collection(USERS_COLLECTION).document(uid),
              mapOf(DISPLAY_NAME to "Ada L.", LOCATION_SHARING_ENABLED to true),
          )
          .update(publicProfile(uid), DISPLAY_NAME, "Ada L.")
          .commit()
          .await()
    }

    val user = step("get profile") { repository.getOrCreateUser(uid, "Ada", "ada@example.com") }

    assertEquals("Ada L.", user.displayName)
    assertTrue(user.locationSharingEnabled)
    assertEquals(createdAt, user.createdAt)
  }

  @Test
  fun getOrCreateUser_createsThePublicProfileThatOtherUsersRead() = emulatorTest {
    step("create profile") { repository.getOrCreateUser(uid, "Ada", "ada@example.com") }

    signInAsAnotherUser()

    assertEquals(
        PublicProfile(uid, "Ada"),
        step("read public profile") { repository.getPublicProfile(uid) },
    )
    assertNull(step("read public profile") { repository.getPublicProfile("nobody") })
  }

  @Test
  fun getOrCreateUser_givesAProfileFromBeforePublicProfilesItsPublicProfile() = emulatorTest {
    step("write profile") {
      db.collection(USERS_COLLECTION)
          .document(uid)
          .set(UserRepositoryFirestore.newUserFields("Ada", "ada@example.com"))
          .await()
    }
    assertNull(step("read public profile") { repository.getPublicProfile(uid) })

    step("get profile") { repository.getOrCreateUser(uid, "Ada", "ada@example.com") }

    assertEquals(
        PublicProfile(uid, "Ada"),
        step("read public profile") { repository.getPublicProfile(uid) },
    )
  }

  @Test
  fun rules_forbidListingPublicProfiles() = emulatorTest {
    step("create profile") { repository.getOrCreateUser(uid, "Ada", "ada@example.com") }

    assertDenied { db.collection(PUBLIC_PROFILES_COLLECTION).get().await() }
  }

  @Test
  fun rules_keepThePublicProfileTheNameOfItsOwner() = emulatorTest {
    step("write profile") {
      db.collection(USERS_COLLECTION)
          .document(uid)
          .set(UserRepositoryFirestore.newUserFields("Ada", "ada@example.com"))
          .await()
    }

    assertDenied { publicProfile(uid).set(mapOf(DISPLAY_NAME to "Eve")).await() }
    assertDenied { publicProfile(uid).set(mapOf(DISPLAY_NAME to "Ada", "email" to "x")).await() }
    assertDenied { publicProfile("someone-else").set(mapOf(DISPLAY_NAME to "Ada")).await() }
    step("write public profile") { publicProfile(uid).set(mapOf(DISPLAY_NAME to "Ada")).await() }
    // Renaming one profile without the other would make them disagree.
    assertDenied {
      db.collection(USERS_COLLECTION).document(uid).update(DISPLAY_NAME, "Ada L.").await()
    }
    assertDenied { publicProfile(uid).update(DISPLAY_NAME, "Ada L.").await() }
    assertDenied { publicProfile(uid).delete().await() }
    assertEquals(
        PublicProfile(uid, "Ada"),
        step("read public profile") { repository.getPublicProfile(uid) },
    )
  }

  @Test
  fun rules_forbidAccessingAnotherUsersProfile() = emulatorTest {
    val thrown =
        step("get another profile") {
          runCatching { repository.getOrCreateUser("someone-else", "Eve", "eve@example.com") }
              .exceptionOrNull()
        }

    assertTrue(thrown is UserRepositoryException.PermissionDenied)
  }

  @Test
  fun rules_forbidCreatingProfileWithLocationSharingAlreadyEnabled() = emulatorTest {
    val fields = UserRepositoryFirestore.newUserFields("Ada", "ada@example.com").toMutableMap()
    fields[LOCATION_SHARING_ENABLED] = true
    try {
      step("write profile") { db.collection(USERS_COLLECTION).document(uid).set(fields).await() }
      fail("Expected PERMISSION_DENIED")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }

  @Test
  fun getOrCreateUser_withMode_createsTheProfileWithIt() = emulatorTest {
    step("create profile") {
      repository.getOrCreateUser(uid, "Ada", "ada@example.com", UserMode.ORGANIZER)
    }

    assertEquals(UserMode.ORGANIZER, step("read profile") { repository.getUser(uid) }?.mode)
  }

  @Test
  fun getOrCreateUser_withMode_savesItOnlyOnAProfileWithout() = emulatorTest {
    // A profile from before modes.
    step("create profile") { repository.getOrCreateUser(uid, "Ada", "ada@example.com") }

    step("sign in as organizer") {
      repository.getOrCreateUser(uid, "Ada", "ada@example.com", UserMode.ORGANIZER)
    }
    val user =
        step("sign in as volunteer") {
          repository.getOrCreateUser(uid, "Ada", "ada@example.com", UserMode.VOLUNTEER)
        }

    assertEquals(UserMode.ORGANIZER, user.mode)
    assertEquals(UserMode.ORGANIZER, step("read profile") { repository.getUser(uid) }?.mode)
  }

  @Test
  fun getOrCreateUser_withModeOnAMalformedProfile_savesNoMode() = emulatorTest {
    // A profile without an email: no client may create it, but the rules let its owner add a mode.
    step("write malformed profile") {
      FirestoreEmulatorAdmin.createDocument(
          "$USERS_COLLECTION/$uid",
          mapOf(DISPLAY_NAME to "Ada", LOCATION_SHARING_ENABLED to false),
      )
    }
    val profile = db.collection(USERS_COLLECTION).document(uid)

    val thrown =
        step("sign in as organizer") {
          runCatching {
            repository.getOrCreateUser(uid, "Ada", "ada@example.com", UserMode.ORGANIZER)
          }
              .exceptionOrNull()
        }

    assertTrue(thrown is UserRepositoryException.Unknown)
    val stored = step("read raw profile") { profile.get().await() }
    assertNull(stored.get(MODE))
    assertFalse(step("read public profile") { publicProfile(uid).get().await() }.exists())
  }

  @Test
  fun setMode_changesTheMode() = emulatorTest {
    step("create profile") {
      repository.getOrCreateUser(uid, "Ada", "ada@example.com", UserMode.VOLUNTEER)
    }

    step("set mode") { repository.setMode(uid, UserMode.ORGANIZER) }
    assertEquals(UserMode.ORGANIZER, step("read profile") { repository.getUser(uid) }?.mode)
    step("set mode back") { repository.setMode(uid, UserMode.VOLUNTEER) }
    assertEquals(UserMode.VOLUNTEER, step("read profile") { repository.getUser(uid) }?.mode)
  }

  @Test
  fun setMode_withoutProfile_throwsPermissionDenied() = emulatorTest {
    val thrown =
        step("set mode") {
          runCatching { repository.setMode(uid, UserMode.VOLUNTEER) }.exceptionOrNull()
        }

    assertTrue(thrown is UserRepositoryException.PermissionDenied)
    assertNull(step("read profile") { repository.getUser(uid) })
  }

  @Test
  fun rules_forbidAnUnknownOrRemovedMode() = emulatorTest {
    val profile = db.collection(USERS_COLLECTION).document(uid)
    val withUnknownMode =
        UserRepositoryFirestore.newUserFields("Ada", "ada@example.com") + (MODE to "ADMIN")
    assertDenied { profile.set(withUnknownMode).await() }
    step("create profile") {
      repository.getOrCreateUser(uid, "Ada", "ada@example.com", UserMode.VOLUNTEER)
    }

    assertDenied { profile.update(MODE, "ADMIN").await() }
    assertDenied { profile.update(MODE, 1).await() }
    assertDenied { profile.update(MODE, FieldValue.delete()).await() }
    assertEquals(UserMode.VOLUNTEER, step("read profile") { repository.getUser(uid) }?.mode)
  }

  @Test
  fun rules_forbidChangingAnotherUsersMode() = emulatorTest {
    val owner = uid
    step("create profile") {
      repository.getOrCreateUser(owner, "Ada", "ada@example.com", UserMode.VOLUNTEER)
    }
    signInAsAnotherUser()

    val thrown =
        step("set another mode") {
          runCatching { repository.setMode(owner, UserMode.ORGANIZER) }.exceptionOrNull()
        }

    assertTrue(thrown is UserRepositoryException.PermissionDenied)
  }

  private fun publicProfile(uid: String) = db.collection(PUBLIC_PROFILES_COLLECTION).document(uid)

  private suspend fun signInAsAnotherUser() {
    auth.signOut()
    auth.signInForFirestore { auth.signInAnonymously().await() }
  }

  private suspend fun assertDenied(block: suspend () -> Unit) {
    val failure = step("denied call") { runCatching { block() }.exceptionOrNull() }
    assertTrue(
        "expected a permission denial, but was: $failure",
        (failure as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }
}
