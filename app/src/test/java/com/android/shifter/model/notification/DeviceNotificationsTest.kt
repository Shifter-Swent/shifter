// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.user.FakeUserRepository
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Covers what the engine of the phone is built from: its checks, its notifier and its stores. */
@RunWith(RobolectricTestRunner::class)
class DeviceNotificationsTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val manager = context.getSystemService(NotificationManager::class.java)
  private val events = FakeEventRepository()
  private val missions = FakeMissionRepository(Clock.fixed(NOW, ZoneOffset.UTC))
  private val repositories =
      RepositoryProvider(
          mockk(),
          events,
          FakeUserRepository(),
          mockk(),
          missions,
          mockk(),
          mockk(),
          mockk(),
          mockk(),
          FakeNotificationInbox(),
          mockk(),
      )

  @Test
  fun runChecks_showsAMissionShortOfVolunteers_inTheStatusBar() = runTest {
    val missionId = createShortMission()

    DeviceNotifications(context, repositories).engine.runChecks(ORGANIZER, NOW)

    assertTrue("OPEN_SPOTS:$missionId" in manager.activeNotifications.map { it.tag })
  }

  @Test
  fun runChecks_inANewProcess_remembersWhatWasShown() = runTest {
    createShortMission()
    DeviceNotifications(context, repositories).engine.runChecks(ORGANIZER, NOW)
    manager.cancelAll()

    DeviceNotifications(context, repositories).engine.runChecks(ORGANIZER, NOW)

    assertEquals(emptyList<String>(), manager.activeNotifications.map { it.tag })
  }

  /** Creates an event of [ORGANIZER] with a mission in an hour still needing a volunteer. */
  private suspend fun createShortMission(): String {
    val event =
        events.createEvent(
            Event(
                organizerId = ORGANIZER,
                title = "Food drive",
                description = "",
                type = EventType.MARKET,
                startAt = NOW - Duration.ofDays(1),
                endAt = NOW + Duration.ofDays(1),
                location = EventLocation("Rue du Marché 1, Lausanne"),
                createdAt = NOW - Duration.ofDays(30),
            )
        )
    val startAt = NOW + Duration.ofHours(1)
    return missions
        .createMission(
            Mission(
                eventId = event.id,
                title = "Set up the stands",
                description = "",
                teamId = "team",
                volunteersNeeded = 1,
                startAt = startAt,
                endAt = startAt + Duration.ofHours(2),
                assigneeIds = emptyList(),
                createdAt = NOW - Duration.ofDays(7),
                completed = false,
            )
        )
        .id
  }

  private companion object {
    const val ORGANIZER = "organizer"
    val NOW: Instant = Instant.parse("2026-11-27T08:00:00Z")
  }
}
