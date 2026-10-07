// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FirestoreEventRepository
import java.time.Instant
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Joins an event by its code against the Firebase emulators, with `firestore.rules`: an organizer
 * creates the event, then a volunteer looks it up by code and sends a request.
 */
@RunWith(AndroidJUnit4::class)
class MembershipRequestRepositoryFirestoreEmulatorTest {

  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val events = FirestoreEventRepository(db)
  private val repository = MembershipRequestRepositoryFirestore(db)
  private lateinit var event: Event
  private lateinit var volunteerId: String

  @Before
  fun createEventThenSignInAsVolunteer() = runTest {
    // Fresh anonymous accounts per test, so tests never share an event or a request.
    val organizerId = auth.signInAnonymously().await().user!!.uid
    event = events.createEvent(newEvent(organizerId))
    auth.signOut()
    volunteerId = auth.signInAnonymously().await().user!!.uid
  }

  @After fun signOut() = auth.signOut()

  @Test
  fun requestToJoin_storesPendingRequestForEventFoundByJoinCode() = runTest {
    val found = events.getEventByJoinCode(event.joinCode)!!

    val sent = repository.requestToJoin(found.id, request(volunteerId))

    assertEquals(MembershipRequestStatus.PENDING, sent.status)
    assertEquals(sent, repository.getRequest(found.id, volunteerId))
  }

  @Test
  fun requestToJoin_returnsExistingRequestWithoutOverwritingIt() = runTest {
    val first = repository.requestToJoin(event.id, request(volunteerId))

    val second =
        repository.requestToJoin(event.id, request(volunteerId).copy(preferredTeamIds = listOf()))

    assertEquals(first, second)
  }

  @Test
  fun rules_forbidRequestingForAnotherUserOrAMissingEvent() = runTest {
    val forOther = runCatching { repository.requestToJoin(event.id, request("someone-else")) }
    val toMissing = runCatching { repository.requestToJoin("no-event", request(volunteerId)) }

    assertTrue(forOther.exceptionOrNull() is MembershipRequestRepositoryException.PermissionDenied)
    assertTrue(toMissing.exceptionOrNull() is MembershipRequestRepositoryException.PermissionDenied)
  }

  private fun newEvent(organizerId: String) =
      Event(
          organizerId = organizerId,
          title = "Lakeside",
          description = "A festival by the lake.",
          type = EventType.MUSIC,
          startAt = Instant.ofEpochSecond(1_000),
          endAt = Instant.ofEpochSecond(2_000),
          location = EventLocation("Lausanne"),
          createdAt = Instant.ofEpochSecond(500),
      )

  private fun request(userId: String) =
      MembershipRequest(
          userId = userId,
          preferredTeamIds = listOf("team-1"),
          availability =
              listOf(AvailabilitySlot(Instant.ofEpochSecond(1_000), Instant.ofEpochSecond(2_000))),
          createdAt = Instant.ofEpochSecond(600),
      )
}
