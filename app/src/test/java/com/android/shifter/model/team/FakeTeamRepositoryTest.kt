// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the contracts the fake has to keep on its own: a write aimed at a team that is not there
 * fails, and the previous manager goes back to the members, as on the backend.
 */
class FakeTeamRepositoryTest {

  private val team =
      Team(
          id = "team-1",
          eventId = "event-1",
          name = "Logistics",
          description = "Move the equipment around the site",
          icon = "box",
          createdAt = Instant.parse("2027-06-01T10:00:00Z"),
      )

  private val repository = FakeTeamRepository(listOf(team))

  @Test
  fun writes_toAMissingTeam_fail() = runTest {
    for (write in
        listOf<suspend () -> Unit>(
            { repository.setManager(team.eventId, "absent", "user-1") },
            { repository.addMember(team.eventId, "absent", "user-1") },
            { repository.removeMember(team.eventId, "absent", "user-1") },
            // The id alone is not enough: a team of another event is just as absent.
            { repository.addMember("event-2", team.id, "user-1") },
        )) {
      val failure = runCatching { write() }.exceptionOrNull()
      assertTrue("a missing team must fail, but was: $failure", failure is NoSuchElementException)
    }
  }

  @Test
  fun setManager_sendsThePreviousManagerBackToTheMembers() = runTest {
    repository.addMember(team.eventId, team.id, "ann")
    repository.addMember(team.eventId, team.id, "bob")

    repository.setManager(team.eventId, team.id, "ann")
    assertEquals(listOf("bob"), repository.getTeam(team.eventId, team.id)?.memberIds)

    repository.setManager(team.eventId, team.id, "bob")
    val replaced = repository.getTeam(team.eventId, team.id)
    assertEquals("bob", replaced?.managerId)
    assertEquals(listOf("ann"), replaced?.memberIds)

    repository.setManager(team.eventId, team.id, null)
    val removed = repository.getTeam(team.eventId, team.id)
    assertEquals(null, removed?.managerId)
    assertEquals(listOf("ann", "bob"), removed?.memberIds)
  }

  /** The guard must not stand in the way of the teams that are there. */
  @Test
  fun writes_toAnExistingTeam_stillApply() = runTest {
    repository.addMember(team.eventId, team.id, "user-1")

    assertEquals(listOf("user-1"), repository.getTeam(team.eventId, team.id)?.memberIds)
  }
}
