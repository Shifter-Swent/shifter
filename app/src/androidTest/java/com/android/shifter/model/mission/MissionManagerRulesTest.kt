// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepositoryFirestore
import com.swent.shifter.model.team.FirestoreTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.toFirestoreMap
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `firestore.rules` of `events/{eventId}/missions` for a team's manager, run against the
 * Firestore emulator: they write the missions of their team, only add its members as assignees,
 * never more than needed, and cannot move a mission to a team they do not manage. Missions of no
 * team ("General") stay the organizer's.
 *
 * Most writes use raw maps, so the rules are checked on their own; two tests go through
 * [FirestoreMissionRepository], the way the app writes.
 */
@RunWith(AndroidJUnit4::class)
class MissionManagerRulesTest {

  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore

  /** Paths deleted in [cleanUp] through the emulator's admin API, whatever the rules allow. */
  private val paths = mutableSetOf<String>()

  /** Auth accounts deleted in [cleanUp], so the emulator does not keep them between tests. */
  private val accounts = mutableListOf<Account>()

  private lateinit var organizer: Account
  private lateinit var manager: Account
  private lateinit var member: Account
  private lateinit var formerMember: Account
  private lateinit var otherManager: Account
  private lateinit var eventId: String

  @Before
  fun setUp() = emulatorTest {
    organizer = account()
    manager = account()
    member = account()
    formerMember = account()
    otherManager = account()
    eventId = event()
    accept(manager, TEAM_ID)
    accept(member, TEAM_ID)
    accept(otherManager, OTHER_TEAM_ID)
    // The organizer appoints the managers among the members, who then leave the members.
    val teams = FirestoreTeamRepository(db)
    signIn(organizer)
    teams.setManager(eventId, TEAM_ID, manager.uid)
    teams.setManager(eventId, OTHER_TEAM_ID, otherManager.uid)
  }

  @After
  fun cleanUp() = emulatorTest {
    try {
      step("cleanup") { paths.forEach { FirestoreEmulatorAdmin.deleteDocument(it) } }
    } finally {
      try {
        accounts.forEach { account ->
          signIn(account)
          checkNotNull(auth.currentUser).delete().await()
        }
      } finally {
        auth.signOut()
      }
    }
  }

  @Test
  fun create_byTheManager_forTheirTeam_isAllowed() = emulatorTest {
    signIn(manager)

    val created = newMission()
    created.set(mission(TEAM_ID, member.uid)).await()

    assertTrue(created.get(Source.SERVER).await().exists())
  }

  @Test
  fun create_byTheManager_forAnotherTeamOrNone_isDenied() = emulatorTest {
    signIn(manager)

    assertDenied { newMission().set(mission(OTHER_TEAM_ID)).await() }
    assertDenied { newMission().set(mission(teamId = null)).await() }
    assertDenied { newMission().set(mission(TEAM_ID) - "teamId").await() }
  }

  @Test
  fun create_byAMemberOfTheTeam_isDenied() = emulatorTest {
    signIn(member)

    assertDenied { newMission().set(mission(TEAM_ID)).await() }
  }

  @Test
  fun create_byTheManager_withANonMemberAssignee_isDenied() = emulatorTest {
    signIn(manager)

    assertDenied { newMission().set(mission(TEAM_ID, formerMember.uid)).await() }
    assertDenied { newMission().set(mission(TEAM_ID, manager.uid)).await() }
  }

  @Test
  fun create_byTheManager_withMoreAssigneesThanNeeded_isDenied() = emulatorTest {
    signIn(manager)

    assertDenied {
      newMission().set(mission(TEAM_ID, member.uid) + ("volunteersNeeded" to 0)).await()
    }
  }

  @Test
  fun update_byTheManager_toAnotherTeam_isDenied() = emulatorTest {
    val mission = missionByOrganizer(TEAM_ID)
    signIn(manager)

    assertDenied { mission.update("teamId", OTHER_TEAM_ID).await() }
    assertDenied { mission.update("teamId", null).await() }
  }

  @Test
  fun update_byTheManager_keepsAFormerMemberAssigned() = emulatorTest {
    // The organizer may assign anyone; formerMember is not, or no longer, in the team.
    val mission = missionByOrganizer(TEAM_ID, formerMember.uid, member.uid)
    signIn(manager)

    mission.update("assigneeIds", listOf(formerMember.uid)).await()

    assertEquals(listOf(formerMember.uid), mission.get(Source.SERVER).await().get("assigneeIds"))
  }

  @Test
  fun update_byTheManager_addingANonMember_isDenied() = emulatorTest {
    val mission = missionByOrganizer(TEAM_ID)
    signIn(manager)

    assertDenied { mission.update("assigneeIds", listOf(formerMember.uid)).await() }
  }

  @Test
  fun update_byTheManagerOfAnotherTeam_isDenied() = emulatorTest {
    val mission = missionByOrganizer(TEAM_ID)
    signIn(otherManager)

    assertDenied { mission.update("title", "Taken over").await() }
    assertDenied { mission.delete().await() }
  }

  @Test
  fun delete_byTheManager_removesTheMissionOfTheirTeam() = emulatorTest {
    val mission = missionByOrganizer(TEAM_ID)
    signIn(manager)

    mission.delete().await()

    signIn(organizer)
    assertFalse(mission.get(Source.SERVER).await().exists())
  }

  @Test
  fun write_byTheManager_onAGeneralMission_isDenied() = emulatorTest {
    val mission = missionByOrganizer(teamId = null)
    signIn(manager)

    assertDenied { mission.update("title", "Mine").await() }
    assertDenied { mission.delete().await() }
  }

  @Test
  fun createMissionAndUpdateAssignees_byTheManager_writeThroughTheRepository() = emulatorTest {
    val repository = FirestoreMissionRepository(db)
    signIn(manager)

    val created =
        repository.createMission(
            Mission(
                eventId = eventId,
                title = "Trier les dons alimentaires",
                description = "Trier les produits collectés.",
                teamId = TEAM_ID,
                volunteersNeeded = 1,
                startAt = Instant.parse("2026-06-20T12:00:00Z"),
                endAt = Instant.parse("2026-06-20T13:30:00Z"),
                createdAt = Instant.parse("2026-05-02T08:15:00Z"),
            )
        )
    paths += "${EventSchema.COLLECTION}/$eventId/missions/${created.id}"
    repository.updateAssignees(eventId, created.id, listOf(member.uid))

    assertEquals(listOf(member.uid), repository.getMission(eventId, created.id)?.assigneeIds)
  }

  /**
   * Creates the event, owned by [organizer], with the two teams its volunteers apply to. The teams
   * are complete, so the team repository can read them.
   */
  private suspend fun event(): String {
    signIn(organizer)
    val event = db.collection(EventSchema.COLLECTION).document()
    paths += event.path
    paths += "eventParticipants/${event.id}"
    event.set(mapOf(EventSchema.ORGANIZER_ID to organizer.uid)).await()
    listOf(TEAM_ID, OTHER_TEAM_ID).forEach { teamId ->
      val team = event.collection("teams").document(teamId)
      paths += team.path
      team
          .set(
              Team(
                      eventId = event.id,
                      name = teamId,
                      description = "The $teamId team",
                      icon = "wine",
                      createdAt = Instant.EPOCH,
                  )
                  .toFirestoreMap()
          )
          .await()
    }
    return event.id
  }

  /**
   * Makes [user] a participant and a member of [teamId] through the real flow: they apply, the
   * organizer accepts. The manager reads the missions of the event as a participant.
   */
  private suspend fun accept(user: Account, teamId: String) {
    val requests = MembershipRequestRepositoryFirestore(db)
    signIn(user)
    paths += "${EventSchema.COLLECTION}/$eventId/membershipRequests/${user.uid}"
    requests.apply(
        eventId,
        MembershipRequest(
            userId = user.uid,
            teamId = teamId,
            availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
            createdAt = Instant.EPOCH,
        ),
    )
    signIn(organizer)
    requests.accept(eventId, user.uid)
  }

  private suspend fun missionByOrganizer(
      teamId: String?,
      vararg assigneeIds: String,
  ): DocumentReference {
    signIn(organizer)
    return newMission().also { it.set(mission(teamId, *assigneeIds)).await() }
  }

  /** The fields the rules look at, plus a title for the updates that change something else. */
  private fun mission(teamId: String?, vararg assigneeIds: String): Map<String, Any?> =
      mapOf(
          "title" to "Accueil",
          "teamId" to teamId,
          "volunteersNeeded" to 2,
          "assigneeIds" to assigneeIds.toList(),
      )

  private fun newMission(): DocumentReference =
      db.collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection("missions")
          .document()
          .also { paths += it.path }

  private suspend fun account(): Account {
    val email = "rules-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
    // Creating the account signs it in, so Firestore switches to it too.
    val user =
        checkNotNull(
            auth
                .signInForFirestore { auth.createUserWithEmailAndPassword(email, password).await() }
                .user
        )
    return Account(user.uid, EmailAuthProvider.getCredential(email, password)).also {
      accounts += it
    }
  }

  private suspend fun signIn(user: Account) {
    auth.signInForFirestore { auth.signInWithCredential(user.credential).await() }
  }

  private suspend fun assertDenied(block: suspend () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue(
        "expected a permission denial, but was: $failure",
        (failure as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private data class Account(val uid: String, val credential: AuthCredential)

  private companion object {
    const val TEAM_ID = "team-logistics"
    const val OTHER_TEAM_ID = "team-welcome"
  }
}
