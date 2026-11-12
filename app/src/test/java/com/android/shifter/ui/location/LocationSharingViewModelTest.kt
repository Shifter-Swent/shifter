// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.location.LiveLocationSharingState
import com.swent.shifter.model.location.LiveLocationTarget
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserRepositoryException
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The states and the target names [LocationSharingViewModel] gives the top bar's indicator. */
class LocationSharingViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val events = FakeEventRepository().apply { events += event(EVENT, "City Marathon") }
  private var eventReads = 0
  private val countingEvents =
      object : EventRepository by events {
        override suspend fun getEvent(eventId: String): Event? {
          eventReads++
          return events.getEvent(eventId)
        }
      }
  private val teams =
      FakeTeamRepository(
          listOf(
              Team(
                  EVENT_HYDRATION,
                  EVENT,
                  "Hydration",
                  "",
                  "icon",
                  managerId = ALEX,
                  createdAt = NOW,
              ),
              Team(EVENT_FINISH, EVENT, "Finish area", "", "icon", createdAt = NOW),
          )
      )
  private val users = FakeUserRepository(listOf(User(ALEX, "Alex", "alex@example.com")))
  private val viewModel = LocationSharingViewModel(countingEvents, teams, users)

  @Test
  fun sharingStates_followThePublisher() {
    viewModel.onSharingStates(mapOf(HYDRATION to LiveLocationSharingState.SHARED))
    assertEquals(mapOf(HYDRATION to LiveLocationSharingState.SHARED), viewModel.sharingStates.value)

    // The publisher stopped: no session runs, so nothing is left to show.
    viewModel.onSharingStates(emptyMap())
    assertEquals(
        emptyMap<LiveLocationTarget, LiveLocationSharingState>(),
        viewModel.sharingStates.value,
    )
  }

  @Test
  fun loadLabels_namesTheEventTheTeamAndItsManager() {
    viewModel.onSharingStates(
        mapOf(
            HYDRATION to LiveLocationSharingState.SHARED,
            FINISH to LiveLocationSharingState.WAITING,
        )
    )

    viewModel.loadLabels()

    assertEquals(
        mapOf(
            HYDRATION to SharingTargetLabel("City Marathon", "Hydration", "Alex"),
            // A team without a manager is named without one.
            FINISH to SharingTargetLabel("City Marathon", "Finish area", null),
        ),
        viewModel.labels.value,
    )
  }

  @Test
  fun loadLabels_afterAFailedRead_readsAgainAtTheNextOpening() {
    viewModel.onSharingStates(mapOf(HYDRATION to LiveLocationSharingState.SHARED))
    events.failure = EventRepositoryException.Unavailable()

    viewModel.loadLabels()
    // The target is shown without names rather than with wrong ones.
    assertEquals(emptyMap<LiveLocationTarget, SharingTargetLabel>(), viewModel.labels.value)

    events.failure = null
    viewModel.loadLabels()

    assertEquals(
        SharingTargetLabel("City Marathon", "Hydration", "Alex"),
        viewModel.labels.value[HYDRATION],
    )
  }

  @Test
  fun loadLabels_readsOnlyTheTargetsNotNamedYet() {
    viewModel.onSharingStates(mapOf(HYDRATION to LiveLocationSharingState.SHARED))
    viewModel.loadLabels()
    assertEquals(1, eventReads)

    // Opening the sheet again reads nothing; a new target is the only one read.
    viewModel.loadLabels()
    assertEquals(1, eventReads)
    viewModel.onSharingStates(
        mapOf(
            HYDRATION to LiveLocationSharingState.SHARED,
            FINISH to LiveLocationSharingState.SHARED,
        )
    )
    viewModel.loadLabels()

    assertEquals(2, eventReads)
    assertEquals(setOf(HYDRATION, FINISH), viewModel.labels.value.keys)
  }

  @Test
  fun loadLabels_afterThePublisherStopped_readsTheNamesAgain() {
    viewModel.onSharingStates(mapOf(HYDRATION to LiveLocationSharingState.SHARED))
    // Alex's name cannot be read, so the team is named without its manager.
    users.failure = UserRepositoryException.Unavailable()
    viewModel.loadLabels()
    assertEquals(
        SharingTargetLabel("City Marathon", "Hydration", null),
        viewModel.labels.value[HYDRATION],
    )

    // Meanwhile Sam replaces Alex. The app goes to the background, then comes back.
    users.failure = null
    users.users[SAM] = User(SAM, "Sam", "sam@example.com")
    runBlocking { teams.setManager(EVENT, EVENT_HYDRATION, SAM) }
    viewModel.onSharingStates(emptyMap())
    assertEquals(emptyMap<LiveLocationTarget, SharingTargetLabel>(), viewModel.labels.value)
    viewModel.onSharingStates(mapOf(HYDRATION to LiveLocationSharingState.SHARED))
    viewModel.loadLabels()

    // The sheet names who sees the position now, not who did before.
    assertEquals(
        SharingTargetLabel("City Marathon", "Hydration", "Sam"),
        viewModel.labels.value[HYDRATION],
    )
  }

  @Test
  fun loadLabels_forADeletedTeam_keepsNoLabel() {
    val gone = LiveLocationTarget(EVENT, "deleted")
    viewModel.onSharingStates(mapOf(gone to LiveLocationSharingState.REFUSED))

    viewModel.loadLabels()

    assertEquals(emptyMap<LiveLocationTarget, SharingTargetLabel>(), viewModel.labels.value)
  }

  private companion object {
    const val EVENT = "marathon"
    const val EVENT_HYDRATION = "hydration"
    const val EVENT_FINISH = "finish"
    const val ALEX = "alex"
    const val SAM = "sam"
    val HYDRATION = LiveLocationTarget(EVENT, EVENT_HYDRATION)
    val FINISH = LiveLocationTarget(EVENT, EVENT_FINISH)
    val NOW: Instant = Instant.parse("2026-06-14T09:00:00Z")

    fun event(id: String, title: String) =
        Event(
            id = id,
            organizerId = "organizer",
            title = title,
            description = "",
            type = EventType.MUSIC,
            startAt = NOW,
            endAt = NOW.plusSeconds(3600),
            location = EventLocation("Lyon"),
            createdAt = NOW,
        )
  }
}
