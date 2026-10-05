// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.shifter.model.user

import com.android.shifter.model.user.UserRepositoryFirestore.Companion.CREATED_AT
import com.android.shifter.model.user.UserRepositoryFirestore.Companion.DISPLAY_NAME
import com.android.shifter.model.user.UserRepositoryFirestore.Companion.EMAIL
import com.android.shifter.model.user.UserRepositoryFirestore.Companion.LOCATION_SHARING_ENABLED
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserRepositoryFirestoreTest {

  private val db = mockk<FirebaseFirestore>()
  private val ref = mockk<DocumentReference>()
  private val snapshot = mockk<DocumentSnapshot>()
  private val transaction = mockk<Transaction>(relaxed = true)
  private val repository = UserRepositoryFirestore(db)

  @Before
  fun setUp() {
    val collection = mockk<CollectionReference>()
    every { db.collection(USERS_COLLECTION) } returns collection
    every { collection.document(UID) } returns ref
    every { ref.get() } returns Tasks.forResult(snapshot)
    every { transaction.get(ref) } returns snapshot
    // Runs the transaction body synchronously against the mocked transaction.
    every { db.runTransaction(any<Transaction.Function<Boolean>>()) } answers
        {
          Tasks.forResult(firstArg<Transaction.Function<Boolean>>().apply(transaction))
        }
  }

  @Test
  fun createUserIfAbsent_createsProfileWhenAbsent() = runTest {
    every { snapshot.exists() } returns false

    assertTrue(repository.createUserIfAbsent(UID, "Ada", "ada@example.com"))

    verify { transaction.set(ref, UserRepositoryFirestore.newUserFields("Ada", "ada@example.com")) }
  }

  @Test
  fun createUserIfAbsent_neverOverwritesExistingProfile() = runTest {
    every { snapshot.exists() } returns true

    assertFalse(repository.createUserIfAbsent(UID, "Ada", "ada@example.com"))

    verify(exactly = 0) { transaction.set(any<DocumentReference>(), any()) }
  }

  @Test
  fun createUserIfAbsent_propagatesFirestoreErrors() = runTest {
    val error = FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    every { db.runTransaction(any<Transaction.Function<Boolean>>()) } returns
        Tasks.forException(error)

    val thrown = runCatching { repository.createUserIfAbsent(UID, "Ada", "a@b.c") }
    assertEquals(error, thrown.exceptionOrNull())
  }

  @Test
  fun getUser_returnsNullWhenDocumentMissing() = runTest {
    every { snapshot.data } returns null

    assertNull(repository.getUser(UID))
  }

  @Test
  fun getUser_returnsMappedUserWhenDocumentExists() = runTest {
    every { snapshot.data } returns mapOf(DISPLAY_NAME to "Ada", EMAIL to "ada@example.com")

    assertEquals(User(UID, "Ada", "ada@example.com"), repository.getUser(UID))
  }

  @Test
  fun newUserFields_containsSpecFieldsWithLocationSharingDisabled() {
    val fields = UserRepositoryFirestore.newUserFields("Ada", "ada@example.com")

    assertEquals(setOf(DISPLAY_NAME, EMAIL, LOCATION_SHARING_ENABLED, CREATED_AT), fields.keys)
    assertEquals("Ada", fields[DISPLAY_NAME])
    assertEquals("ada@example.com", fields[EMAIL])
    assertEquals(false, fields[LOCATION_SHARING_ENABLED])
    assertEquals(FieldValue.serverTimestamp(), fields[CREATED_AT])
  }

  @Test
  fun userFromFirestore_mapsAllFields() {
    val date = Date(1_700_000_000_000)
    val data =
        mapOf(
            DISPLAY_NAME to "Ada",
            EMAIL to "ada@example.com",
            LOCATION_SHARING_ENABLED to true,
            CREATED_AT to Timestamp(date),
        )

    val user = UserRepositoryFirestore.userFromFirestore(UID, data)

    assertEquals(User(UID, "Ada", "ada@example.com", true, date), user)
  }

  @Test
  fun userFromFirestore_usesDefaultsForMissingOrMistypedFields() {
    val user =
        UserRepositoryFirestore.userFromFirestore(UID, mapOf(LOCATION_SHARING_ENABLED to "yes"))

    assertEquals(User(UID, "", "", false, null), user)
  }

  private companion object {
    const val UID = "uid-1"
  }
}
