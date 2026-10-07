// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.event

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
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
import org.junit.Before
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

  private val auth = FirestoreEmulator.auth
  private val firestore = FirestoreEmulator.firestore
  private val repository = FirestoreEventRepository(firestore)

  /** Ids of the documents this test created, deleted in [tearDown]. */
  private val createdEventIds = mutableListOf<String>()
  private val organizerCredentials = mutableMapOf<String, AuthCredential>()

  /** `firestore.rules` only lets signed-in users touch events. */
  @Before fun signIn() = emulatorTest { auth.signInAnonymously().await() }

  @After
  fun tearDown() = emulatorTest {
    createdEventIds.forEach { id ->
      FirestoreEmulatorAdmin.deleteDocument("${EventSchema.COLLECTION}/$id")
    }
    createdEventIds.clear()
    auth.signOut()
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
    val organizerId = uniqueOrganizerId()
    val created = create(richEvent(organizerId))

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
    // checkNotNull rather than !!: a geocoded event must come back with both coordinates set.
    assertEquals(46.3869, checkNotNull(found.location.latitude), COORDINATE_TOLERANCE)
    assertEquals(6.2228, checkNotNull(found.location.longitude), COORDINATE_TOLERANCE)
    assertEquals(2, found.emergencyContacts.size)
    assertEquals(
        EmergencyContact("Site medic", "+41220000001", "Medical"),
        found.emergencyContacts[0],
    )
    assertEquals(EmergencyContact("Night lead", "+41220000002"), found.emergencyContacts[1])
    assertNull("an unset role must stay null", found.emergencyContacts[1].role)
    assertEquals(EventStatus.ONGOING, found.status)
    assertEquals(Instant.parse("2026-01-15T09:00:00Z"), found.createdAt)
    // Order matters: the volunteer list is rendered in the order the members joined.
    assertEquals(membersOf(organizerId), found.memberIds)
  }

  @Test
  fun getEvent_roundTripsAnEventWithoutAnImageContactsOrMembers() = emulatorTest {
    // The optional collections and the optional image. A null image must come back as a null
    // rather than as an empty string, and an empty list must not come back as a missing field.
    val created =
        create(
            richEvent(uniqueOrganizerId())
                .copy(imageUrl = null, emergencyContacts = emptyList(), memberIds = emptyList())
        )

    val found = repository.getEvent(created.id)

    assertEquals(created, found)
    checkNotNull(found)
    assertNull("a null image must stay null", found.imageUrl)
    assertTrue("an empty contact list must stay empty", found.emergencyContacts.isEmpty())
    assertTrue("an empty member list must stay empty", found.memberIds.isEmpty())
  }

  @Test
  fun getEvent_roundTripsAnEventWithoutCoordinates() = emulatorTest {
    // What the creation screen produces until geocoding exists: an address and no map pin.
    val created =
        create(
            richEvent(uniqueOrganizerId())
                .copy(location = EventLocation("Route de Saint-Cergue 318, 1260 Nyon"))
        )

    val found = repository.getEvent(created.id)

    assertEquals(created, found)
    checkNotNull(found)
    assertEquals("Route de Saint-Cergue 318, 1260 Nyon", found.location.address)
    assertNull("an ungeocoded latitude must stay null", found.location.latitude)
    assertNull("an ungeocoded longitude must stay null", found.location.longitude)
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
    val eventId = writeRawEvent { it - EventSchema.TITLE }

    // A document that cannot be mapped is a schema problem, not "no event": returning null, or an
    // Event with a blank title, would hide it.
    assertFailsOnField(eventId, EventSchema.TITLE)
  }

  @Test
  fun getEvent_failsLoudlyOnARequiredFieldWithAWrongType() = emulatorTest {
    // getString would raise a bare Firebase RuntimeException here, which toEvent does not promise
    // and which callers would not recognise as a schema problem.
    val eventId = writeRawEvent { it + (EventSchema.TITLE to 42) }

    assertFailsOnField(eventId, EventSchema.TITLE)
  }

  @Test
  fun getEvent_failsLoudlyOnARequiredTimestampWithAWrongType() = emulatorTest {
    // An instant stored as the string an older tool might have written, rather than as a Timestamp.
    val eventId = writeRawEvent { it + (EventSchema.START_AT to "2026-07-21T16:00:00Z") }

    assertFailsOnField(eventId, EventSchema.START_AT)
  }

  @Test
  fun getEvent_failsLoudlyOnAnImageUrlWithAWrongType() = emulatorTest {
    // imageUrl is optional, so absent and null are both legitimate. A value of another type is
    // not: mapping it to null would silently drop the event's cover picture.
    val eventId = writeRawEvent { it + (EventSchema.IMAGE_URL to 7) }

    assertFailsOnField(eventId, EventSchema.IMAGE_URL)
  }

  @Test
  fun getEvent_mapsAnUnknownTypeToOther() = emulatorTest {
    // A type added by a newer build must not stop this build from reading the event.
    val eventId = writeRawEvent { it + (EventSchema.TYPE to "SPACE_OPERA") }

    assertEquals(EventType.OTHER, repository.getEvent(eventId)?.type)
  }

  @Test
  fun getEvent_failsLoudlyOnAnUnknownStatus() = emulatorTest {
    // Deliberately stricter than the type: EventStatus has no catch-all, and guessing one would
    // either resurrect an archived event or archive a live one.
    val eventId = writeRawEvent { it + (EventSchema.STATUS to "ZOMBIE") }

    assertFailsOnField(eventId, EventSchema.STATUS)
  }

  @Test
  fun getEvent_failsLoudlyOnMalformedMemberIds() = emulatorTest {
    val eventId = writeRawEvent { it + (EventSchema.MEMBER_IDS to listOf(1, 2)) }

    assertFailsOnField(eventId, EventSchema.MEMBER_IDS)
  }

  @Test
  fun getEvent_failsLoudlyOnHalfSetCoordinates() = emulatorTest {
    // A pin at (46.5, nowhere) is not an event waiting to be geocoded, it is a broken document.
    val eventId = writeRawEvent {
      it +
          (EventSchema.LOCATION to
              mapOf(
                  EventSchema.LocationFields.ADDRESS to "Route de Saint-Cergue 318, 1260 Nyon",
                  EventSchema.LocationFields.LATITUDE to 46.5,
                  EventSchema.LocationFields.LONGITUDE to null,
              ))
    }

    assertFailsOnField(eventId, EventSchema.LOCATION)
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

  @Test
  fun getEventsByOrganizer_returnsAnEmptyListForAnOrganizerWithoutEvents() = emulatorTest {
    // A fresh id no event was ever created under, so "no events" cannot be confused with "the
    // other tests' events were cleaned up".
    assertEquals(emptyList<Event>(), repository.getEventsByOrganizer(uniqueOrganizerId()))
  }

  @Test
  fun getEventsByMember_returnsOnlyTheEventsTheUserJoined() = emulatorTest {
    val userId = uniqueMemberId()
    val mine =
        listOf(
            create(richEvent(uniqueOrganizerId()).copy(memberIds = listOf(userId))),
            // Not the first member of the array, so the query cannot be passing by accident.
            create(
                richEvent(uniqueOrganizerId()).copy(memberIds = listOf(uniqueMemberId(), userId))
            ),
        )
    val theirs = create(richEvent(uniqueOrganizerId()))

    val found = repository.getEventsByMember(userId)

    assertEquals(mine.map { it.id }.toSet(), found.map { it.id }.toSet())
    assertTrue("an event the user never joined leaked in", found.none { it.id == theirs.id })
    assertTrue(found.all { userId in it.memberIds })
  }

  @Test
  fun getEventsByMember_returnsAnEmptyListForAUserWhoJoinedNothing() = emulatorTest {
    assertEquals(emptyList<Event>(), repository.getEventsByMember(uniqueMemberId()))
  }

  /** Creates [event] through the repository and remembers it for cleanup. */
  private suspend fun create(event: Event): Event {
    auth.signInWithCredential(organizerCredentials.getValue(event.organizerId)).await()
    return track(repository.createEvent(event))
  }

  private fun track(event: Event): Event = event.also { createdEventIds += it.id }

  /**
   * Writes an event document straight to Firestore, bypassing the repository, and returns its id.
   *
   * No repository call can write a malformed document, but a schema change or a hand edit in the
   * console can, so the mapping tests derive theirs from [richEvent]'s valid document body.
   */
  private suspend fun writeRawEvent(body: (Map<String, Any?>) -> Map<String, Any?>): String {
    val document = firestore.collection(EventSchema.COLLECTION).document()
    createdEventIds += document.id
    document.set(body(richEvent(uniqueOrganizerId()).toFirestoreMap())).await()
    return document.id
  }

  /** Asserts that reading [eventId] fails loudly and that the failure names [field]. */
  private suspend fun assertFailsOnField(eventId: String, field: String) {
    val failure = runCatching { repository.getEvent(eventId) }.exceptionOrNull()

    assertTrue(
        "a malformed document must fail with IllegalStateException, but was: $failure",
        failure is IllegalStateException,
    )
    assertTrue(
        "the failure must name the offending field, but said: ${failure?.message}",
        failure?.message?.contains(field) == true,
    )
  }

  private fun uniqueOrganizerId(): String = runBlocking {
    val email = "organizer-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
    val user = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
    organizerCredentials[user.uid] = EmailAuthProvider.getCredential(email, password)
    user.uid
  }

  private fun uniqueMemberId(): String = "member-" + UUID.randomUUID()

  /**
   * The two members of [richEvent], derived from [organizerId] so that they are unique to the test
   * that created the event and cannot be matched by another test's member query.
   */
  private fun membersOf(organizerId: String): List<String> =
      listOf("$organizerId-volunteer-1", "$organizerId-volunteer-2")

  /**
   * An event exercising every non-trivial mapping: a nested location, two emergency contacts (one
   * without a role), two members, a non-null image, a non-default status, and three instants.
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
          memberIds = membersOf(organizerId),
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
  }
}
