// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.Transaction
import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.CREATED_AT
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.DISPLAY_NAME
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.EMAIL
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.LOCATION_SHARING_ENABLED
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.MODE
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.NOTIFICATION_SETTINGS
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryFirestoreTest {

  private val db = mockk<FirebaseFirestore>()
  private val ref = mockk<DocumentReference>()
  private val snapshot = mockk<DocumentSnapshot>()
  private val publicRef = mockk<DocumentReference>()
  private val publicSnapshot = mockk<DocumentSnapshot>()
  private val transaction = mockk<Transaction>(relaxed = true)
  private val repository = UserRepositoryFirestore(db)

  @Before
  fun setUp() {
    val collection = mockk<CollectionReference>()
    every { db.collection(USERS_COLLECTION) } returns collection
    every { collection.document(UID) } returns ref
    every { ref.get() } returns Tasks.forResult(snapshot)
    every { transaction.get(ref) } returns snapshot
    every { snapshot.id } returns UID
    val publicProfiles = mockk<CollectionReference>()
    every { db.collection(PUBLIC_PROFILES_COLLECTION) } returns publicProfiles
    every { publicProfiles.document(UID) } returns publicRef
    every { publicRef.get() } returns Tasks.forResult(publicSnapshot)
    every { transaction.get(publicRef) } returns publicSnapshot
    every { publicSnapshot.id } returns UID
    // Signed in before: the public profile already exists, unless a test removes it.
    storedPublicProfile(mapOf(DISPLAY_NAME to "Ada"))
    // Runs the transaction body synchronously against the mocked transaction.
    every { db.runTransaction(any<Transaction.Function<Any?>>()) } answers
        {
          Tasks.forResult(firstArg<Transaction.Function<Any?>>().apply(transaction))
        }
  }

  /** Makes [snapshot] an existing document holding [fields], or a missing one when null. */
  private fun stored(fields: Map<String, Any?>?) {
    every { snapshot.exists() } returns (fields != null)
    every { snapshot.get(any<String>()) } answers { fields?.get(firstArg()) }
  }

  /** Makes [publicSnapshot] an existing public profile holding [fields], or a missing one. */
  private fun storedPublicProfile(fields: Map<String, Any?>?) {
    every { publicSnapshot.exists() } returns (fields != null)
    every { publicSnapshot.get(any<String>()) } answers { fields?.get(firstArg()) }
  }

  @Test
  fun getOrCreateUser_createsProfileAndPublicProfileWhenAbsent() = runTest {
    stored(null)
    storedPublicProfile(null)

    val user = repository.getOrCreateUser(UID, "Ada", "ada@example.com")

    assertEquals(User(UID, "Ada", "ada@example.com"), user)
    verify { transaction.set(ref, UserRepositoryFirestore.newUserFields("Ada", "ada@example.com")) }
    verify { transaction.set(publicRef, UserRepositoryFirestore.publicProfileFields("Ada")) }
  }

  @Test
  fun getOrCreateUser_givesAnExistingProfileItsMissingPublicProfile() = runTest {
    // Created before public profiles existed, under a name changed since.
    stored(
        mapOf(
            DISPLAY_NAME to "Ada L.",
            EMAIL to "ada@example.com",
            LOCATION_SHARING_ENABLED to false,
        )
    )
    storedPublicProfile(null)

    repository.getOrCreateUser(UID, "Ada", "ada@example.com")

    verify { transaction.set(publicRef, UserRepositoryFirestore.publicProfileFields("Ada L.")) }
    verify(exactly = 0) { transaction.set(ref, any()) }
  }

  @Test
  fun getOrCreateUser_returnsExistingProfileWithoutOverwritingIt() = runTest {
    stored(
        mapOf(
            DISPLAY_NAME to "Ada L.",
            EMAIL to "ada@example.com",
            LOCATION_SHARING_ENABLED to true,
        )
    )

    val user = repository.getOrCreateUser(UID, "Ada", "ada@example.com")

    assertEquals(User(UID, "Ada L.", "ada@example.com", true), user)
    verify(exactly = 0) { transaction.set(any<DocumentReference>(), any()) }
  }

  @Test
  fun getOrCreateUser_translatesFirestoreErrors() = runTest {
    val error = FirebaseFirestoreException("offline", Code.UNAVAILABLE)
    every { db.runTransaction(any<Transaction.Function<Any?>>()) } returns Tasks.forException(error)

    val thrown = runCatching { repository.getOrCreateUser(UID, "Ada", "a@b.c") }.exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.Unavailable)
    assertEquals(error, thrown!!.cause)
  }

  @Test
  fun getOrCreateUser_withModeOnAMalformedProfile_writesNothing() = runTest {
    // No email and no mode: the sign-in fails, and must not leave the mode behind.
    stored(mapOf(DISPLAY_NAME to "Ada", LOCATION_SHARING_ENABLED to false))
    storedPublicProfile(null)

    val thrown = runCatching {
      repository.getOrCreateUser(UID, "Ada", "a@b.c", UserMode.ORGANIZER)
    }
        .exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.Unknown)
    assertTrue(thrown!!.cause is IllegalStateException)
    verify(exactly = 0) { transaction.update(any(), any<Map<String, Any>>()) }
    verify(exactly = 0) { transaction.set(any<DocumentReference>(), any()) }
  }

  @Test
  fun getOrCreateUser_reportsMalformedProfileAsUnknown() = runTest {
    stored(mapOf(DISPLAY_NAME to "Ada", LOCATION_SHARING_ENABLED to false))

    val thrown = runCatching { repository.getOrCreateUser(UID, "Ada", "a@b.c") }.exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.Unknown)
    assertTrue(thrown!!.cause is IllegalStateException)
    verify(exactly = 0) { transaction.set(any<DocumentReference>(), any()) }
  }

  @Test
  fun getOrCreateUser_withMode_createsTheProfileWithIt() = runTest {
    stored(null)

    val user = repository.getOrCreateUser(UID, "Ada", "ada@example.com", UserMode.ORGANIZER)

    assertEquals(UserMode.ORGANIZER, user.mode)
    verify {
      transaction.set(
          ref,
          UserRepositoryFirestore.newUserFields("Ada", "ada@example.com", UserMode.ORGANIZER),
      )
    }
  }

  @Test
  fun getOrCreateUser_withModeOnAProfileWithout_savesIt() = runTest {
    stored(
        mapOf(DISPLAY_NAME to "Ada", EMAIL to "ada@example.com", LOCATION_SHARING_ENABLED to false)
    )

    val user = repository.getOrCreateUser(UID, "Ada", "ada@example.com", UserMode.ORGANIZER)

    assertEquals(UserMode.ORGANIZER, user.mode)
    verify { transaction.update(ref, mapOf<String, Any>(MODE to "ORGANIZER")) }
  }

  @Test
  fun getOrCreateUser_withModeOnAProfileWithOne_keepsTheStoredMode() = runTest {
    // An unknown mode, from a later version, is kept as well.
    listOf("VOLUNTEER", "ADMIN").forEach { storedMode ->
      stored(
          mapOf(
              DISPLAY_NAME to "Ada",
              EMAIL to "ada@example.com",
              LOCATION_SHARING_ENABLED to false,
              MODE to storedMode,
          )
      )

      val user = repository.getOrCreateUser(UID, "Ada", "ada@example.com", UserMode.ORGANIZER)

      assertEquals(UserMode.entries.firstOrNull { it.name == storedMode }, user.mode)
    }
    verify(exactly = 0) { transaction.update(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun setMode_writesTheModeByName() = runTest {
    every { ref.update(MODE, "ORGANIZER") } returns Tasks.forResult(null)

    repository.setMode(UID, UserMode.ORGANIZER)

    verify { ref.update(MODE, "ORGANIZER") }
  }

  @Test
  fun setMode_translatesFirestoreErrors() = runTest {
    // What the rules answer when there is no profile.
    every { ref.update(MODE, "VOLUNTEER") } returns
        Tasks.forException(FirebaseFirestoreException("denied", Code.PERMISSION_DENIED))

    val thrown = runCatching { repository.setMode(UID, UserMode.VOLUNTEER) }.exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.PermissionDenied)
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
    stored(null)

    assertNull(repository.getUser(UID))
  }

  @Test
  fun getUser_returnsMappedUserWhenDocumentExists() = runTest {
    stored(
        mapOf(DISPLAY_NAME to "Ada", EMAIL to "ada@example.com", LOCATION_SHARING_ENABLED to false)
    )

    assertEquals(User(UID, "Ada", "ada@example.com"), repository.getUser(UID))
  }

  @Test
  fun getUser_reportsMalformedProfileAsUnknown() = runTest {
    stored(mapOf(DISPLAY_NAME to "Ada", EMAIL to 42, LOCATION_SHARING_ENABLED to false))

    val thrown = runCatching { repository.getUser(UID) }.exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.Unknown)
    assertTrue(thrown!!.cause is IllegalStateException)
  }

  @Test
  fun getPublicProfile_returnsNullWhenDocumentMissing() = runTest {
    storedPublicProfile(null)

    assertNull(repository.getPublicProfile(UID))
  }

  @Test
  fun getPublicProfile_returnsMappedProfileWhenDocumentExists() = runTest {
    assertEquals(PublicProfile(UID, "Ada"), repository.getPublicProfile(UID))
  }

  @Test
  fun getPublicProfile_reportsMalformedProfileAsUnknown() = runTest {
    storedPublicProfile(mapOf(DISPLAY_NAME to 42))

    val thrown = runCatching { repository.getPublicProfile(UID) }.exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.Unknown)
    assertTrue(thrown!!.cause is IllegalStateException)
  }

  @Test
  fun getPublicProfile_translatesFirestoreErrors() = runTest {
    val error = FirebaseFirestoreException("denied", Code.PERMISSION_DENIED)
    every { publicRef.get() } returns Tasks.forException(error)

    val thrown = runCatching { repository.getPublicProfile(UID) }.exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.PermissionDenied)
  }

  @Test
  fun publicProfileFields_holdOnlyTheName() {
    assertEquals(mapOf(DISPLAY_NAME to "Ada"), UserRepositoryFirestore.publicProfileFields("Ada"))
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
    val createdAt = Instant.ofEpochSecond(1_700_000_000, 123_000)
    stored(
        mapOf(
            DISPLAY_NAME to "Ada",
            EMAIL to "ada@example.com",
            LOCATION_SHARING_ENABLED to true,
            CREATED_AT to Timestamp(createdAt.epochSecond, createdAt.nano),
        )
    )

    val user = UserRepositoryFirestore.userFromFirestore(snapshot)

    assertEquals(User(UID, "Ada", "ada@example.com", true, createdAt), user)
  }

  @Test
  fun userFromFirestore_leavesCreatedAtNullWhileTheServerTimestampIsPending() {
    stored(
        mapOf(DISPLAY_NAME to "Ada", EMAIL to "ada@example.com", LOCATION_SHARING_ENABLED to false)
    )

    assertNull(UserRepositoryFirestore.userFromFirestore(snapshot).createdAt)
  }

  @Test
  fun userFromFirestore_mapsTheModeByName() {
    stored(
        mapOf(
            DISPLAY_NAME to "Ada",
            EMAIL to "ada@example.com",
            LOCATION_SHARING_ENABLED to false,
            MODE to "ORGANIZER",
        )
    )

    assertEquals(UserMode.ORGANIZER, UserRepositoryFirestore.userFromFirestore(snapshot).mode)
  }

  @Test
  fun userFromFirestore_withoutMode_leavesItNull() {
    stored(
        mapOf(DISPLAY_NAME to "Ada", EMAIL to "ada@example.com", LOCATION_SHARING_ENABLED to false)
    )

    assertNull(UserRepositoryFirestore.userFromFirestore(snapshot).mode)
  }

  @Test
  fun userFromFirestore_withUnknownMode_leavesItNull() {
    stored(
        mapOf(
            DISPLAY_NAME to "Ada",
            EMAIL to "ada@example.com",
            LOCATION_SHARING_ENABLED to false,
            MODE to "ADMIN",
        )
    )

    assertNull(UserRepositoryFirestore.userFromFirestore(snapshot).mode)
  }

  @Test
  fun userFromFirestore_failsOnMissingOrMistypedFields() {
    val valid =
        mapOf(
            DISPLAY_NAME to "Ada",
            EMAIL to "ada@example.com",
            LOCATION_SHARING_ENABLED to false,
            CREATED_AT to Timestamp(1_700_000_000, 0),
        )
    val malformed =
        listOf(
            valid - DISPLAY_NAME,
            valid - EMAIL,
            valid - LOCATION_SHARING_ENABLED,
            valid + (EMAIL to 42),
            valid + (LOCATION_SHARING_ENABLED to "yes"),
            valid + (CREATED_AT to "2024-01-01"),
            valid + (MODE to 1),
        )

    malformed.forEach { fields ->
      stored(fields)
      assertThrows(IllegalStateException::class.java) {
        UserRepositoryFirestore.userFromFirestore(snapshot)
      }
    }
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

  /**
   * Captures the snapshot listener [observeLocationSharing] registers on `users/{uid}`, so a test
   * can drive it with the snapshots and errors Firestore would deliver. The registration is a
   * relaxed mock, since the flow only calls `remove()` on it.
   */
  private fun captureSnapshotListener() =
      slot<EventListener<DocumentSnapshot>>().also {
        every { ref.addSnapshotListener(capture(it)) } returns
            mockk<ListenerRegistration>(relaxed = true)
      }

  @Test
  fun observeLocationSharing_emitsTheStoredSetting() = runTest {
    val listener = captureSnapshotListener()
    stored(mapOf(LOCATION_SHARING_ENABLED to true))
    val seen = mutableListOf<Boolean>()
    val job = backgroundScope.launch {
      repository.observeLocationSharing(UID).collect { seen += it }
    }
    runCurrent()

    listener.captured.onEvent(snapshot, null)
    runCurrent()
    job.cancel()

    assertEquals(listOf(true), seen)
  }

  @Test
  fun observeLocationSharing_whenTheProfileIsMissing_emitsFalse() = runTest {
    val listener = captureSnapshotListener()
    stored(null)
    val seen = mutableListOf<Boolean>()
    val job = backgroundScope.launch {
      repository.observeLocationSharing(UID).collect { seen += it }
    }
    runCurrent()

    listener.captured.onEvent(snapshot, null)
    runCurrent()
    job.cancel()

    assertEquals(listOf(false), seen)
  }

  @Test
  fun observeLocationSharing_whenTheProfileIsMalformed_failsWithUnknown() = runTest {
    val listener = captureSnapshotListener()
    // Present but without the boolean field: requireBoolean rejects it as a malformed document.
    stored(mapOf(DISPLAY_NAME to "Ada"))
    var thrown: Throwable? = null
    backgroundScope.launch {
      try {
        repository.observeLocationSharing(UID).collect {}
      } catch (e: Throwable) {
        thrown = e
      }
    }
    runCurrent()

    listener.captured.onEvent(snapshot, null)
    runCurrent()

    assertTrue(thrown is UserRepositoryException.Unknown)
  }

  @Test
  fun observeLocationSharing_whenTheListenerFails_failsWithTheMappedException() = runTest {
    val listener = captureSnapshotListener()
    var thrown: Throwable? = null
    backgroundScope.launch {
      try {
        repository.observeLocationSharing(UID).collect {}
      } catch (e: Throwable) {
        thrown = e
      }
    }
    runCurrent()

    listener.captured.onEvent(null, FirebaseFirestoreException("denied", Code.PERMISSION_DENIED))
    runCurrent()

    assertTrue(thrown is UserRepositoryException.PermissionDenied)
  }

  @Test
  fun setNotificationEnabled_writesOnlyThatKind() = runTest {
    val path = FieldPath.of(NOTIFICATION_SETTINGS, "ANNOUNCEMENT")
    every { ref.update(path, false) } returns Tasks.forResult(null)

    repository.setNotificationEnabled(UID, NotificationKind.ANNOUNCEMENT, enabled = false)

    verify { ref.update(path, false) }
  }

  @Test
  fun setNotificationEnabled_translatesFirestoreErrors() = runTest {
    // What the rules answer when there is no profile.
    every { ref.update(FieldPath.of(NOTIFICATION_SETTINGS, "ANNOUNCEMENT"), true) } returns
        Tasks.forException(FirebaseFirestoreException("denied", Code.PERMISSION_DENIED))

    val thrown = runCatching {
      repository.setNotificationEnabled(UID, NotificationKind.ANNOUNCEMENT, enabled = true)
    }
        .exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.PermissionDenied)
  }

  @Test
  fun observeDisabledNotifications_emitsTheKindsTurnedOff() = runTest {
    val settings = mapOf("ANNOUNCEMENT" to false, "OPEN_SPOTS" to true)

    val seen = disabledNotificationsSeen(mapOf(NOTIFICATION_SETTINGS to settings))

    assertEquals(listOf(setOf(NotificationKind.ANNOUNCEMENT)), seen)
  }

  @Test
  fun observeDisabledNotifications_withoutChoices_emitsNoKind() = runTest {
    assertEquals(listOf(emptySet<NotificationKind>()), disabledNotificationsSeen(mapOf()))
  }

  @Test
  fun observeDisabledNotifications_whenTheProfileIsMissing_emitsNoKind() = runTest {
    assertEquals(listOf(emptySet<NotificationKind>()), disabledNotificationsSeen(null))
  }

  @Test
  fun observeDisabledNotifications_skipsAKindItDoesNotKnow() = runTest {
    // Written by a later version, with a kind this one does not have.
    val settings = mapOf("A_LATER_KIND" to false, "ANNOUNCEMENT" to false)

    val seen = disabledNotificationsSeen(mapOf(NOTIFICATION_SETTINGS to settings))

    assertEquals(listOf(setOf(NotificationKind.ANNOUNCEMENT)), seen)
  }

  @Test
  fun observeDisabledNotifications_whenTheChoicesAreMalformed_failsWithUnknown() = runTest {
    for (malformed in listOf("off", mapOf("ANNOUNCEMENT" to "off"))) {
      val thrown = disabledNotificationsFailure(mapOf(NOTIFICATION_SETTINGS to malformed))

      assertTrue("$malformed gave $thrown", thrown is UserRepositoryException.Unknown)
    }
  }

  @Test
  fun observeDisabledNotifications_whenTheListenerFails_failsWithTheMappedException() = runTest {
    val listener = captureMetadataSnapshotListener()
    var thrown: Throwable? = null
    backgroundScope.launch {
      try {
        repository.observeDisabledNotifications(UID).collect {}
      } catch (e: Throwable) {
        thrown = e
      }
    }
    runCurrent()

    listener.captured.onEvent(null, FirebaseFirestoreException("denied", Code.PERMISSION_DENIED))
    runCurrent()

    assertTrue(thrown is UserRepositoryException.PermissionDenied)
  }

  @Test
  fun observeDisabledNotifications_dropsAnUnchangedSet() = runTest {
    val listener = captureMetadataSnapshotListener()
    val seen = mutableListOf<Set<NotificationKind>>()
    val job = backgroundScope.launch {
      repository.observeDisabledNotifications(UID).collect { seen += it }
    }
    runCurrent()

    // Another field changes: the listener fires again with the same choices.
    stored(mapOf(MODE to "VOLUNTEER"))
    listener.captured.onEvent(snapshot, null)
    stored(mapOf(MODE to "ORGANIZER"))
    listener.captured.onEvent(snapshot, null)
    stored(mapOf(MODE to "ORGANIZER", NOTIFICATION_SETTINGS to mapOf("ANNOUNCEMENT" to false)))
    listener.captured.onEvent(snapshot, null)
    runCurrent()
    job.cancel()

    assertEquals(listOf(emptySet(), setOf(NotificationKind.ANNOUNCEMENT)), seen)
  }

  @Test
  fun observeDisabledNotifications_offlineWithoutACachedProfile_waitsForTheServer() = runTest {
    val listener = captureMetadataSnapshotListener()
    val seen = mutableListOf<Set<NotificationKind>>()
    val job = backgroundScope.launch {
      repository.observeDisabledNotifications(UID).collect { seen += it }
    }
    runCurrent()

    // Offline, a profile this device never cached reads as missing from the cache: unknown.
    stored(null)
    fromCache(true)
    listener.captured.onEvent(snapshot, null)
    runCurrent()
    assertEquals(emptyList<Set<NotificationKind>>(), seen)

    // Back online, the server confirms it is missing: only the metadata changes.
    fromCache(false)
    listener.captured.onEvent(snapshot, null)
    runCurrent()
    job.cancel()

    assertEquals(listOf(emptySet<NotificationKind>()), seen)
  }

  @Test
  fun observeDisabledNotifications_offlineWithACachedProfile_emitsItsChoices() = runTest {
    val settings = mapOf("ANNOUNCEMENT" to false)

    val seen = disabledNotificationsSeen(mapOf(NOTIFICATION_SETTINGS to settings), cached = true)

    assertEquals(listOf(setOf(NotificationKind.ANNOUNCEMENT)), seen)
  }

  /**
   * Captures the listener [UserRepositoryFirestore.observeDisabledNotifications] registers, with
   * metadata changes, and makes [snapshot] come from the server until [fromCache] says otherwise.
   */
  private fun captureMetadataSnapshotListener() =
      slot<EventListener<DocumentSnapshot>>().also {
        every { ref.addSnapshotListener(MetadataChanges.INCLUDE, capture(it)) } returns
            mockk<ListenerRegistration>(relaxed = true)
        fromCache(false)
      }

  /** Makes [snapshot] come from the local cache when [cached], else from the server. */
  private fun fromCache(cached: Boolean) {
    val metadata = mockk<SnapshotMetadata>()
    every { metadata.isFromCache } returns cached
    every { snapshot.metadata } returns metadata
  }

  /**
   * What [UserRepositoryFirestore.observeDisabledNotifications] emits for one snapshot, from the
   * server or, when [cached], from the local cache.
   */
  private fun TestScope.disabledNotificationsSeen(
      fields: Map<String, Any?>?,
      cached: Boolean = false,
  ): List<Set<NotificationKind>> {
    val listener = captureMetadataSnapshotListener()
    stored(fields)
    fromCache(cached)
    val seen = mutableListOf<Set<NotificationKind>>()
    val job = backgroundScope.launch {
      repository.observeDisabledNotifications(UID).collect { seen += it }
    }
    runCurrent()

    listener.captured.onEvent(snapshot, null)
    runCurrent()
    job.cancel()
    return seen
  }

  /** What [UserRepositoryFirestore.observeDisabledNotifications] fails with for one snapshot. */
  private fun TestScope.disabledNotificationsFailure(fields: Map<String, Any?>): Throwable? {
    val listener = captureMetadataSnapshotListener()
    stored(fields)
    var thrown: Throwable? = null
    backgroundScope.launch {
      try {
        repository.observeDisabledNotifications(UID).collect {}
      } catch (e: Throwable) {
        thrown = e
      }
    }
    runCurrent()

    listener.captured.onEvent(snapshot, null)
    runCurrent()
    return thrown
  }

  private companion object {
    const val UID = "uid-1"
  }
}
