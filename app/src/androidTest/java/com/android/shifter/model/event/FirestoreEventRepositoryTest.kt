// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.android.shifter.model.event

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [FirestoreEventRepository], running the real Firestore SDK against the
 * Firestore emulator through [FirestoreEmulator].
 *
 * Isolation: every test builds its events under a fresh random organizer id, so no test can observe
 * another test's data and the order they run in does not matter. Every document created is
 * remembered and deleted in [tearDown], which JUnit runs even when an assertion fails.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreEventRepositoryTest {

  private val firestore = FirestoreEmulator.firestore
  private val repository = FirestoreEventRepository(firestore)

  /** Ids of the documents this test created, deleted in [tearDown]. */
  private val createdEventIds = mutableListOf<String>()

  @After
  fun tearDown() = emulatorTest {
    createdEventIds.forEach { id ->
      firestore.collection(EventSchema.COLLECTION).document(id).delete().await()
    }
    createdEventIds.clear()
  }

  @Test
  fun createEvent_generatesANonEmptyIdAndJoinCode() = emulatorTest {
    val created = create(richEvent(uniqueOrganizerId()))

    assertTrue("the generated id must not be empty", created.id.isNotEmpty())
    assertTrue("the generated join code must not be empty", created.joinCode.isNotEmpty())
    assertEquals(JOIN_CODE_LENGTH, created.joinCode.length)

    // Every character must come from the generator's alphabet. A code is read out loud and typed
    // by volunteers, so characters like O/0 and I/1/L must never appear.
    val unexpected = created.joinCode.filterNot { it in JOIN_CODE_ALPHABET }
    assertTrue(
        "join code '${created.joinCode}' uses characters outside the alphabet: '$unexpected'",
        unexpected.isEmpty(),
    )
  }

  @Test
  fun getEvent_returnsTheStoredEventWithEveryDomainField() = emulatorTest {
    val created = create(richEvent(uniqueOrganizerId()))

    val found = repository.getEvent(created.id)

    // Data-class equality covers every field at once, including the generated id and join code.
    assertEquals(created, found)

    // Spelled out as well, so a mapping regression says which field broke.
    checkNotNull(found)
    assertEquals(created.id, found.id)
    assertEquals(created.organizerId, found.organizerId)
    assertEquals("Six stages, 250 volunteers, by the lake.", found.description)
    assertEquals(EventType.MUSIC, found.type)
    assertEquals("https://example.org/lakeside.jpg", found.imageUrl)
    assertEquals(Instant.parse("2026-07-21T16:00:00Z"), found.startAt)
    assertEquals(Instant.parse("2026-07-26T02:30:00Z"), found.endAt)
    assertEquals("Route de Saint-Cergue 318, 1260 Nyon", found.location.address)
    assertEquals(46.3869, found.location.latitude, COORDINATE_TOLERANCE)
    assertEquals(6.2228, found.location.longitude, COORDINATE_TOLERANCE)
    assertEquals(2, found.emergencyContacts.size)
    assertEquals(
        EmergencyContact("Site medic", "+41220000001", "Medical"),
        found.emergencyContacts[0],
    )
    assertEquals(EmergencyContact("Night lead", "+41220000002"), found.emergencyContacts[1])
    assertNull("an unset role must stay null", found.emergencyContacts[1].role)
    assertEquals(EventStatus.ONGOING, found.status)
    assertEquals(Instant.parse("2026-01-15T09:00:00Z"), found.createdAt)
  }

  @Test
  fun getEvent_roundTripsAnEventWithoutAnImageOrEmergencyContacts() = emulatorTest {
    // The two optional fields of the model. A null image must come back as a null rather than as
    // an empty string, and an empty contact list must not come back as a missing field.
    val created =
        create(
            richEvent(uniqueOrganizerId()).copy(imageUrl = null, emergencyContacts = emptyList())
        )

    val found = repository.getEvent(created.id)

    assertEquals(created, found)
    checkNotNull(found)
    assertNull("a null image must stay null", found.imageUrl)
    assertTrue("an empty contact list must stay empty", found.emergencyContacts.isEmpty())
  }

  @Test
  fun createEvent_doesNotDuplicateTheIdAsADocumentField() = emulatorTest {
    val created = create(richEvent(uniqueOrganizerId()))

    val document =
        firestore.collection(EventSchema.COLLECTION).document(created.id).get(Source.SERVER).await()

    assertEquals("the event id must be the document id", created.id, document.id)
    assertFalse("the id must not be duplicated as a field", document.contains("id"))
  }

  @Test
  fun createEvent_neverReusesAnExistingJoinCode() = emulatorTest {
    val organizerId = uniqueOrganizerId()
    // Two repositories seeded identically draw the same first candidate, so the second create has
    // to notice the collision and draw again.
    val first = FirestoreEventRepository(firestore, Random(JOIN_CODE_SEED))
    val second = FirestoreEventRepository(firestore, Random(JOIN_CODE_SEED))

    val firstEvent = track(first.createEvent(richEvent(organizerId)))
    val secondEvent = track(second.createEvent(richEvent(organizerId)))

    assertNotEquals(firstEvent.joinCode, secondEvent.joinCode)
    assertEquals(firstEvent.id, repository.getEventByJoinCode(firstEvent.joinCode)?.id)
    assertEquals(secondEvent.id, repository.getEventByJoinCode(secondEvent.joinCode)?.id)
  }

  @Test
  fun getEvent_returnsNullForAnUnknownId() = emulatorTest {
    assertNull(repository.getEvent("missing-" + UUID.randomUUID()))
  }

  @Test
  fun getEvent_failsLoudlyOnADocumentMissingARequiredField() = emulatorTest {
    // Written straight to Firestore, bypassing the repository: no repository call can write this
    // document, but a schema change or a hand edit in the console can.
    val document = firestore.collection(EventSchema.COLLECTION).document()
    createdEventIds += document.id
    document.set(richEvent(uniqueOrganizerId()).toFirestoreMap() - EventSchema.TITLE).await()

    val failure = runCatching { repository.getEvent(document.id) }.exceptionOrNull()

    // A document that cannot be mapped is a schema problem, not "no event": returning null, or an
    // Event with a blank title, would hide it.
    assertTrue(
        "a malformed document must fail with IllegalStateException, but was: $failure",
        failure is IllegalStateException,
    )
    assertTrue(
        "the failure must name the offending field, but said: ${failure?.message}",
        failure?.message?.contains(EventSchema.TITLE) == true,
    )
  }

  @Test
  fun getEventByJoinCode_returnsTheMatchingEvent() = emulatorTest {
    val created = create(richEvent(uniqueOrganizerId()))

    val found = repository.getEventByJoinCode(created.joinCode)

    assertEquals(created, found)
  }

  @Test
  fun getEventByJoinCode_returnsNullForAnUnknownCode() = emulatorTest {
    // Lower case and a dash, so the generator can never produce this code.
    assertNull(repository.getEventByJoinCode("unknown-" + UUID.randomUUID()))
  }

  @Test
  fun getEventsByOrganizer_returnsOnlyTheEventsOfThatOrganizer() = emulatorTest {
    val organizerId = uniqueOrganizerId()
    val otherOrganizerId = uniqueOrganizerId()
    val mine =
        listOf(
            create(richEvent(organizerId).copy(title = "Opening weekend")),
            create(richEvent(organizerId).copy(title = "Closing weekend")),
        )
    val theirs = create(richEvent(otherOrganizerId))

    val found = repository.getEventsByOrganizer(organizerId)

    assertEquals(mine.map { it.id }.toSet(), found.map { it.id }.toSet())
    assertTrue("another organizer's event leaked in", found.none { it.id == theirs.id })
    assertTrue(found.all { it.organizerId == organizerId })
  }

  /** Creates [event] through the repository and remembers it for cleanup. */
  private suspend fun create(event: Event): Event = track(repository.createEvent(event))

  private fun track(event: Event): Event = event.also { createdEventIds += it.id }

  private fun uniqueOrganizerId(): String = "organizer-" + UUID.randomUUID()

  /**
   * An event exercising every non-trivial mapping: a nested location, two emergency contacts (one
   * without a role), a non-null image, a non-default status, and three instants.
   *
   * The instants are whole seconds on purpose: Firestore keeps microseconds, so a sub-microsecond
   * [Instant] would not survive the round trip.
   */
  private fun richEvent(organizerId: String) =
      Event(
          organizerId = organizerId,
          title = "Lakeside Festival",
          description = "Six stages, 250 volunteers, by the lake.",
          type = EventType.MUSIC,
          imageUrl = "https://example.org/lakeside.jpg",
          startAt = Instant.parse("2026-07-21T16:00:00Z"),
          endAt = Instant.parse("2026-07-26T02:30:00Z"),
          location = EventLocation("Route de Saint-Cergue 318, 1260 Nyon", 46.3869, 6.2228),
          emergencyContacts =
              listOf(
                  EmergencyContact("Site medic", "+41220000001", "Medical"),
                  EmergencyContact("Night lead", "+41220000002"),
              ),
          status = EventStatus.ONGOING,
          createdAt = Instant.parse("2026-01-15T09:00:00Z"),
      )

  /** Fails the test instead of hanging when the emulator cannot be reached. */
  private fun emulatorTest(block: suspend () -> Unit) = runBlocking {
    withTimeout(TIMEOUT_MILLIS) { block() }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L
    const val COORDINATE_TOLERANCE = 1e-9
    const val JOIN_CODE_SEED = 20260705

    /**
     * The alphabet [FirestoreEventRepository] draws join codes from: upper-case letters and digits
     * without the pairs users misread when typing a code, so no `0`/`O` and no `1`/`I`/`L`.
     *
     * Repeated characters are legitimate: `MMMMMM` is a code the generator can produce, `OOOOOO` is
     * not. Kept here rather than read from the repository, whose constants are private, so that
     * widening the alphabet in production fails this test and has to be a deliberate decision.
     */
    const val JOIN_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val JOIN_CODE_LENGTH = 6

    /** Exactly the fields [FirestoreEventRepository] writes: no `id` among them. */
    val EXPECTED_DOCUMENT_FIELDS =
        setOf(
            EventSchema.ORGANIZER_ID,
            EventSchema.TITLE,
            EventSchema.DESCRIPTION,
            EventSchema.TYPE,
            EventSchema.IMAGE_URL,
            EventSchema.START_AT,
            EventSchema.END_AT,
            EventSchema.LOCATION,
            EventSchema.EMERGENCY_CONTACTS,
            EventSchema.JOIN_CODE,
            EventSchema.STATUS,
            EventSchema.CREATED_AT,
        )
  }
}
