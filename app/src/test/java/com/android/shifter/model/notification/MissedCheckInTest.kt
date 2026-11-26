// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.R
import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInMethod
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.FakeMembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserMode
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers, through [MissedCheckIn.run], who is missing (the 10 min after the start, checked in or
 * not, the mission over), whose missions count (the teams managed, the events accepted to), what is
 * shown (each volunteer once, their name or not), the next runs asked of the scheduler, and nothing
 * marked when a read or a text fails.
 */
class MissedCheckInTest {

  /** 11:10 in Zurich, where the texts are written, so a mission at 10:00 UTC reads "11:00". */
  private val now = Instant.parse("2026-11-21T10:10:00Z")

  private val texts = FakeNotificationTexts(ZoneId.of("Europe/Zurich"))

  private val requestRepository = FakeMembershipRequestRepository()

  private val eventRepository = FakeEventRepository()

  private val teamRepository = FakeTeamRepository()

  private val missionRepository = FakeMissionRepository(Clock.fixed(now, ZoneOffset.UTC))

  private val checkInRepository = FakeCheckInRepository()

  private val userRepository =
      FakeUserRepository(
          listOf(
              User(uid = "bob", displayName = "Bob Rossi", email = "bob@example.com"),
              User(uid = "ana", displayName = "Ana Silva", email = "ana@example.com"),
          )
      )

  private val scheduler = FakeNotificationScheduler()

  private val seen = InMemorySeenStore()

  private lateinit var event: Event

  private lateinit var team: Team

  private fun check(
      checkIns: CheckInRepository = checkInRepository,
      notificationTexts: NotificationTexts = texts,
  ) =
      MissedCheckIn(
          requestRepository,
          eventRepository,
          teamRepository,
          missionRepository,
          checkIns,
          userRepository,
          notificationTexts,
          scheduler,
      )

  @Before
  fun setUp() = runTest {
    event = createEvent()
    team = createTeam(event.id, managerId = MANAGER)
    accept(event.id)
  }

  private suspend fun createEvent(endAt: Instant = now + Duration.ofDays(1)) =
      eventRepository.createEvent(
          Event(
              organizerId = "organizer",
              title = "Summer Festival",
              description = "",
              type = EventType.MARKET,
              startAt = endAt - Duration.ofDays(2),
              endAt = endAt,
              location = EventLocation("Avenue du Grammont 7, Lausanne"),
              createdAt = now - Duration.ofDays(30),
          )
      )

  private suspend fun createTeam(eventId: String, managerId: String?) =
      teamRepository.createTeam(
          Team(
              eventId = eventId,
              name = "Bar",
              description = "",
              icon = "bar",
              managerId = managerId,
              createdAt = now - Duration.ofDays(20),
          )
      )

  /** The manager's request to [eventId], with [status]. */
  private fun accept(
      eventId: String,
      status: MembershipRequestStatus = MembershipRequestStatus.ACCEPTED,
  ) =
      requestRepository.add(
          eventId,
          MembershipRequest(
              userId = MANAGER,
              teamId = "any",
              availability = listOf(AvailabilitySlot(now, now + Duration.ofHours(4))),
              status = status,
              createdAt = now - Duration.ofDays(10),
          ),
      )

  private suspend fun createMission(
      startAt: Instant = now - Duration.ofMinutes(10),
      assigneeIds: List<String> = listOf("bob"),
      completed: Boolean = false,
      eventId: String = event.id,
      teamId: String? = team.id,
      title: String = "Serve at the bar",
  ) =
      missionRepository.createMission(
          Mission(
              eventId = eventId,
              title = title,
              description = "",
              teamId = teamId,
              volunteersNeeded = 3,
              startAt = startAt,
              endAt = startAt + Duration.ofHours(2),
              assigneeIds = assigneeIds,
              createdAt = now - Duration.ofDays(7),
              completed = completed,
          )
      )

  private fun checkIn(mission: Mission, userId: String, pending: Boolean = false) =
      checkInRepository.add(
          CheckIn(
              eventId = mission.eventId,
              missionId = mission.id,
              userId = userId,
              checkedInAt = mission.startAt,
              method = CheckInMethod.SELF,
              checkedInBy = userId,
              pending = pending,
          )
      )

  private suspend fun run() = check().run(MANAGER, now, seen)

  @Test
  fun run_nineMinutesAfterTheStart_staysSilent() = runTest {
    createMission(startAt = now - Duration.ofMinutes(9))

    assertEquals(emptyList<AppNotification>(), run())
  }

  @Test
  fun run_tenMinutesAfterTheStart_notifiesTheMissingVolunteer() = runTest {
    val mission = createMission()

    assertEquals(
        listOf(
            AppNotification(
                kind = NotificationKind.MISSED_CHECK_IN,
                key = "${mission.id}:bob",
                title = "Summer Festival",
                text =
                    "${R.string.notification_missed_check_in_text}[Bob Rossi, Serve at the bar, 11:00]",
                at = now,
                target =
                    NotificationTarget.Mission(
                        event.id,
                        "Summer Festival",
                        UserMode.VOLUNTEER,
                        mission.id,
                    ),
            )
        ),
        run(),
    )
  }

  @Test
  fun run_withTheVolunteerCheckedIn_staysSilent() = runTest {
    checkIn(createMission(), "bob")

    assertEquals(emptyList<AppNotification>(), run())
  }

  @Test
  fun run_withACheckInStillBeingSent_staysSilent() = runTest {
    checkIn(createMission(), "bob", pending = true)

    assertEquals(emptyList<AppNotification>(), run())
  }

  @Test
  fun run_withSeveralMissingVolunteers_notifiesEachOnce() = runTest {
    val mission = createMission(assigneeIds = listOf("bob", "ana"))

    assertEquals(listOf("${mission.id}:bob", "${mission.id}:ana"), run().map { it.key })
  }

  @Test
  fun run_again_staysSilent() = runTest {
    createMission()
    run()

    assertEquals(emptyList<AppNotification>(), run())
  }

  @Test
  fun run_withAVolunteerWithoutAName_saysAVolunteer() = runTest {
    createMission(assigneeIds = listOf("carl"))

    assertEquals(
        "${R.string.notification_missed_check_in_text_unknown}[Serve at the bar, 11:00]",
        run().single().text,
    )
  }

  @Test
  fun run_withAMissionOver_staysSilentAndForgetsIt() = runTest {
    val mission = createMission(startAt = now - Duration.ofHours(2))
    seen.markShown("${mission.id}:bob")

    assertEquals(emptyList<AppNotification>(), run())
    assertFalse(seen.wasShown("${mission.id}:bob"))
  }

  @Test
  fun run_withACompletedMission_staysSilent() = runTest {
    createMission(completed = true)

    assertEquals(emptyList<AppNotification>(), run())
  }

  @Test
  fun run_withAVolunteerCheckedInAfterBeingTold_forgetsThem() = runTest {
    val mission = createMission()
    run()
    checkIn(mission, "bob")

    run()

    assertFalse(seen.wasShown("${mission.id}:bob"))
  }

  @Test
  fun run_withATeamManagedBySomeoneElse_staysSilent() = runTest {
    createMission(teamId = createTeam(event.id, managerId = "someone-else").id)

    assertEquals(emptyList<AppNotification>(), run())
  }

  @Test
  fun run_withAGeneralMission_staysSilent() = runTest {
    createMission(teamId = null)

    assertEquals(emptyList<AppNotification>(), run())
  }

  @Test
  fun run_withAnEventStillPending_staysSilent() = runTest {
    val pending = createEvent()
    accept(pending.id, MembershipRequestStatus.PENDING)
    createMission(eventId = pending.id, teamId = createTeam(pending.id, MANAGER).id)

    assertEquals(emptyList<AppNotification>(), run())
  }

  @Test
  fun run_withAnEventOver_doesNotReadItsMissions() = runTest {
    val over = createEvent(endAt = now - Duration.ofMinutes(1))
    accept(over.id)
    createMission(eventId = over.id, teamId = createTeam(over.id, MANAGER).id)

    assertEquals(emptyList<AppNotification>(), run())
  }

  @Test
  fun run_withAMissionAhead_asksToRunWhenItIsDue() = runTest {
    val ahead = createMission(startAt = now + Duration.ofHours(1))
    createMission(startAt = now - Duration.ofMinutes(10), title = "Clean the tables")

    run()

    assertEquals(listOf(ahead.startAt + Duration.ofMinutes(10)), scheduler.requested)
  }

  @Test
  fun run_whenACheckInReadFails_marksNothing() = runTest {
    val first = createMission()
    val second = createMission(startAt = now - Duration.ofMinutes(30), title = "Clean the tables")
    val failing =
        object : CheckInRepository by checkInRepository {
          override fun observeCheckIns(eventId: String, missionId: String): Flow<List<CheckIn>> =
              if (missionId == second.id) flow { throw IOException("offline") }
              else checkInRepository.observeCheckIns(eventId, missionId)
        }

    val result = runCatching { check(checkIns = failing).run(MANAGER, now, seen) }

    assertTrue(result.exceptionOrNull() is IOException)
    assertFalse(seen.wasShown("${first.id}:bob"))
  }

  @Test
  fun run_whenALaterTextFails_marksNothing() = runTest {
    val mission = createMission(assigneeIds = listOf("bob", "ana"))
    // Writes the first text, then fails on the second, like a malformed translation.
    val failingTexts =
        object : NotificationTexts by texts {
          private var calls = 0

          override fun string(id: Int, vararg args: Any): String {
            if (++calls > 1) throw IllegalStateException("bad translation")
            return texts.string(id, *args)
          }
        }

    val result = runCatching { check(notificationTexts = failingTexts).run(MANAGER, now, seen) }

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertFalse(seen.wasShown("${mission.id}:bob"))
  }

  private companion object {
    const val MANAGER = "manager"
  }
}
