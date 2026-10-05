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
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    every { db.runTransaction(any<Transaction.Function<User>>()) } answers
        {
          Tasks.forResult(firstArg<Transaction.Function<User>>().apply(transaction))
        }
  }

  @Test
  fun getOrCreateUser_createsProfileWhenAbsent() = runTest {
    every { snapshot.data } returns null

    val user = repository.getOrCreateUser(UID, "Ada", "ada@example.com")

    assertEquals(User(UID, "Ada", "ada@example.com"), user)
    verify { transaction.set(ref, UserRepositoryFirestore.newUserFields("Ada", "ada@example.com")) }
  }

  @Test
  fun getOrCreateUser_returnsExistingProfileWithoutOverwritingIt() = runTest {
    every { snapshot.data } returns
        mapOf(
            DISPLAY_NAME to "Ada L.",
            EMAIL to "ada@example.com",
            LOCATION_SHARING_ENABLED to true,
        )

    val user = repository.getOrCreateUser(UID, "Ada", "ada@example.com")

    assertEquals(User(UID, "Ada L.", "ada@example.com", true), user)
    verify(exactly = 0) { transaction.set(any<DocumentReference>(), any()) }
  }

  @Test
  fun getOrCreateUser_translatesFirestoreErrors() = runTest {
    val error = FirebaseFirestoreException("offline", Code.UNAVAILABLE)
    every { db.runTransaction(any<Transaction.Function<User>>()) } returns Tasks.forException(error)

    val thrown = runCatching { repository.getOrCreateUser(UID, "Ada", "a@b.c") }.exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.Unavailable)
    assertEquals(error, thrown!!.cause)
  }

  @Test
  fun getUser_translatesFirestoreErrors() = runTest {
    val error = FirebaseFirestoreException("denied", Code.PERMISSION_DENIED)
    every { ref.get() } returns Tasks.forException(error)

    val thrown = runCatching { repository.getUser(UID) }.exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.PermissionDenied)
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

  @Test
  fun toRepositoryException_mapsFirestoreCodes() {
    fun map(code: Code) =
        UserRepositoryFirestore.toRepositoryException(FirebaseFirestoreException("", code))

    assertTrue(map(Code.PERMISSION_DENIED) is UserRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAUTHENTICATED) is UserRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAVAILABLE) is UserRepositoryException.Unavailable)
    assertTrue(map(Code.DEADLINE_EXCEEDED) is UserRepositoryException.Unavailable)
    assertTrue(map(Code.INTERNAL) is UserRepositoryException.Unknown)
  }

  private companion object {
    const val UID = "uid-1"
  }
}
