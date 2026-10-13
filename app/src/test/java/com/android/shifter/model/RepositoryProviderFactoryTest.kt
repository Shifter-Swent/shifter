// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.membership.MembershipRequestSchema
import com.swent.shifter.model.mission.MissionSchema
import com.swent.shifter.model.team.TeamSchema
import com.swent.shifter.model.user.USERS_COLLECTION
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Covers what [RepositoryProviderFactory.create] is for: handing the two Firebase instances it is
 * given to the repositories it builds.
 *
 * Both mocks are strict, which is what makes this a real check rather than a constructor call: a
 * repository built on `Firebase.firestore` or `Firebase.auth` instead of the supplied instance
 * would reach an uninitialized Firebase and fail here. [RepositoryProviderFactory.production] is
 * never touched, so no test initializes the production backend.
 */
class RepositoryProviderFactoryTest {

  private val firestore = mockk<FirebaseFirestore>()
  private val auth = mockk<FirebaseAuth>()

  private val events = mockk<CollectionReference>()
  private val event = mockk<DocumentReference>()
  private val users = mockk<CollectionReference>()
  private val user = mockk<DocumentReference>()
  private val requests = mockk<CollectionReference>()
  private val missions = mockk<CollectionReference>()
  private val teams = mockk<CollectionReference>()

  private val absent = mockk<DocumentSnapshot>()
  private val empty = mockk<QuerySnapshot>()

  private val provider = RepositoryProviderFactory.create(firestore, auth)

  /** The cheapest read of each repository, answered with an already-complete task. */
  @Before
  fun setUp() {
    every { firestore.collection(EventSchema.COLLECTION) } returns events
    every { events.document(EVENT_ID) } returns event
    every { event.get() } returns Tasks.forResult(absent)

    every { firestore.collection(USERS_COLLECTION) } returns users
    every { users.document(USER_ID) } returns user
    every { user.get() } returns Tasks.forResult(absent)

    every { event.collection(MembershipRequestSchema.COLLECTION) } returns requests
    every { event.collection(MissionSchema.COLLECTION) } returns missions
    every { event.collection(TeamSchema.COLLECTION) } returns teams
    listOf(requests, missions, teams).forEach { every { it.get() } returns Tasks.forResult(empty) }

    every { absent.exists() } returns false
    every { absent.data } returns null
    every { empty.documents } returns emptyList()
  }

  @Test
  fun create_buildsTheAuthRepositoryOnTheSuppliedAuth() {
    every { auth.signOut() } just runs

    val result = provider.authRepository.signOut()

    verify(exactly = 1) { auth.signOut() }
    assertEquals(Result.success(Unit), result)
  }

  /**
   * Reads through all five Firestore repositories of one provider. Each read answers from the
   * supplied instance, so each repository was built on it; a sixth instance anywhere would throw.
   */
  @Test
  fun create_buildsEveryFirestoreRepositoryOnTheSuppliedFirestore() = runTest {
    assertNull(provider.eventRepository.getEvent(EVENT_ID))
    assertNull(provider.userRepository.getUser(USER_ID))
    assertEquals(
        emptyList<Any>(),
        provider.membershipRequestRepository.getMembershipRequestsByEId(EVENT_ID),
    )
    assertEquals(emptyList<Any>(), provider.missionRepository.getMissionsByEvent(EVENT_ID))
    assertEquals(emptyList<Any>(), provider.teamRepository.getTeamsByEvent(EVENT_ID))

    verify { firestore.collection(EventSchema.COLLECTION) }
    verify { firestore.collection(USERS_COLLECTION) }
  }

  private companion object {
    const val EVENT_ID = "event-1"
    const val USER_ID = "user-1"
  }
}
