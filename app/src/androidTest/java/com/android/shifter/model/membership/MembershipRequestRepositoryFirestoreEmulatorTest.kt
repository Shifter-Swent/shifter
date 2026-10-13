// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FirestoreEventRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MembershipRequestRepositoryFirestoreEmulatorTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val repository = MembershipRequestRepositoryFirestore(db)
  private val paths = mutableSetOf<String>()
  private lateinit var organizer: Account
  private lateinit var volunteer: Account

  @Before
  fun setUp() = emulatorTest {
    organizer = account()
    volunteer = account()
  }

  @After
  fun cleanUp() = emulatorTest {
    try {
      paths.forEach { FirestoreEmulatorAdmin.deleteDocument(it) }
    } finally {
      auth.signOut()
    }
  }

  @Test
  fun acceptCreatesParticipantsAndPreservesOtherParticipantsWithoutDuplicates() = emulatorTest {
    val eventId = event()
    val pending = apply(eventId, volunteer)
    signIn(organizer)
    repository.accept(eventId, volunteer.uid)
    assertEquals(listOf(volunteer.uid), participantIds(eventId))
    val other = account()
    apply(eventId, other)
    signIn(organizer)
    repository.accept(eventId, other.uid)
    repository.accept(eventId, volunteer.uid)
    assertEquals(listOf(volunteer.uid, other.uid), participantIds(eventId))
    assertEquals(listOf(volunteer.uid, other.uid), memberIds(eventId, TEAM_ID))
    assertEquals(emptyList<String>(), memberIds(eventId, OTHER_TEAM_ID))
    val accepted = pending.copy(status = MembershipRequestStatus.ACCEPTED)
    assertEquals(
        accepted,
        repository.getMembershipRequestsByEId(eventId).first { it.userId == volunteer.uid },
    )
    signIn(volunteer)
    assertEquals(accepted, repository.apply(eventId, request(volunteer)))
  }

  @Test
  fun decisionsCanBeReversedWithoutLosingRequestsOrOtherParticipants() = emulatorTest {
    val eventId = event()
    val pending = apply(eventId, volunteer)
    val other = account()
    apply(eventId, other)
    signIn(organizer)
    repository.accept(eventId, other.uid)
    repository.accept(eventId, volunteer.uid)
    repository.reject(eventId, volunteer.uid)
    repository.reject(eventId, volunteer.uid)
    assertEquals(
        pending.copy(status = MembershipRequestStatus.REJECTED),
        requestRef(eventId, volunteer).get(Source.SERVER).await().toMembershipRequest(),
    )
    assertEquals(listOf(other.uid), participantIds(eventId))
    assertEquals(listOf(other.uid), memberIds(eventId, TEAM_ID))
    repository.accept(eventId, volunteer.uid)
    repository.accept(eventId, volunteer.uid)
    assertEquals(listOf(other.uid, volunteer.uid), participantIds(eventId))
    assertEquals(listOf(other.uid, volunteer.uid), memberIds(eventId, TEAM_ID))
    assertEquals(
        pending.copy(status = MembershipRequestStatus.ACCEPTED),
        requestRef(eventId, volunteer).get(Source.SERVER).await().toMembershipRequest(),
    )
  }

  @Test
  fun rejectPendingCreatesEmptyParticipantsAndPreservesRejectedRequest() = emulatorTest {
    val eventId = event()
    val pending = apply(eventId, volunteer)
    signIn(organizer)
    repository.reject(eventId, volunteer.uid)
    repository.reject(eventId, volunteer.uid)
    assertEquals(emptyList<String>(), participantIds(eventId))
    assertEquals(
        pending.copy(status = MembershipRequestStatus.REJECTED),
        requestRef(eventId, volunteer).get(Source.SERVER).await().toMembershipRequest(),
    )
    repository.accept(eventId, volunteer.uid)
    assertEquals(listOf(volunteer.uid), participantIds(eventId))
  }

  @Test
  fun rejectionRequiresConsistentStatusAndParticipantRemoval() = emulatorTest {
    val eventId = event()
    val pending = apply(eventId, volunteer)
    signIn(organizer)
    repository.accept(eventId, volunteer.uid)
    assertDenied { requestRef(eventId, volunteer).update("status", "REJECTED").await() }
    assertDenied {
      participants(eventId).update("participantIds", FieldValue.arrayRemove(volunteer.uid)).await()
    }
    // Rejecting must also take the volunteer out of their team.
    assertDenied {
      db.batch()
          .update(requestRef(eventId, volunteer), "status", "REJECTED")
          .update(participants(eventId), "participantIds", FieldValue.arrayRemove(volunteer.uid))
          .commit()
          .await()
    }
    assertEquals(listOf(volunteer.uid), participantIds(eventId))
    assertEquals(listOf(volunteer.uid), memberIds(eventId, TEAM_ID))
    assertEquals(
        pending.copy(status = MembershipRequestStatus.ACCEPTED),
        requestRef(eventId, volunteer).get(Source.SERVER).await().toMembershipRequest(),
    )
  }

  @Test
  fun missingRequestDoesNotCreateParticipants() = emulatorTest {
    val eventId = event()
    signIn(organizer)
    for (decision in
        listOf<suspend () -> Unit>(
            { repository.accept(eventId, volunteer.uid) },
            { repository.reject(eventId, volunteer.uid) },
        )) {
      val failure = runCatching { decision() }.exceptionOrNull()
      assertTrue(
          "Expected an unknown request, got $failure",
          failure is MembershipRequestRepositoryException.Unknown,
      )
    }
    assertFalse(participants(eventId).get(Source.SERVER).await().exists())
    assertEquals(emptyList<String>(), memberIds(eventId, TEAM_ID))
  }

  @Test
  fun queriesFindOwnRequestsAcrossEventsAndOrganizerCanListEventRequests() = emulatorTest {
    val first = event()
    val second = event()
    val unrelated = event()
    val one = apply(first, volunteer)
    val two = apply(second, volunteer, OTHER_TEAM_ID)
    val other = account()
    apply(unrelated, other)
    signIn(volunteer)
    assertEquals(
        mapOf(first to one, second to two),
        repository.getMembershipRequestsByUId(volunteer.uid),
    )
    assertDenied { repository.getMembershipRequestsByUId(other.uid) }
    assertDenied { repository.getMembershipRequestsByEId(unrelated) }
    signIn(organizer)
    assertEquals(listOf(one), repository.getMembershipRequestsByEId(first))
    assertTrue(repository.getMembershipRequestsByEId(event()).isEmpty())
  }

  @Test
  fun volunteerCannotDecideOrChangeOwnApplication() = emulatorTest {
    val eventId = event()
    val pending = apply(eventId, volunteer)
    assertDenied { repository.accept(eventId, volunteer.uid) }
    assertDenied { repository.reject(eventId, volunteer.uid) }
    assertDenied { requestRef(eventId, volunteer).update("teamId", OTHER_TEAM_ID).await() }
    assertDenied {
      participants(eventId).set(mapOf("participantIds" to listOf(volunteer.uid))).await()
    }
    assertEquals(
        pending,
        requestRef(eventId, volunteer).get(Source.SERVER).await().toMembershipRequest(),
    )
  }

  @Test
  fun acceptedVolunteerWithdrawsKeepingOtherParticipantsAndCanApplyAgain() = emulatorTest {
    val eventId = event()
    apply(eventId, volunteer)
    val other = account()
    apply(eventId, other)
    signIn(organizer)
    repository.accept(eventId, volunteer.uid)
    repository.accept(eventId, other.uid)
    signIn(volunteer)
    repository.withdraw(eventId, volunteer.uid)
    assertEquals(listOf(other.uid), participantIds(eventId))
    assertEquals(listOf(other.uid), memberIds(eventId, TEAM_ID))
    assertFalse(requestRef(eventId, volunteer).get(Source.SERVER).await().exists())
    assertTrue(repository.getMembershipRequestsByUId(volunteer.uid).isEmpty())
    assertEquals(MembershipRequestStatus.PENDING, apply(eventId, volunteer).status)
  }

  @Test
  fun pendingVolunteerWithdrawsWithoutCreatingParticipants() = emulatorTest {
    val eventId = event()
    apply(eventId, volunteer)
    repository.withdraw(eventId, volunteer.uid)
    repository.withdraw(eventId, volunteer.uid)
    assertFalse(requestRef(eventId, volunteer).get(Source.SERVER).await().exists())
    assertFalse(participants(eventId).get(Source.SERVER).await().exists())
  }

  @Test
  fun rejectedVolunteerCanNeitherWithdrawNorApplyAgain() = emulatorTest {
    val eventId = event()
    val pending = apply(eventId, volunteer)
    signIn(organizer)
    repository.reject(eventId, volunteer.uid)
    signIn(volunteer)
    assertDenied { repository.withdraw(eventId, volunteer.uid) }
    assertDenied { requestRef(eventId, volunteer).delete().await() }
    assertDenied { requestRef(eventId, volunteer).set(pending.toFirestoreMap()).await() }
    assertEquals(MembershipRequestStatus.REJECTED, apply(eventId, volunteer).status)
    assertEquals(
        pending.copy(status = MembershipRequestStatus.REJECTED),
        requestRef(eventId, volunteer).get(Source.SERVER).await().toMembershipRequest(),
    )
  }

  @Test
  fun withdrawalNeedsBothWritesAndOnlyTheVolunteerThemself() = emulatorTest {
    val eventId = event()
    val pending = apply(eventId, volunteer)
    val other = account()
    apply(eventId, other)
    signIn(organizer)
    repository.accept(eventId, volunteer.uid)
    repository.accept(eventId, other.uid)
    signIn(volunteer)
    assertDenied { requestRef(eventId, volunteer).delete().await() }
    assertDenied {
      participants(eventId).update("participantIds", FieldValue.arrayRemove(volunteer.uid)).await()
    }
    // Leaving the event must also mean leaving the team.
    assertDenied {
      db.batch()
          .delete(requestRef(eventId, volunteer))
          .update(participants(eventId), "participantIds", FieldValue.arrayRemove(volunteer.uid))
          .commit()
          .await()
    }
    assertDenied { repository.withdraw(eventId, other.uid) }
    signIn(organizer)
    assertDenied { repository.withdraw(eventId, volunteer.uid) }
    assertEquals(listOf(volunteer.uid, other.uid), participantIds(eventId))
    assertEquals(listOf(volunteer.uid, other.uid), memberIds(eventId, TEAM_ID))
    assertEquals(
        pending.copy(status = MembershipRequestStatus.ACCEPTED),
        requestRef(eventId, volunteer).get(Source.SERVER).await().toMembershipRequest(),
    )
  }

  @Test
  fun organizerCannotAlterRequestFieldsResetStatusOrDeleteRequest() = emulatorTest {
    val eventId = event()
    apply(eventId, volunteer)
    signIn(organizer)
    val ref = requestRef(eventId, volunteer)
    assertDenied { ref.update("userId", organizer.uid).await() }
    assertDenied { ref.update("availability", emptyList<Any>()).await() }
    assertDenied { ref.update("status", "INVALID").await() }
    repository.reject(eventId, volunteer.uid)
    assertDenied { ref.update("status", "PENDING").await() }
    assertDenied { ref.delete().await() }
  }

  @Test
  fun acceptanceRequiresBothWritesAndCannotAddUnacceptedUsers() = emulatorTest {
    val eventId = event()
    apply(eventId, volunteer)
    signIn(organizer)
    val ref = requestRef(eventId, volunteer)
    assertDenied { ref.update("status", "ACCEPTED").await() }
    assertDenied {
      participants(eventId).set(mapOf("participantIds" to listOf(volunteer.uid))).await()
    }
    // Accepting must also add the volunteer to the team they applied to, and only to that one.
    assertDenied {
      db.batch()
          .update(ref, "status", "ACCEPTED")
          .set(participants(eventId), mapOf("participantIds" to listOf(volunteer.uid)))
          .commit()
          .await()
    }
    assertDenied {
      db.batch()
          .update(ref, "status", "ACCEPTED")
          .set(participants(eventId), mapOf("participantIds" to listOf(volunteer.uid)))
          .update(team(eventId, OTHER_TEAM_ID), "memberIds", FieldValue.arrayUnion(volunteer.uid))
          .commit()
          .await()
    }
    assertEquals("PENDING", ref.get(Source.SERVER).await().getString("status"))
    assertFalse(participants(eventId).get(Source.SERVER).await().exists())
    repository.accept(eventId, volunteer.uid)
    assertDenied {
      participants(eventId).update("participantIds", FieldValue.arrayUnion("stranger")).await()
    }
    assertDenied { participants(eventId).update("participantIds", emptyList<String>()).await() }
    assertDenied {
      participants(eventId).update("participantIds", listOf(volunteer.uid, volunteer.uid)).await()
    }
    assertDenied { participants(eventId).update("note", "unexpected").await() }
    assertDenied { participants(eventId).delete().await() }
    assertEquals(listOf(volunteer.uid), participantIds(eventId))
  }

  @Test
  fun singleReadFindsOwnRequestOnlyForTheVolunteerAndTheOrganizer() = emulatorTest {
    val eventId = event()
    signIn(volunteer)
    assertNull(repository.getMembershipRequest(eventId, volunteer.uid))
    val sent = apply(eventId, volunteer, listOf("bar"))
    assertEquals(sent, repository.getMembershipRequest(eventId, volunteer.uid))
    signIn(organizer)
    assertEquals(sent, repository.getMembershipRequest(eventId, volunteer.uid))
    signIn(account())
    assertDenied { repository.getMembershipRequest(eventId, volunteer.uid) }
  }

  @Test
  fun unrelatedUserCannotReadRequestsOrTakeOverEvent() = emulatorTest {
    val eventId = event()
    apply(eventId, volunteer)
    val other = account()
    assertDenied { requestRef(eventId, volunteer).get(Source.SERVER).await() }
    assertDenied { repository.accept(eventId, volunteer.uid) }
    assertDenied { repository.reject(eventId, volunteer.uid) }
    val event = db.collection("events").document(eventId)
    assertDenied { event.update("organizerId", other.uid).await() }
    assertDenied { event.update("title", "changed").await() }
    assertDenied { event.delete().await() }
    assertDenied { event.set(mapOf("organizerId" to other.uid)).await() }
    signIn(organizer)
    assertDenied { event.update("organizerId", other.uid).await() }
    event.update("title", "Updated title").await()
    assertEquals("Updated title", event.get(Source.SERVER).await().getString("title"))
    event.delete().await()
  }

  @Test
  fun createValidatesOwnerEventStatusAndFields() = emulatorTest {
    val eventId = event()
    signIn(volunteer)
    assertDenied { repository.apply(eventId, request(organizer)) }
    assertDenied { repository.apply("missing-${UUID.randomUUID()}", request(volunteer)) }
    val ref = requestRef(eventId, volunteer)
    paths += ref.path
    val valid = request(volunteer).toFirestoreMap()
    for (invalid in
        listOf(
            valid + ("status" to "ACCEPTED"),
            valid + ("availability" to emptyList<Any>()),
            valid + ("unexpected" to true),
            valid - "createdAt",
            valid - "teamId",
            valid + ("teamId" to "missing-team"),
            valid + ("teamId" to listOf(TEAM_ID)),
            valid + ("preferredTeamIds" to listOf(TEAM_ID)),
        )) {
      assertDenied { ref.set(invalid).await() }
    }
    val bogusEvent = db.collection("events").document()
    paths += bogusEvent.path
    assertDenied { bogusEvent.set(mapOf("organizerId" to organizer.uid)).await() }
  }

  @Test
  fun signedOutUsersCannotReadOrWrite() = emulatorTest {
    val eventId = event()
    apply(eventId, volunteer)
    signIn(organizer)
    repository.accept(eventId, volunteer.uid)
    auth.signOut()
    assertDenied { db.collection("events").document(eventId).get(Source.SERVER).await() }
    assertDenied { requestRef(eventId, volunteer).get(Source.SERVER).await() }
    assertDenied { participants(eventId).get(Source.SERVER).await() }
    assertDenied { repository.reject(eventId, volunteer.uid) }
    assertDenied { repository.accept(eventId, volunteer.uid) }
  }

  @Test
  fun applyStoresPendingRequestForEventFoundByJoinCode() = emulatorTest {
    signIn(organizer)
    val events = FirestoreEventRepository(db)
    val event =
        events.createEvent(
            Event(
                organizerId = organizer.uid,
                title = "Lakeside",
                description = "A festival by the lake.",
                type = EventType.MUSIC,
                startAt = Instant.ofEpochSecond(1_000),
                endAt = Instant.ofEpochSecond(2_000),
                location = EventLocation("Lausanne"),
                createdAt = Instant.ofEpochSecond(500),
            )
        )
    paths += "events/${event.id}"
    createTeam(event.id, TEAM_ID)
    signIn(volunteer)
    val found = checkNotNull(events.getEventByJoinCode(event.joinCode))
    val sent = apply(found.id, volunteer)
    assertEquals(MembershipRequestStatus.PENDING, sent.status)
    assertEquals(
        sent,
        requestRef(found.id, volunteer).get(Source.SERVER).await().toMembershipRequest(),
    )
  }

  @Test
  fun applyReturnsExistingPendingRequestWithoutChangingItsTeam() = emulatorTest {
    val eventId = event()
    val first = apply(eventId, volunteer)
    val second = repository.apply(eventId, request(volunteer, OTHER_TEAM_ID))
    assertEquals(first, second)
  }

  private suspend fun event(): String {
    signIn(organizer)
    val ref = db.collection("events").document()
    paths += ref.path
    paths += participants(ref.id).path
    ref.set(mapOf("organizerId" to organizer.uid)).await()
    createTeam(ref.id, TEAM_ID)
    createTeam(ref.id, OTHER_TEAM_ID)
    return ref.id
  }

  /** Creates an empty team of [eventId] as its organizer, who must be signed in. */
  private suspend fun createTeam(eventId: String, teamId: String) {
    val ref = team(eventId, teamId)
    paths += ref.path
    ref.set(mapOf("memberIds" to emptyList<String>())).await()
  }

  private suspend fun apply(
      eventId: String,
      user: Account,
      teamId: String = TEAM_ID,
  ): MembershipRequest {
    signIn(user)
    paths += requestRef(eventId, user).path
    return repository.apply(eventId, request(user, teamId))
  }

  private suspend fun account(): Account {
    val email = "membership-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
    val user = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
    return Account(user.uid, EmailAuthProvider.getCredential(email, password))
  }

  private suspend fun signIn(user: Account) {
    auth.signInWithCredential(user.credential).await()
  }

  private fun participants(eventId: String) = db.collection("eventParticipants").document(eventId)

  private suspend fun participantIds(eventId: String) =
      participants(eventId).get(Source.SERVER).await().get("participantIds")

  private fun team(eventId: String, teamId: String) =
      db.collection("events").document(eventId).collection("teams").document(teamId)

  private suspend fun memberIds(eventId: String, teamId: String) =
      team(eventId, teamId).get(Source.SERVER).await().get("memberIds")

  private fun requestRef(eventId: String, user: Account) =
      db.collection("events").document(eventId).collection("membershipRequests").document(user.uid)

  private fun request(user: Account, teamId: String = TEAM_ID) =
      MembershipRequest(
          userId = user.uid,
          teamId = teamId,
          availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
          createdAt = Instant.EPOCH,
      )

  private data class Account(val uid: String, val credential: AuthCredential)

  private companion object {
    const val TEAM_ID = "team-bar"
    const val OTHER_TEAM_ID = "team-logistics"
  }

  private suspend fun assertDenied(block: suspend () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue(
        "Expected permission denial, got $failure",
        failure is MembershipRequestRepositoryException.PermissionDenied ||
            (failure is FirebaseFirestoreException &&
                failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED),
    )
  }

  private fun emulatorTest(block: suspend () -> Unit) = runBlocking {
    withTimeout(30_000) { block() }
  }
}
