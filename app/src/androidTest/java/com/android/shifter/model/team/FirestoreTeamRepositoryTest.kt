// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.ScratchEvents
import com.swent.shifter.model.event.EventSchema
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [FirestoreTeamRepository], running the real Firestore SDK against the
 * Firestore emulator through [FirestoreEmulator]. Field-level mapping is covered by
 * [TeamFirestoreMapperTest]; these tests cover the repository's queries and membership writes.
 *
 * Isolation: every test builds its teams under a fresh event of its own, so no test can observe
 * another test's data and the order they run in does not matter. Every document created is
 * remembered and deleted in [tearDown], which JUnit runs even when an assertion fails.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreTeamRepositoryTest {

  private val auth = FirestoreEmulator.auth
  private val firestore = FirestoreEmulator.firestore
  private val repository = FirestoreTeamRepository(firestore)

  /** The documents this test created, deleted in [tearDown]. */
  private val createdDocuments = mutableListOf<DocumentReference>()

  /** `firestore.rules` only lets signed-in users touch the teams of an event. */
  @Before fun signIn() = emulatorTest { auth.signInAnonymously().await() }

  @After
  fun tearDown() = emulatorTest {
    // Children first: once its event is gone, the rules refuse to delete them.
    createdDocuments.asReversed().forEach { it.delete().await() }
    createdDocuments.clear()
    auth.signOut()
  }

  @Test
  fun createTeamStoresTheTeamUnderItsEventWithAGeneratedId() = emulatorTest {
    val created = create(richTeam(newEvent()))

    assertTrue("the generated id must not be empty", created.id.isNotEmpty())
    val document = teamDocument(created.eventId, created.id).get(Source.SERVER).await()
    assertTrue("the team must be stored in its event's subcollection", document.exists())
  }

  @Test
  fun getTeamReturnsTheStoredTeam() = emulatorTest {
    val eventId = newEvent()
    val created = create(richTeam(eventId))

    assertEquals(created, repository.getTeam(eventId, created.id))
  }

  @Test
  fun getTeamReturnsNullForAnUnknownId() = emulatorTest {
    assertNull(repository.getTeam(newEvent(), "missing-" + UUID.randomUUID()))
  }

  @Test
  fun createTeamRejectsATeamWithoutAnEvent() = emulatorTest {
    val failure = runCatching { repository.createTeam(richTeam(eventId = "")) }.exceptionOrNull()

    assertTrue(
        "an event-less team must fail with IllegalArgumentException, but was: $failure",
        failure is IllegalArgumentException,
    )
  }

  @Test
  fun createTeamRejectsAManagerListedAmongTheMembers() = emulatorTest {
    val eventId = newEvent()
    val team = richTeam(eventId).copy(memberIds = listOf(MANAGER_ID, VOLUNTEER_ID))

    val failure = runCatching { repository.createTeam(team) }.exceptionOrNull()

    assertTrue(
        "a manager among the members must fail with IllegalArgumentException, but was: $failure",
        failure is IllegalArgumentException,
    )
    assertEquals("nothing must have been written", emptyList<Team>(), teamsOf(eventId))
  }

  @Test
  fun getTeamsByEventReturnsOnlyTheTeamsOfThatEvent() = emulatorTest {
    val eventId = newEvent()
    val mine =
        listOf(
            create(richTeam(eventId).copy(name = "Logistics")),
            create(richTeam(eventId).copy(name = "Bar")),
        )
    val theirs = create(richTeam(newEvent()))

    val found = repository.getTeamsByEvent(eventId)

    assertEquals(mine.toSet(), found.toSet())
    assertTrue("another event's team leaked in", found.none { it.id == theirs.id })
  }

  @Test
  fun getTeamsByEventReturnsAnEmptyListForAnEventWithoutTeams() = emulatorTest {
    // A fresh id no team was ever created under, so "no teams" cannot be confused with "the other
    // tests' teams were cleaned up".
    assertEquals(emptyList<Team>(), repository.getTeamsByEvent(newEvent()))
  }

  @Test
  fun getTeamsOfMemberReturnsOnlyTheTeamsTheVolunteerBelongsTo() = emulatorTest {
    val eventId = newEvent()
    val mine =
        listOf(
            create(richTeam(eventId).copy(memberIds = listOf(VOLUNTEER_ID))),
            // Not the first member of the array, so the query cannot be passing by accident.
            create(richTeam(eventId).copy(memberIds = listOf(OTHER_VOLUNTEER_ID, VOLUNTEER_ID))),
        )
    create(richTeam(eventId).copy(memberIds = listOf(OTHER_VOLUNTEER_ID)))
    // Managing a team is not belonging to it.
    create(richTeam(eventId).copy(managerId = VOLUNTEER_ID, memberIds = emptyList()))
    // The same volunteer in another event's team must not leak into this event.
    create(richTeam(newEvent()).copy(memberIds = listOf(VOLUNTEER_ID)))

    val found = repository.getTeamsOfMember(eventId, VOLUNTEER_ID)

    assertEquals(mine.toSet(), found.toSet())
  }

  @Test
  fun getTeamsManagedByReturnsOnlyTheTeamsThatUserManages() = emulatorTest {
    val eventId = newEvent()
    val mine = create(richTeam(eventId).copy(managerId = MANAGER_ID))
    create(richTeam(eventId).copy(managerId = "other-manager"))
    create(richTeam(eventId).copy(managerId = null))
    create(richTeam(eventId).copy(managerId = null, memberIds = listOf(MANAGER_ID)))
    create(richTeam(newEvent()).copy(managerId = MANAGER_ID))

    assertEquals(listOf(mine), repository.getTeamsManagedBy(eventId, MANAGER_ID))
  }

  @Test
  fun setManagerAppointsSomeoneOutsideTheTeam() = emulatorTest {
    val team = create(richTeam(newEvent()).copy(managerId = null))

    repository.setManager(team.eventId, team.id, "new-manager")

    assertEquals(team.copy(managerId = "new-manager"), reload(team))
  }

  @Test
  fun setManagerPromotesAMemberAndTakesThemOutOfTheMembers() = emulatorTest {
    val team =
        create(
            richTeam(newEvent())
                .copy(managerId = MANAGER_ID, memberIds = listOf(VOLUNTEER_ID, OTHER_VOLUNTEER_ID))
        )

    repository.setManager(team.eventId, team.id, VOLUNTEER_ID)

    // The other member stays, and the previous manager does not become a member.
    assertEquals(
        team.copy(managerId = VOLUNTEER_ID, memberIds = listOf(OTHER_VOLUNTEER_ID)),
        reload(team),
    )
  }

  @Test
  fun setManagerWithNullLeavesTheTeamWithoutAManager() = emulatorTest {
    val team = create(richTeam(newEvent()))

    repository.setManager(team.eventId, team.id, null)

    assertEquals(team.copy(managerId = null), reload(team))
  }

  @Test
  fun addMemberAppendsTheVolunteerOnlyOnce() = emulatorTest {
    val team = create(richTeam(newEvent()).copy(memberIds = listOf(OTHER_VOLUNTEER_ID)))

    repository.addMember(team.eventId, team.id, VOLUNTEER_ID)
    repository.addMember(team.eventId, team.id, VOLUNTEER_ID)

    assertEquals(team.copy(memberIds = listOf(OTHER_VOLUNTEER_ID, VOLUNTEER_ID)), reload(team))
  }

  @Test
  fun addMemberRejectsTheManagerOfTheTeam() = emulatorTest {
    val team = create(richTeam(newEvent()))

    val failure = runCatching {
      repository.addMember(team.eventId, team.id, MANAGER_ID)
    }
        .exceptionOrNull()

    assertTrue(
        "adding the manager must fail with IllegalArgumentException, but was: $failure",
        failure is IllegalArgumentException,
    )
    assertEquals("the team must be left unchanged", team, reload(team))
  }

  @Test
  fun removeMemberRemovesOnlyThatVolunteer() = emulatorTest {
    val team =
        create(richTeam(newEvent()).copy(memberIds = listOf(VOLUNTEER_ID, OTHER_VOLUNTEER_ID)))

    repository.removeMember(team.eventId, team.id, VOLUNTEER_ID)
    // Removing someone who is no longer a member is harmless.
    repository.removeMember(team.eventId, team.id, VOLUNTEER_ID)

    assertEquals(team.copy(memberIds = listOf(OTHER_VOLUNTEER_ID)), reload(team))
  }

  @Test
  fun membershipWritesFailOnAMissingTeamWithoutCreatingIt() = emulatorTest {
    val eventId = newEvent()
    val teamId = "missing-" + UUID.randomUUID()
    // Remembered in case a write wrongly creates the team.
    createdDocuments += teamDocument(eventId, teamId)

    assertNotFound { repository.setManager(eventId, teamId, MANAGER_ID) }
    assertNotFound { repository.addMember(eventId, teamId, VOLUNTEER_ID) }
    assertNotFound { repository.removeMember(eventId, teamId, VOLUNTEER_ID) }
    assertNull("no write may create the team", repository.getTeam(eventId, teamId))
  }

  /** Creates [team] through the repository and remembers it for cleanup. */
  private suspend fun create(team: Team): Team =
      repository.createTeam(team).also { createdDocuments += teamDocument(it.eventId, it.id) }

  /** Reads [team] back from the server, so the assertion sees what was really stored. */
  private suspend fun reload(team: Team): Team =
      teamDocument(team.eventId, team.id).get(Source.SERVER).await().toTeam()

  private suspend fun teamsOf(eventId: String): List<Team> =
      firestore
          .collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(TeamSchema.COLLECTION)
          .get(Source.SERVER)
          .await()
          .documents
          .map { it.toTeam() }

  private suspend fun assertNotFound(block: suspend () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()

    assertTrue(
        "a write to a missing team must fail with NOT_FOUND, but was: $failure",
        (failure as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.NOT_FOUND,
    )
  }

  private fun teamDocument(eventId: String, teamId: String) =
      firestore
          .collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(TeamSchema.COLLECTION)
          .document(teamId)

  /**
   * Creates a fresh event owned by the signed-in user, which `firestore.rules` requires before any
   * team can be written under it, and remembers it for cleanup.
   */
  private suspend fun newEvent(): String = ScratchEvents.create().also { createdDocuments += it }.id

  /** Whole-second instants on purpose: Firestore keeps microseconds, not nanoseconds. */
  private fun richTeam(eventId: String) =
      Team(
          eventId = eventId,
          name = "Logistics",
          icon = "truck",
          managerId = MANAGER_ID,
          memberIds = listOf(VOLUNTEER_ID),
          volunteersNeeded = 12,
          checkInZone = CheckInZone(latitude = 46.3869, longitude = 6.2228, radiusMeters = 75.0),
          createdAt = Instant.parse("2026-01-15T09:00:00Z"),
      )

  /** Fails the test instead of hanging when the emulator cannot be reached. */
  private fun emulatorTest(block: suspend () -> Unit) = runBlocking {
    withTimeout(TIMEOUT_MILLIS) { block() }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L

    const val MANAGER_ID = "user-manager-1"
    const val VOLUNTEER_ID = "user-volunteer-1"
    const val OTHER_VOLUNTEER_ID = "user-volunteer-2"
  }
}
