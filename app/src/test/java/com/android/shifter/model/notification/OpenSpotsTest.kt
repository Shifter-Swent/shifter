// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.R
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.user.UserMode
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers which missions [isShortAt] keeps, and what [OpenSpots] shows: each short mission once,
 * again once it was full then short again, and nothing marked when a read fails.
 */
class OpenSpotsTest {

  /** 08:00 in Zurich, where the texts are written, so a mission at 08:00 UTC reads "09:00". */
  private val now = Instant.parse("2026-11-21T07:00:00Z")

  private val texts = FakeNotificationTexts(ZoneId.of("Europe/Zurich"))

  private val eventRepository = FakeEventRepository()

  private val missionRepository = FakeMissionRepository(Clock.fixed(now, ZoneOffset.UTC))

  private val seen = InMemorySeenStore()

  private val openSpots = OpenSpots(eventRepository, missionRepository, texts)

  private fun mission(
      startAt: Instant = now + Duration.ofHours(1),
      volunteersNeeded: Int = 2,
      assigneeIds: List<String> = emptyList(),
      completed: Boolean = false,
      eventId: String = "event-1",
      title: String = "Set up the stands",
  ) =
      Mission(
          eventId = eventId,
          title = title,
          description = "",
          teamId = "team",
          volunteersNeeded = volunteersNeeded,
          startAt = startAt,
          endAt = startAt + Duration.ofHours(2),
          assigneeIds = assigneeIds,
          createdAt = now - Duration.ofDays(7),
          completed = completed,
      )

  private suspend fun createEvent(
      organizerId: String = "organizer",
      title: String = "Food drive",
      endAt: Instant = now + Duration.ofDays(2),
  ) =
      eventRepository.createEvent(
          Event(
              organizerId = organizerId,
              title = title,
              description = "",
              type = EventType.MARKET,
              startAt = endAt - Duration.ofDays(3),
              endAt = endAt,
              location = EventLocation("Rue du Marché 1, Lausanne"),
              createdAt = now - Duration.ofDays(30),
          )
      )

  @Test
  fun isShortAt_withinTheNext24h_isTrue() {
    assertTrue(mission().isShortAt(now))
  }

  @Test
  fun isShortAt_atExactly24h_isTrue() {
    assertTrue(mission(startAt = now + Duration.ofHours(24)).isShortAt(now))
  }

  @Test
  fun isShortAt_after24h_isFalse() {
    assertFalse(mission(startAt = now + Duration.ofHours(24).plusSeconds(1)).isShortAt(now))
  }

  @Test
  fun isShortAt_startingNow_isTrue() {
    assertTrue(mission(startAt = now).isShortAt(now))
  }

  @Test
  fun isShortAt_alreadyStarted_isFalse() {
    assertFalse(mission(startAt = now - Duration.ofMinutes(1)).isShortAt(now))
  }

  @Test
  fun isShortAt_whenFull_isFalse() {
    assertFalse(mission(assigneeIds = listOf("alice", "bob")).isShortAt(now))
  }

  @Test
  fun isShortAt_whenCompleted_isFalse() {
    assertFalse(mission(completed = true).isShortAt(now))
  }

  @Test
  fun run_withAShortMission_notifiesItOnce() = runTest {
    val event = createEvent()
    val created =
        missionRepository.createMission(mission(startAt = Instant.parse("2026-11-21T08:00:00Z")))

    val shown = openSpots.run("organizer", now, seen)

    assertEquals(
        listOf(
            AppNotification(
                kind = NotificationKind.OPEN_SPOTS,
                key = created.id,
                title = "Food drive",
                text = "${R.plurals.notification_open_spots_text}#2[Set up the stands, 09:00, 2]",
                at = now,
                target =
                    NotificationTarget.Mission(
                        event.id,
                        "Food drive",
                        UserMode.ORGANIZER,
                        created.id,
                    ),
            )
        ),
        shown,
    )
    assertEquals(emptyList<AppNotification>(), openSpots.run("organizer", now, seen))
  }

  @Test
  fun run_withOneSpotLeft_usesTheSingular() = runTest {
    createEvent()
    missionRepository.createMission(mission(assigneeIds = listOf("alice")))

    val shown = openSpots.run("organizer", now, seen)

    assertEquals(
        "${R.plurals.notification_open_spots_text}#1[Set up the stands, 09:00, 1]",
        shown.single().text,
    )
  }

  @Test
  fun run_afterFullThenShortAgain_showsItAgain() = runTest {
    val event = createEvent()
    val created = missionRepository.createMission(mission(volunteersNeeded = 1))
    openSpots.run("organizer", now, seen)

    missionRepository.updateAssignees(event.id, created.id, listOf("alice"))
    assertEquals(emptyList<AppNotification>(), openSpots.run("organizer", now, seen))
    missionRepository.updateAssignees(event.id, created.id, emptyList())

    assertEquals(listOf(created.id), openSpots.run("organizer", now, seen).map { it.key })
  }

  @Test
  fun run_withSeveralEvents_notifiesEachShortMission() = runTest {
    val first = createEvent(title = "Food drive")
    val second = createEvent(title = "Flea market")
    missionRepository.createMission(mission(eventId = first.id, title = "Set up the stands"))
    missionRepository.createMission(mission(eventId = second.id, title = "Sell the books"))
    missionRepository.createMission(mission(eventId = second.id, assigneeIds = listOf("a", "b")))

    val shown = openSpots.run("organizer", now, seen)

    assertEquals(listOf("Food drive", "Flea market"), shown.map { it.title })
  }

  @Test
  fun run_withAnotherOrganizersEvent_showsNothing() = runTest {
    val event = createEvent(organizerId = "someone-else")
    missionRepository.createMission(mission(eventId = event.id))

    assertEquals(emptyList<AppNotification>(), openSpots.run("organizer", now, seen))
  }

  @Test
  fun run_withAnEventOver_doesNotReadItsMissions() = runTest {
    val event = createEvent(endAt = now - Duration.ofHours(1))
    // A short mission left in the window, which would be shown if the event were read.
    missionRepository.createMission(mission(eventId = event.id))

    assertEquals(emptyList<AppNotification>(), openSpots.run("organizer", now, seen))
  }

  @Test
  fun run_whenAMissionReadFails_marksNothing() = runTest {
    val first = createEvent()
    val second = createEvent()
    val created = missionRepository.createMission(mission(eventId = first.id))
    val failing =
        object : MissionRepository by missionRepository {
          override suspend fun getMissionsByEvent(eventId: String): List<Mission> =
              if (eventId == second.id) throw IOException("offline")
              else missionRepository.getMissionsByEvent(eventId)
        }

    val result = runCatching {
      OpenSpots(eventRepository, failing, texts).run("organizer", now, seen)
    }

    assertTrue(result.exceptionOrNull() is IOException)
    assertFalse(seen.wasShown(created.id))
  }
}
